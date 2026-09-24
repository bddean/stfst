#!/usr/bin/env python3
"""Rewrite Typey Type's vowels by the spelling theory in `dictionaries/typey-vowels.toml`.

Typey Type picks a word's vowel chord by sound and then breaks ties between
homophones one pair at a time: `sea` takes AE because `see` exists, `sail` takes
AEU* because `sale` does. The table replaces that with a rule over the word's own
spelling, so a word's outline follows from how it is written and needs no twin.

Input is `~/.config/plover/typey-type-upstream.json`, Typey Type as it was
imported. Output is `~/.config/plover/typey-type-full.json`, which both Plover
systems read, so the result is a JSON dictionary and not a transducer.

What is rewritten:

- **One syllable, one stroke.** Every stroke of a one-syllable word (inflections
  included: `sneaks`, `stayed`) whose consonants are the word's own gets the
  table's vowel. The consonants are kept, so alternates that differ only in them
  stay. A stroke with no vowel, or whose consonants are not the word's (a brief,
  a misstroke), is left alone.
- **One syllable per stroke.** A stroke of a longer outline is rewritten only
  where the word's spelling, its CMU syllables and the outline's strokes line up
  one to one and the stroke's consonants are that syllable's.

Two theory words on one stroke go to the more frequent, unless the other already
writes it and the more frequent one has somewhere else to go. The theory takes a
stroke from any other entry unless that entry is an affix or a hundred times as
frequent, and the report names every word left with no outline in the stack.

Usage (needs cmudict and wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-typey-vowels.py            # write typey-type-full.json, print the report
    $PY scripts/gen-typey-vowels.py --check    # fail if the committed JSON is stale
    $PY scripts/gen-typey-vowels.py --ablate   # what each rule saves over today's chord
"""
import argparse
import collections
import json
import os
import re
import sys
import tomllib
from dataclasses import dataclass

import cmudict
from wordfreq import zipf_frequency

PLOVER = os.path.expanduser("~/.config/plover")
UPSTREAM = os.path.join(PLOVER, "typey-type-upstream.json")
OUTPUT = os.path.join(PLOVER, "typey-type-full.json")
HERE = os.path.dirname(os.path.abspath(__file__))
TABLE = os.path.join(HERE, "..", "dictionaries", "typey-vowels.toml")
GOLDEN = os.path.join(HERE, "..", "tests", "vowels.tsv")
STFST = os.path.join(HERE, "..", "target", "release", "stfst")
SYSTEM = os.path.join(HERE, "..", "systems", "stenotype-extended.toml")
RECIPE = os.path.join(HERE, "..", "recipe.toml")

# A collision between two words this frequent is a common one.
COMMON = 3.0
# The theory takes a stroke from another entry unless that entry is a hundred
# times as frequent: `hep` does not take HEP from help, and `mean` takes PHAEPB
# from many.
DISPLACE = 2.0

CMU = cmudict.dict()
VOWEL_LETTERS = "aeiou"


def zipf(word):
    return zipf_frequency(word, "en")


# --- Pronunciation ---------------------------------------------------------

def prons(word):
    """CMU pronunciations, with a final unstressed ER after the stressed vowel
    read as R: CMU gives `fire` and `our` two syllables, and steno one."""
    out = []
    for p in CMU.get(word, []):
        if len(p) >= 2 and p[-1] == "ER0" and p[-2][-1] == "1":
            p = p[:-1] + ["R"]
        out.append(p)
    return out


def stressed(pron):
    """(onset, vowel, coda) phonemes of a one-syllable pronunciation.

    The vowel is the CMU name with R appended when an R follows it (AA+R is AAR),
    since an r changes the vowel's quality. A Y glide after the onset (`cute`) is
    part of the vowel."""
    k = [i for i, p in enumerate(pron) if p[-1].isdigit()]
    if len(k) != 1:
        return None
    i = k[0]
    onset, vowel, coda = pron[:i], pron[i][:-1], pron[i + 1:]
    if len(onset) > 1 and onset[-1] == "Y":
        onset = onset[:-1]
    if vowel == "ER":
        coda = ["R"] + coda
    elif coda and coda[0] == "R":
        vowel += "R"
    return onset, vowel, coda


# --- Spelling --------------------------------------------------------------

@dataclass(frozen=True)
class Spelling:
    onset: str      # letters before the vowel
    vowel: str      # the vowel letters: `ea`, `igh`, `ow`, or `a_e` for a magic e
    coda: str       # letters after the vowel, the magic e not included


