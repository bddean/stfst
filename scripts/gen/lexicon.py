"""Write `dictionaries/lexicon.lexc`: each word Typey Type writes and each
common word, its CMU pronunciations aligned to its spelling, and its frequency.

A pronunciation is aligned by syllable. The word alternates consonant segments
and vowel segments, starting and ending with a consonant one, and each segment
pairs phonemes with letters: report is `R=r IY0=e P=p AO1=o R.T=rt`. A segment
is its phonemes joined by `.`, `=`, and its letters, and either side may be
empty: at is `= AE1=a T=t`. Letters that spell nothing follow a segment's
own after a `_`: a final e or ue (`T=t_e`), and the e of aye, owe and awe
(`EY1=ay_e`). A run of vowel letters that is two vowels is split after its
first letter: create is `K.R=cr IY0=e = EY1=a T=t_e`.

The phonemes come first so that a rule reads a segment's sound before its
spelling, which is the order a steno theory asks in.

A word with a Typey Type outline of two strokes (after the vowel theory) also
has that outline as an entry, each chord headed with its stress, `1:` or `0:`:
pirouette is `1 P0:EUr/W1:Et`. The outline is the word written out, so its CMU
entries are flagged `@`, and the pipeline takes them only where a rewrite made
them shorter. An outline whose chords are not the word's vowels is a brief and
is left out (actually's TAOUL/HREU), and Typey Type's KWR, SKWR or W before a
vowel is dropped where the word has no such sound (makeup's PHAEUBG/SKWRUP).

Each entry starts with the pronunciation's place in CMU's order and a space, so
a rule can prefer the first. The pronunciations are CMU's as steno hears them
(`vowels.prons`). A word whose vowel letters do not line up with its
pronunciation is left out. The weight is the word's zipf negated, so the more
frequent word costs less.

Usage (needs cmudict, wordfreq and lemminflect, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-lexicon.py            # write the lexicon, print the report
    $PY scripts/gen-lexicon.py --check    # fail if it is stale
"""
import argparse
import json
import os
import re
import sys
from dataclasses import dataclass

import lemminflect
import plover_tape
import wordfreq

from . import ROOT, SYSTEM
from . import vowels as g

LEXICON = os.path.join(ROOT, "dictionaries", "lexicon.lexc")
# A word below this is not worth a stroke.
MIN_ZIPF = 3.0


@dataclass(frozen=True)
class Segment:
    phones: tuple
    letters: str
    silent: str = ""    # letters after these that spell nothing: a final e, or aye's e

    def __str__(self):
        return f"{'.'.join(self.phones)}={self.letters}" + (f"_{self.silent}" if self.silent else "")


@dataclass(frozen=True)
class Entry:
    word: str
    rank: int           # the pronunciation's place in CMU's order, from 1
    segments: tuple     # consonant, vowel, consonant, ..., consonant

    def __str__(self):
        return f"{self.rank} " + " ".join(map(str, self.segments))


def align(word, pron, rank=1):
    """The Entry for a word and one pronunciation, or None when its vowel
    letters and vowels do not line up.

    Two vowels with no consonant between them (react) may be one run of
    letters, split after its first letter, or after ou or oo said u
    (pirouette); an r-coloured first vowel has its r between (around). A final e or ue after q or g is silent where the vowels
    line up without it (fire, create), and in -le and -re only where they do
    not line up with it (acre, but agile)."""
    vowels = [i for i, p in enumerate(pron) if p[-1].isdigit()]
    runs = g.vowel_runs(word)
    if not vowels:
        return None
    tries = [(runs, "")]
    if len(runs) > 1:
        i, j, v = runs[-1]
        if j == len(word) and i > runs[-2][1] and (v == "e" or (v == "ue" and word[i - 1] in "qg")):
            tries.insert(0 if word[-2:] not in ("le", "re") else 1, (runs[:-1], v))
    for rs, tail in tries:
        if (spans := vowel_spans(word, rs, pron, vowels)) is not None:
            break
    else:
        return None
    segs = []
    at = 0
    for n, (i, j, v) in enumerate(spans):
        segs.append(Segment(tuple(pron[vowels[n - 1] + 1 if n else 0:vowels[n]]), word[at:i]))
        segs.append(Segment((pron[vowels[n]],), v, word[i + len(v):j]))
        at = j
    end = len(word) - len(tail)
    segs.append(Segment(tuple(pron[vowels[-1] + 1:]), word[at:end], tail))
    return Entry(word, rank, tuple(segs))