def is_vowel_letter(w, i):
    c = w[i]
    if c == "y":
        return not (i == 0 and len(w) > 1 and w[1] in VOWEL_LETTERS)
    if c not in VOWEL_LETTERS:
        return False
    if c == "u" and i > 0 and w[i - 1] == "q":
        return False
    # gu before a vowel is a hard g: guess, guide
    return not (c == "u" and i == 1 and w[0] == "g" and w[2:3] and w[2] in VOWEL_LETTERS + "y")


def vowel_runs(w):
    """(start, end, letters) of each run of vowel letters. A w or y closes a
    digraph (cow, law, day) unless a vowel follows (bowed), and gh after i, ei,
    ou, au or ai is part of it (sight, eight, bought)."""
    runs, i = [], 0
    while i < len(w):
        if not is_vowel_letter(w, i):
            i += 1
            continue
        j = i
        while j < len(w) and (is_vowel_letter(w, j) or (
                w[j] in "wy" and j > i and (j + 1 == len(w) or w[j + 1] not in VOWEL_LETTERS
                                            or w[j + 1:] == "e"))):
            j += 1
        v = w[i:j]
        if len(v) == 3 and v[1] in "wy" and v[2] == "e" and v != "eye":
            v = v[:2]           # awe, owe, ewe, aye: the e adds nothing
        if w[j:j + 2] == "gh" and v in ("i", "ei", "ou", "au", "ai"):
            v, j = v + "gh", j + 2
        runs.append((i, j, v))
        i = j
    return runs


def spell(w):
    """The Spelling of a one-syllable word, or None when it has two vowel groups."""
    runs = vowel_runs(w)
    end = len(w)
    if len(runs) == 2 and runs[1][1] == len(w) and runs[1][0] > runs[0][1]:
        i, _, v = runs[1]
        # a final e is silent, and so is the ue of vague and league
        if v == "e" or (v == "ue" and w[i - 1] in "qg"):
            runs, end = runs[:1], i
    if len(runs) != 1:
        return None
    i, j, v = runs[0]
    if end < len(w) and len(v) == 1:
        v += "_e"
    return Spelling(w[:i], v, w[j:end])


def stressed_vowels(word):
    return {s[1] for p in prons(word) if (s := stressed(p))}


def stem(word, known):
    """(stem, suffix) when `word` is a one-syllable stem plus -s, -es, -ed or -d.

    A stem in `known` must share the word's vowel sound; one that is not must
    leave the vowel and some coda intact (`yikes` is `yike`+s, `speed` is not
    `spe`+ed)."""
    cands = []
    if len(word) > 4 and word.endswith("ed") and word[-3] == word[-4]:
        cands.append((word[:-3], word[-3:]))            # hopped
    if word.endswith("ves"):
        cands += [(word[:-3] + "fe", "ves"), (word[:-3] + "f", "ves")]
    if word[-1:] in ("s", "d"):
        cands.append((word[:-1], word[-1:]))
    if word.endswith(("ies", "ied")):
        cands.append((word[:-3] + "y", word[-3:]))
    if word.endswith(("es", "ed")):
        cands.append((word[:-2], word[-2:]))
    for s, suffix in cands:
        if len(s) >= 2 and s in known and stressed_vowels(s) & stressed_vowels(word):
            return s, suffix
    for s, suffix in cands:
        sp = spell(s)
        if sp and sp.coda and (suffix != "d" or sp.vowel.endswith("_e")):
            return s, suffix
    return word, ""


# --- Strokes ---------------------------------------------------------------

STROKE = re.compile(r"^([STKPWHR]*)([AO]*)(\*?)([EU]*)(-?)([FRPBLGTSDZ]*)$")


@dataclass(frozen=True)
class Stroke:
    onset: str
    vowel: str      # AOEU keys, star excluded
    star: bool
    coda: str

    @classmethod
    def parse(cls, text):
        m = STROKE.match(text)
        if not m:
            return None
        on, left, star, right, _, coda = m.groups()
        return cls(on, left + right, bool(star), coda)

    def __str__(self):
        left = "".join(k for k in "AO" if k in self.vowel)
        right = "".join(k for k in "EU" if k in self.vowel)
        mid = left + ("*" if self.star else "") + right
        return self.onset + (mid or "-") + self.coda if self.coda or not mid else self.onset + mid


# --- Consonants ------------------------------------------------------------
#
# A stroke is rewritten only when its consonants are the word's, which is what
# tells a theory stroke from a brief (`WO` would) or a misstroke (`TPHRET` net).
# Either side passes if the phonemes spell it, or if at least three words write
# the same letters with the same keys (Plover's orthographic habits: -GT for ght).

LEFT = {
    "B": ["PW"], "CH": ["KH"], "D": ["TK"], "DH": ["TH"], "F": ["TP"], "G": ["TKPW"],
    "HH": ["H"], "JH": ["SKWR"], "K": ["K", "KR"], "L": ["HR"], "M": ["PH"],
    "N": ["TPH"], "P": ["P"], "R": ["R"], "S": ["S"], "SH": ["SH"], "T": ["T"],
    "TH": ["TH"], "V": ["SR"], "W": ["W", "WH"], "Y": ["KWR"], "Z": ["STKPW", "S"],
    "ZH": ["SH", "SKWR"],
}
RIGHT = {
    "P": ["P"], "B": ["B"], "T": ["T", "D"], "D": ["D"], "K": ["BG"], "G": ["G"],
    "F": ["F"], "V": ["F", "FB"], "TH": ["T"], "DH": ["T"], "S": ["S", "Z", "F"],
    "Z": ["Z", "S"], "SH": ["RB"], "ZH": ["RB", "PBLG"], "CH": ["FP"], "JH": ["PBLG"],
    "M": ["PL"], "N": ["PB"], "NG": ["PBG"], "L": ["L"], "R": ["R"],
}
RIGHT_PAIRS = {
    ("N", "K"): ["PBG"], ("M", "P"): ["PL"], ("S", "T"): ["S", "FT"], ("K", "S"): ["BGS"],
    ("N", "JH"): ["PBLG"], ("N", "CH"): ["FRPB"], ("R", "CH"): ["FRPB"], ("L", "CH"): ["LG"],
    ("L", "K"): ["LG"], ("R", "V"): ["FRB", "RF"], ("L", "V"): ["LF"], ("L", "JH"): ["LG"],
    ("R", "JH"): ["RPBLG", "RG"], ("S", "K"): ["FBG", "SK"], ("S", "P"): ["FP"],
}
# Onset letters Plover writes although they are silent.
SILENT = {"wr": "W", "kn": "K", "gn": "TKPW", "wh": "H", "ps": "P", "ch": "K"}


def key_sets(phones, table, pairs={}):
    """Every set of keys the phonemes can be written with."""
    if not phones:
        return {frozenset()}
    out = set()
    for n, alts in ((2, pairs.get(tuple(phones[:2]), [])), (1, table.get(phones[0], []))):
        for keys in alts:
            out |= {frozenset(keys) | rest for rest in key_sets(phones[n:], table, pairs)}
    return out


def onset_spells(phones, letters, keys):
    extra = [frozenset()] + [frozenset(k) for s, k in SILENT.items() if s in letters]
    if not phones and set(keys) <= set("KWR"):
        return True         # KWR- opens a vowel-initial word
    return any(s | e == frozenset(keys) for s in key_sets(phones, LEFT) for e in extra)


def coda_spells(phones, keys):
    return frozenset(keys) in key_sets(phones, RIGHT, RIGHT_PAIRS)


# --- The table -------------------------------------------------------------

@dataclass
class Rule:
    name: str
    spelling: list
    chord: str          # AOEU keys, with * when the rule sets the spelling bit
    sound: list = None
    after: list = None
    examples: list = None

    def matches(self, spelling, sound):
        return (spelling.vowel in self.spelling
                and (not self.sound or sound in self.sound)
                and (not self.after or spelling.onset.endswith(tuple(self.after))))

    @property
    def keys(self):
        return self.chord.replace("*", "")

    @property
    def star(self):
        return "*" in self.chord


def load_table(path=TABLE):
    """The rules, and the pairs of words the table is meant to tell apart."""
    with open(path, "rb") as f:
        t = tomllib.load(f)
    return [Rule(**r) for r in t["rule"]], t["pairs"]


def rule_for(table, spelling, sound):
    return next((r for r in table if r.matches(spelling, sound)), None)


# Codas steno writes with the star, so a star beside them is a consonant's: a
# sound (-th, -nk, -mp, -st, -v) or a spelling (-c against -ck, -mn, -ph).
STAR_CODAS = ("th", "nk", "mp", "st", "lk", "v", "lf", "rf", "rv", "lv",
              "c", "mn", "mb", "lm", "ph", "rsh", "rb", "rk")


# --- Words -----------------------------------------------------------------