def vowel_spans(word, runs, pron, vowels):
    """Each vowel's letters as (start, end, spelling), splitting a run where two
    vowels meet while the runs are too few, or None when they do not line up.
    A run's spelling can be less than its letters: aye is ay."""
    spans, k = [], 0
    for n, (i, j, v) in enumerate(runs):
        short = (len(vowels) - k) - (len(runs) - n)
        meet = k + 1 < len(vowels) and vowels[k + 1] == vowels[k] + 1 and pron[vowels[k]][:-1] != "ER"
        if short > 0 and meet and j - i > 1:
            m = i + (2 if j - i > 2 and word[i:i + 2] in ("ou", "oo") and pron[vowels[k]][:2] in ("UW", "UH") else 1)
            spans += [(i, m, word[i:m]), (m, j, word[m:j])]
            k += 2
        else:
            spans.append((i, j, v))
            k += 1
    return spans if k == len(vowels) and len(spans) == k else None


def entries(words):
    """{word: (zipf, [Entry])} for the words that align at least once."""
    out = {}
    for w in sorted(words):
        es = {}
        for r, p in enumerate(g.prons(w), 1):
            if (e := align(w, p, r)):
                es.setdefault(e.segments, e)
        es = list(es.values())
        if es:
            out[w] = (g.zipf(w), es)
    return out


def vocabulary():
    """The words CMU pronounces that Typey Type writes, or that lemminflect, which
    lists no names, knows and that are at least MIN_ZIPF frequent: mare. A word
    Typey Type writes capitalized (Welsh) is not taken in lower case."""
    with open(g.UPSTREAM) as f:
        typey = set(json.load(f).values())
    words = {w for w in typey if re.fullmatch("[a-z]+", w) and w in g.CMU}
    folded = {w.lower() for w in typey}
    for w in wordfreq.iter_wordlist("en"):
        if g.zipf(w) < MIN_ZIPF:
            break
        if re.fullmatch("[a-z]+", w) and w in g.CMU and w not in folded and lemminflect.getAllLemmas(w):
            words.add(w)
    return words


# --- Outlines ----------------------------------------------------------------------

# A stroke of the theory: a left bank, a chord with a vowel, a right bank.
STROKE = re.compile(r"([STKPWHR]*)([AO*EU]*[AOEU][AO*EU]*)([frpblgtsdz]*)")
FIRST_KEYS = {"a": "A", "e": "E", "i": "EU", "o": "O", "u": "U", "y": "EU"}


def typey_outlines():
    """{word: [outline]} of Typey Type after the vowel theory, the outlines it
    gave up to the rules included, fewest keys first. Not the files' order,
    which moves as the rules take outlines."""
    out = {}
    for path in (g.OUTPUT, g.COMPOSED, g.SUPERSEDED):
        if os.path.exists(path):
            with open(path) as f:
                for o, w in json.load(f).items():
                    out.setdefault(w, []).append(o)
    return {w: sorted(os, key=lambda o: (g.keys(o), o)) for w, os in out.items()}


def stressed_stroke(entry, strokes):
    """Which of two strokes holds the entry's stressed vowel, or None where
    that is unclear. With two vowels it is the stressed one's; with more, the
    first or last when the stress is there, else the one stroke whose chord
    has the stressed vowel's first letter's keys."""
    vowels = [s for s in entry.segments[1::2]]
    k = next((i for i, v in enumerate(vowels) if v.phones[0][-1] == "1"), None)
    if k is None or len(vowels) < 2:
        return None
    if len(vowels) == 2 or k in (0, len(vowels) - 1):
        return 0 if k == 0 else 1 if k == len(vowels) - 1 else None
    keys = set(FIRST_KEYS.get(vowels[k].letters[:1], "-"))
    found = [i for i, (_, chord, _) in enumerate(strokes) if keys <= set(chord)]
    return found[0] if len(found) == 1 else None


LONG_KEYS = {"a": "AEU", "e": "AOE", "i": "AOEU", "o": "OE", "u": "AOU", "y": "AOEU"}
# The vowel letter a chord spells, as split.xfst's `ChordLetter` reads it.
CHORD_LETTER = {"A": "a", "O": "o", "E": "e", "U": "u", "EU": "i", "AE": "e", "AO": "o", "AU": "a",
                "OE": "o", "OU": "o", "AOE": "e", "AEU": "a", "AOU": "u", "OEU": "o", "AOEU": "i"}


def spells(chord, entry, k, table):
    """Whether a chord can be the entry's k-th vowel as written out: its first
    letter's short or long chord, the vowel table's, or a chord that spells
    one of its letters. A brief's chord is none of these: actually's
    TAOUL/HREU."""
    on, v, co = entry.segments[2 * k:2 * k + 3]
    letter = v.letters[:1].replace("y", "i")
    sound = v.phones[0][:-1] + ("R" if co.phones[:1] == ("R",) else "")
    rule = g.rule_for(table, g.Spelling(on.letters, v.letters, co.letters), sound)
    chord = chord.replace("*", "")
    return (chord in (FIRST_KEYS.get(letter), LONG_KEYS.get(letter), rule and rule.keys)
            or CHORD_LETTER.get(chord, "-") in v.letters.replace("y", "i"))