@dataclass
class Word:
    word: str
    stem: str
    spelling: Spelling
    sounds: list        # the stressed vowel of each pronunciation, CMU's order
    phones: list        # (onset, coda) phoneme pairs, one per pronunciation
    outlines: list      # the word's one-stroke outlines, as written upstream
    z: float

    @property
    def consonant_star(self):
        """A star beside these consonants is theirs: initial z is S*."""
        return self.spelling.coda.endswith(STAR_CODAS) or self.spelling.onset.startswith("z")

    @property
    def letters_after(self):
        """Coda letters, inflection included, with silent h and e dropped."""
        c = self.spelling.coda + self.word[len(self.stem):].replace("e", "")
        return re.sub(r"g?h$", "", c)


def one_syllable_words(upstream):
    known = set(upstream.values())
    strokes = collections.defaultdict(list)
    for outline, word in upstream.items():
        if "/" not in outline and re.fullmatch("[a-z]{2,}", word) and Stroke.parse(outline):
            strokes[word].append(outline)
    words = {}
    for word, outlines in strokes.items():
        ps = [s for p in prons(word) if (s := stressed(p))]
        if not ps:
            continue
        s, _ = stem(word, known)
        sp = spell(s)
        if not sp:
            continue
        sounds = list(dict.fromkeys(v for _, v, _ in ps))
        words[word] = Word(word, s, sp, sounds, [(on, co) for on, _, co in ps],
                           outlines, zipf(word))
    return words


def habits(words):
    """How many words write each (letters, keys) pair, per side."""
    on, co = collections.defaultdict(set), collections.defaultdict(set)
    for w in words.values():
        for o in w.outlines:
            s = Stroke.parse(o)
            on[(w.spelling.onset, s.onset)].add(w.word)
            co[(w.letters_after, s.coda)].add(w.word)
    return on, co


def is_own_stroke(w, s, habit):
    """The stroke's consonants are the word's: phonetically, or by habit."""
    if not s.vowel:
        return False
    on_habit, co_habit = habit
    # A habit that drops every key of a side is a brief's: could KO, should SHO.
    on = any(onset_spells(p[0], w.spelling.onset, s.onset) for p in w.phones) \
        or bool(s.onset) and len(on_habit[(w.spelling.onset, s.onset)]) >= 3
    co = any(coda_spells(p[1], s.coda) for p in w.phones) \
        or bool(s.coda) and len(co_habit[(w.letters_after, s.coda)]) >= 3
    return on and co


# An affix is what an untranslated word is built from, so the theory does not
# take its stroke. A hyphenated one (`{^-style}`) is a compound's convenience
# and not an affix. Nor does the theory take a one-letter word's stroke: `a` is AEU.
AFFIX = re.compile(r"^\{\^[^{}-][^{}]*\}$|^\{[^{}]*[^{}-]\^\}$")


@dataclass
class Typey:
    upstream: dict
    words: dict         # word -> Word, for the one-syllable words
    habit: tuple
    reserved: dict      # stroke -> what it writes, for strokes the theory leaves be
    known: set          # every word Typey Type writes
    own: dict           # word -> its own strokes, filled as asked

    @classmethod
    def load(cls, path=UPSTREAM):
        with open(path) as f:
            upstream = json.load(f)
        words = one_syllable_words(upstream)
        return cls(upstream, words, habits(words),
                   {o: t for o, t in upstream.items() if AFFIX.match(t) or len(t) == 1},
                   set(upstream.values()), {})


def own_strokes(typey, w):
    if w.word not in typey.own:
        typey.own[w.word] = [o for o in w.outlines if is_own_stroke(w, Stroke.parse(o), typey.habit)]
    return typey.own[w.word]


def rewrite(typey, w, rule):
    """{upstream stroke: new stroke}, for the word's own strokes the theory may
    move. One it may not stays out of the map."""
    out = {}
    for o in own_strokes(typey, w):
        s = Stroke.parse(o)
        new = str(Stroke(s.onset, rule.keys, rule.star or (s.star and w.consonant_star), s.coda))
        if new not in typey.reserved and not outranked(typey, w, new):
            out[o] = new
    return out


def outranked(typey, w, stroke):
    """Whether the entry on `stroke` is not a theory word's own and is too
    frequent for `w` to take it."""
    held = typey.upstream.get(stroke)
    if held is None or held == w.word:
        return False
    other = typey.words.get(held)
    if other and stroke in own_strokes(typey, other):
        return False
    return zipf(held) > w.z + DISPLACE


# --- Resolution ------------------------------------------------------------

@dataclass
class Result:
    moved: dict         # word -> {upstream stroke: new stroke}
    theory: dict        # word -> {new stroke: an upstream stroke}, what it claims
    rules: dict         # word -> Rule
    sound: dict         # word -> the pronunciation's vowel the rule was chosen by
    owner: dict         # stroke -> the word that got it
    kept: dict          # word -> upstream strokes it keeps, having got no theory stroke

    def lost(self, words):
        """(loser, winner, stroke) for every stroke a word claimed and did not
        get, where the two words are not spelled and sounded alike."""
        out = []
        for b, t in self.theory.items():
            for stroke in t:
                a = self.owner[stroke]
                if a == b or (self.rules.get(a) is self.rules[b] and self.sound.get(a) == self.sound[b]):
                    continue
                out.append((b, a, stroke))
        return out


def choose_rule(typey, table, w):
    """(sound, rule) for a word. A word with two pronunciations takes the one
    whose chord Typey Type writes it with most: `live` is HREUF, the verb."""
    picks = [(snd, r) for snd in w.sounds if (r := rule_for(table, w.spelling, snd))]
    if not picks:
        return None
    votes = collections.Counter(Stroke.parse(o).vowel for o in own_strokes(typey, w))
    return max(picks, key=lambda p: votes[p[1].keys])       # max keeps the first of a tie


def resolve(typey, table):
    """Each word, most frequent first, takes the strokes it claims that no one has
    taken. A stroke another word already writes goes to that word when the
    taker has another: a second theory stroke, or its own upstream stroke
    (`new` keeps TPHU, and `knew` TPHAOU). A word that gets no theory stroke
    keeps its upstream strokes that are still free."""
    words = typey.words
    moved, theory, rules, sound = {}, {}, {}, {}
    for w in words.values():
        pick = choose_rule(typey, table, w)
        if pick is not None:
            sound[w.word], rules[w.word] = pick
            moved[w.word] = rewrite(typey, w, rules[w.word])
            theory[w.word] = {}
            for o, new in moved[w.word].items():
                if new == o or new not in theory[w.word]:
                    theory[w.word][new] = o
    incumbent = {s: w for w, t in theory.items() for s, o in t.items() if s == o}
    claimed = {s for t in theory.values() for s in t}
    owner, kept = {}, {}
    for w in sorted(theory, key=lambda x: (-words[x].z, x)):
        own = own_strokes(typey, words[w])
        if not own:
            continue
        free = [s for s in theory[w] if s not in owner]
        uncontested = [s for s in free if incumbent.get(s, w) == w]
        spare = [o for o in own if o not in owner]
        if uncontested or (free and not spare):
            take = uncontested or free
        else:
            take = kept[w] = spare
        for s in take:
            owner[s] = w
    for s in claimed - owner.keys():
        owner[s] = incumbent[s]     # left by a word that took another stroke
    return Result(moved, theory, rules, sound, owner, kept)


def score(words, result):
    """Collisions, and those between two common words."""
    lost = result.lost(words)
    return len(lost), sum(1 for b, a, _ in lost if min(words[a].z, words[b].z) > COMMON)


# --- Longer outlines ---------------------------------------------------------
#
# A stroke of a longer outline is rewritten only where the word's vowel runs,
# its CMU syllables and the outline's strokes line up one to one, every
# stroke's consonants are its syllable's, and the syllable is stressed and
# spelled with two vowel letters or more. A single vowel letter is left alone:
# in a longer word it is long in an open syllable (pa-per), which the
# one-syllable table does not describe.

def syllable_parts(pron):
    """(vowels, clusters) of a pronunciation: each syllable's (vowel, stress),
    and the consonants around them, one more cluster than vowels. An R after a
    vowel is kept in the cluster and marks the vowel, as in `stressed`."""
    vowels, clusters, cur = [], [], []
    for p in pron:
        if p[-1].isdigit():
            clusters.append(cur)
            cur = ["R"] if p[:-1] == "ER" else []
            vowels.append([p[:-1], p[-1]])
        else:
            cur.append(p)
    clusters.append(cur)
    for k, (v, _) in enumerate(vowels):
        if v != "ER" and clusters[k + 1][:1] == ["R"]:
            vowels[k][0] += "R"
    return vowels, clusters


def splits(cluster, coda, onset, letters):
    """Whether one stroke's coda and the next one's onset write the consonants
    between their vowels: a prefix and a suffix covering all of it, overlap
    allowed (HAP/PEU)."""
    return any(coda_spells(cluster[:k], coda) and onset_spells(cluster[j:], letters, onset)
               for k in range(len(cluster) + 1) for j in range(k + 1))