# Typey Type's keys before a vowel-initial stroke, which spell no sound unless
# the word has it: makeup is PHAEUBG/SKWRUP. W is one only after an o or u.
FILLERS = {"SKWR": "JH", "KWR": "Y", "W": "W"}


def unfilled(strokes, entry):
    """The strokes without Typey Type's fillers where the word has no sound
    for them."""
    phones = {p for seg in entry.segments[0::2] for p in seg.phones}
    after = lambda i: CHORD_LETTER.get(strokes[i - 1][1].replace("*", "")) if i else None
    filler = lambda i, on: (on in FILLERS and FILLERS[on] not in phones
                            and (on != "W" or after(i) in ("o", "u")))
    return [("" if filler(i, on) else on, chord, co) for i, (on, chord, co) in enumerate(strokes)]


def written_out(entry, strokes, stressed, table):
    """Whether two strokes are the word's own syllables, as far as their chords
    say: the stressed one spells the stressed vowel, the other another vowel."""
    vowels = entry.segments[1::2]
    k = next(i for i, v in enumerate(vowels) if v.phones[0][-1] == "1")
    return (spells(strokes[stressed][1], entry, k, table)
            and any(spells(strokes[1 - stressed][1], entry, i, table) for i in range(len(vowels)) if i != k))


def outline_entries(words, lex):
    """{word: [entry text]}: each word's two-stroke outlines that are written
    out, the stressed stroke's chord headed `1:` and the other's `0:`, and
    Typey Type's fillers dropped."""
    system = plover_tape.System.load(SYSTEM)
    table, _ = g.load_table()
    out = {}
    for w, outlines in typey_outlines().items():
        if w not in lex or w not in words:
            continue
        texts = []
        entry = lex[w][1][0]
        for o in outlines:
            tapes = system.tape(o).split("/")
            strokes = [m.groups() for t in tapes if (m := STROKE.fullmatch(t))]
            if len(tapes) != 2 or len(strokes) != 2:
                continue
            k = stressed_stroke(entry, strokes)
            if k is None or not written_out(entry, strokes, k, table):
                continue
            strokes = unfilled(strokes, entry)
            text = "/".join(f"{on}{'1' if i == k else '0'}:{chord}{co}" for i, (on, chord, co) in enumerate(strokes))
            if text not in texts:
                texts.append(text)
        if texts:
            out[w] = [Keys(f"{min(n, 4)} {t}") for n, t in enumerate(texts, 1)]
    return out


# --- The lexc file ---------------------------------------------------------------

PHONES = sorted({p for ps in g.CMU.values() for pron in ps for p in pron if len(p) > 1})


def lexc_escape(text):
    return re.sub(r"([ !%;:<>#\"0@&])", r"%\1", text)


class Keys(str):
    """Entry text that is keys, a symbol each: TH is T and H, not a phoneme."""


def lexc_text(text):
    return "".join("%" + c for c in text) if isinstance(text, Keys) else lexc_escape(text)


def lexc(header, rows):
    """A lexc lexicon of `rows`, (word, entry text, weight or None), under a
    comment `header`."""
    lines = [f"! {line}" for line in header.split("\n")]
    lines += ["", "Multichar_Symbols", " ".join(lexc_escape(p) for p in PHONES), "", "LEXICON Root"]
    for word, text, weight in rows:
        tail = f' "weight: {weight:g}"' if weight is not None else ""
        lines.append(f"{lexc_escape(word)}:{lexc_text(text)} #{tail} ;")
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the lexicon is stale")
    args = ap.parse_args()

    words = vocabulary()
    lex = entries(words)
    outlined = outline_entries(words, lex)
    rows = [(w, str(e).replace(" ", "@ ", 1) if w in outlined else str(e), -z) for w, (z, es) in lex.items() for e in es]
    rows += [(w, t, -lex[w][0]) for w, ts in outlined.items() for t in ts]
    text = lexc("Written by scripts/gen-lexicon.py: see scripts/gen/lexicon.py for the format.\nDo not edit.",
                sorted(rows, key=lambda r: r[0]))
    if args.check:
        if not os.path.exists(LEXICON) or open(LEXICON).read() != text:
            sys.exit(f"{LEXICON} is stale: run {sys.argv[0]}")
        return
    with open(LEXICON, "w") as f:
        f.write(text)
    n = sum(len(es) for _, es in lex.values())
    left = sorted(words - lex.keys(), key=lambda w: -g.zipf(w))
    print(f"{len(lex)} of {len(words)} words, {n} pronunciations; {len(left)} do not align, "
          f"most frequent first:")
    print("  " + " ".join(left[:200]))
    print(f"{len(outlined)} words have a Typey Type outline of two strokes, which is their written-out form")