# A bare suffix stroke writes the end of the last syllable, not one of its own.
SUFFIX_STROKES = {"-S": ("S", "Z"), "-Z": ("S", "Z"), "-D": ("D", "T")}


def align(word, letters, strokes, suffix=None):
    """For each stroke, (spelling, sound, stress), or None when the word, its
    pronunciation and its strokes do not line up. `letters` spells the word
    without its inflection (vetoed is veto), and `suffix` is a bare suffix
    stroke after the strokes, which takes the last consonant."""
    spoken, word, runs = word, letters, vowel_runs(letters)
    if len(runs) > 1 and runs[-1][2] == "e" and runs[-1][1] == len(word):
        runs = runs[:-1]            # a final silent e
    if len(runs) != len(strokes):
        return None
    for pron in prons(spoken):
        vowels, clusters = syllable_parts(pron)
        if len(vowels) != len(strokes):
            continue
        if suffix:
            if clusters[-1][-1:] not in ([p] for p in SUFFIX_STROKES[suffix]):
                continue
            clusters = clusters[:-1] + [clusters[-1][:-1]]
        ok = onset_spells(clusters[0], word[:runs[0][0]], strokes[0].onset) \
            and coda_spells(clusters[-1], strokes[-1].coda)
        for k in range(len(strokes) - 1):
            ok = ok and splits(clusters[k + 1], strokes[k].coda, strokes[k + 1].onset,
                               word[runs[k][1]:runs[k + 1][0]])
        if ok:
            return [(Spelling(word[runs[k - 1][1] if k else 0:runs[k][0]], runs[k][2],
                              word[runs[k][1]:runs[k + 1][0] if k + 1 < len(runs) else len(word)]),
                     sound, stress)
                    for k, ((sound, stress), _) in enumerate(zip(vowels, strokes))]
    return None


def rewrite_long(typey, result, table, word, outline):
    """The outline with the table's vowel on each stroke it is sure of, or None
    when the outline does not align. Equal to `outline` when nothing changes."""
    parts = outline.split("/")
    suffix = parts.pop() if parts[-1] in SUFFIX_STROKES else None
    strokes = [Stroke.parse(p) for p in parts]
    if not strokes or not all(strokes) or not all(s.vowel for s in strokes):
        return None
    base, _ = stem(word, typey.known)
    parts = align(word, base, strokes, suffix)
    if parts is None:
        return None
    if len(strokes) == 1:
        # a one-syllable word and its suffix stroke: the word's own rule, or its stem's
        w = next((x for x in (word, base) if x in result.rules), None)
        if w is None or w in result.kept:
            return None
        s = strokes[0]
        star = result.rules[w].star or (s.star and typey.words[w].consonant_star)
        return "/".join([str(Stroke(s.onset, result.rules[w].keys, star, s.coda)), suffix])
    out = []
    for s, (spelling, sound, stress) in zip(strokes, parts):
        rule = rule_for(table, spelling, sound)
        if stress != "0" and len(spelling.vowel) > 1 and rule and (rule.star or not s.star):
            s = Stroke(s.onset, rule.keys, rule.star, s.coda)
        out.append(str(s))
    return "/".join(out + [suffix] if suffix else out)


def resolve_long(typey, result, table, taken):
    """{old outline: new outline} for longer outlines of single words, a new
    outline that some other entry already writes left alone; and the counts
    for the report."""
    counts = collections.Counter()
    moves = {}
    for outline, word in typey.upstream.items():
        if "/" not in outline or not re.fullmatch("[a-z]+", word) or word not in CMU:
            continue
        counts["candidates"] += 1
        new = rewrite_long(typey, result, table, word, outline)
        if new is None:
            continue
        counts["aligned"] += 1
        if new == outline:
            continue
        if taken.get(new, word) != word:
            counts["blocked"] += 1
            continue
        moves[outline] = new
        taken[new] = word
    counts["rewritten"] = len(moves)
    return moves, counts


# --- Ablation --------------------------------------------------------------

def todays_chord(typey, result, rule):
    """The chord Typey Type writes most for the words a rule covers, the star
    counted only where it is not a consonant's."""
    counts = collections.Counter()
    for word, r in result.rules.items():
        if r is not rule:
            continue
        w = typey.words[word]
        for s in map(Stroke.parse, own_strokes(typey, w)):
            counts[s.vowel + ("*" if s.star and not w.consonant_star else "")] += 1
    return counts


def ablate(typey, table):
    """What each rule saves over the chord Typey Type writes most for its words,
    every other rule held."""
    words = typey.words
    base = resolve(typey, table)
    total, common = score(words, base)
    print(f"table: {total} collisions, {common} between common words")
    n = collections.Counter(r.name for r in base.rules.values())
    for i, rule in enumerate(table):
        today = todays_chord(typey, base, rule)
        top = today.most_common(1)[0][0] if today else rule.chord
        if top == rule.chord:
            print(f"  {rule.name:26} {n[rule.name]:4}  {rule.chord:6} as today")
            continue
        alt = list(table)
        alt[i] = Rule(rule.name, rule.spelling, top, rule.sound, rule.after)
        t2, c2 = score(words, resolve(typey, alt))
        print(f"  {rule.name:26} {n[rule.name]:4}  {rule.chord:6} today {top:6} "
              f"saves {t2 - total:+3d}, {c2 - common:+3d} common")


# --- Output ----------------------------------------------------------------

def build(typey, result):
    """The rewritten dictionary in upstream order, a new stroke taking the slot
    of the stroke it came from, and the entries the theory displaced."""
    source = {o: new for m in result.moved.values() for o, new in m.items()}
    out, displaced = {}, []
    for o, was in typey.upstream.items():
        if o in result.owner:
            if was != result.owner[o] and o not in source:
                displaced.append((o, was, result.owner[o]))
            out.setdefault(o, result.owner[o])
        elif o not in source:
            out[o] = was
        if o in source:
            out.setdefault(source[o], result.owner[source[o]])
    return out, displaced


def expectations(table, pairs, result):
    """The golden file: each rule's examples and each pair, at the strokes the
    theory gave them. An example that is not the rule's, or that lost its
    stroke, is an error in the table."""
    sys.path.insert(0, HERE)
    import plover_tape
    system = plover_tape.System.load(SYSTEM)

    def cases(word):
        strokes = sorted(s for s in result.theory.get(word, ()) if result.owner[s] == word)
        return [f"{system.tape(s)}\t{word}" for s in strokes]

    lines = [f"! What {os.path.relpath(TABLE, os.path.join(HERE, '..'))} writes, checked against",
             "! typey-type. Written by scripts/gen-typey-vowels.py."]
    bad = []
    for rule in table:
        lines.append(f"! {rule.name}: {rule.chord}")
        for word in rule.examples:
            if result.rules.get(word) is not rule or not cases(word):
                bad.append(f"{word} ({rule.name})")
            lines += cases(word)
    lines.append("! pairs the spelling tells apart")
    for pair in pairs:
        for word in pair:
            if not cases(word):
                bad.append(f"{word} (pairs)")
            lines += [c for c in cases(word) if c not in lines]
    if bad:
        sys.exit("examples that do not show their rule: " + ", ".join(bad))
    return "\n".join(lines) + "\n"


def write_json(d, path):
    """In Plover's own layout, so a rewrite diffs by entry."""
    with open(path, "w") as f:
        f.write("{\n" + ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}"
                                    for k, v in d.items()) + "\n}\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the committed output is stale")
    ap.add_argument("--ablate", action="store_true", help="print what each rule saves")
    args = ap.parse_args()

    typey = Typey.load()
    table, pairs = load_table()
    if args.ablate:
        ablate(typey, table)
        return
    result = resolve(typey, table)
    out, displaced = build(typey, result)
    moves, counts = resolve_long(typey, result, table, dict(out))
    out = {moves.get(o, o): t for o, t in out.items()}
    golden = expectations(table, pairs, result)
    if args.check:
        with open(OUTPUT) as f:
            stale = json.load(f) != out
        with open(GOLDEN) as f:
            stale |= f.read() != golden
        if stale:
            sys.exit(f"{OUTPUT} or {GOLDEN} is stale: run {sys.argv[0]}")
        return
    write_json(out, OUTPUT)
    with open(GOLDEN, "w") as f:
        f.write(golden)
    report(typey, out, result, displaced, counts)


# --- The stack -------------------------------------------------------------

class Stack:
    """The Stenotype Extended stack in `plover.cfg`, highest first. A compiled
    artifact imported from JSON is read as that JSON, typey-type as the
    dictionary being built, and the rest through `stfst lookup`."""

    def __init__(self, typey):
        import configparser
        cfg = configparser.RawConfigParser()
        cfg.read(os.path.join(PLOVER, "plover.cfg"))
        with open(RECIPE, "rb") as f:
            sources = {d["name"]: os.path.expanduser(d["json"])
                       for d in tomllib.load(f)["dict"] if "json" in d}
        self.layers = []
        for e in json.loads(cfg["System: Stenotype Extended"]["dictionaries"]):
            if not e["enabled"]:
                continue
            path = os.path.join(PLOVER, os.path.expanduser(e["path"]))
            name = os.path.basename(path).rsplit(".", 1)[0]
            path = sources.get(name, path)
            if name == "typey-type":
                self.layers.append((name, typey))
            elif path.endswith(".json"):
                with open(path) as f:
                    self.layers.append((name, json.load(f)))
            elif path.endswith(".stfst") and os.path.exists(path):
                self.layers.append((name, path))

    def ask(self, outlines):
        """{outline: [(dictionary, translation)]}, highest first."""
        import subprocess
        outlines = sorted(set(outlines))
        found = collections.defaultdict(list)
        for name, layer in self.layers:
            if isinstance(layer, dict):
                for o in outlines:
                    if o in layer:
                        found[o].append((name, layer[o]))
                continue
            res = subprocess.run([STFST, "lookup", layer, "--system", SYSTEM],
                                 input="\n".join(outlines), capture_output=True, text=True)
            for line in res.stdout.splitlines():
                o, _, record = line.split("\t")
                if record != "-":
                    found[o].append((name, record))
        return found

    def unwritable(self, translations):
        """Those of `translations` no outline in the stack writes."""
        by = collections.defaultdict(list)
        for _, layer in self.layers:
            if isinstance(layer, dict):
                for o, t in layer.items():
                    if t in translations:
                        by[t].append(o)
        answers = self.ask(o for os_ in by.values() for o in os_)
        return [t for t in translations if not any(answers[o][0][1] == t for o in by[t])]


def keys(outline):
    return len(outline.replace("/", "").replace("-", ""))


def report(typey, out, result, displaced, long_counts):
    words, kept = typey.words, result.kept
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 12]) for i in range(0, len(items), 12))
    total, common = score(words, result)
    moved = sum(1 for m in result.moved.values() for o, new in m.items() if o != new)
    norule = sorted(set(words) - set(result.rules), key=lambda w: -words[w].z)
    print(f"== one syllable\n{len(result.rules)} words take a rule; {len(norule)} have none "
          f"and are left alone:\n{wrap(norule)}")
    print(f"{moved} strokes moved to the theory's vowel; the dictionary is "
          f"{len(typey.upstream) - len(out)} entries smaller")
    print(f"{total} collisions, {common} between two words of zipf > {COMMON}:")
    lost = sorted(result.lost(words), key=lambda l: -min(words[l[0]].z, words[l[1]].z))
    print(wrap([f"{b}~{a}" for b, a, _ in lost]))
    exceptions = sorted((w for w in kept if kept[w]), key=lambda w: -words[w].z)
    print(f"{len(exceptions)} words keep an upstream stroke, the theory's being another word's:")
    print(wrap([f"{w}={'|'.join(kept[w])}" for w in exceptions]))

    c = long_counts
    print(f"== longer outlines\n{c['candidates']} outlines of one word; {c['aligned']} align "
          f"a stroke to each syllable; {c['rewritten']} rewritten; {c['blocked']} left "
          f"because another entry writes the new outline")

    stack = Stack(out)
    answers = stack.ask(s for s, w in result.owner.items() if w not in kept)
    shadowed = sorted((s, a[0]) for s, a in answers.items() if a[0][0] != "typey-type")
    print(f"== the stack\n{len(shadowed)} theory strokes a higher dictionary shadows:")
    print(wrap([f"{s}={result.owner[s]}({name}:{t})" for s, (name, t) in shadowed]))
    print(f"{len(displaced)} entries displaced:")
    print(wrap([f"{o}={was}>{by}" for o, was, by in displaced]))
    lost_words = [w for w in kept if not kept[w]]
    orphans = stack.unwritable(sorted({was for _, was, _ in displaced} | set(lost_words)))
    print(f"{len(orphans)} words no outline in the stack writes any more:")
    print(wrap(orphans))

    before = collections.defaultdict(list)
    for o, t in typey.upstream.items():
        before[t].append(o)
    after = collections.defaultdict(list)
    for o, t in out.items():
        after[t].append(o)
    longer = sorted((w for w in result.rules if words[w].z > 5 and after[w]
                     and min(map(keys, after[w])) > min(map(keys, before[w]))),
                    key=lambda w: -words[w].z)
    print(f"{len(longer)} words of zipf > 5 whose shortest outline is longer:")
    print(wrap([f"{w}={min(before[w], key=keys)}>{min(after[w], key=keys)}" for w in longer]))


if __name__ == "__main__":
    main()
