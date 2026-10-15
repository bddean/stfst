"""Write `dictionaries/two-word.json`: two words of one syllable, often written
together, as one stroke.

It is two-syllable.json's rule across a word boundary. `^` says the stroke holds
two syllables; the stressed word's vowel is the stroke's, its chord XOR the
other word's vowel letter; and the other word's consonants join the side of the
stroke they are on. So go home is `^TKPWHEM` (home's `OE` XOR go's o), at all
`^TUL` (all's `AU` XOR a), and make it `^PHABGT` (make's `AEU` XOR i).

`dictionaries/contract-pair.xfst` is that rule, between the lexicon's entries of
the two words, and `theory.xfst` and `caret.xfst` write the stroke as they do for
one word. What this script decides is the rest, which is not a rule:

- Which pairs. Each two words in a row in the phrasing corpora that are both
  words of one syllable the lexicon has, at least MIN_COUNT times. Not a pair a
  fold already writes in one stroke: "the" after a word is its -T, and "and"
  before one is S-K-P-.
- Which word has the stress: the content word, and between two of a kind the
  second, as English phrases are stressed. FUNCTION lists the function words.
- Which pair keeps a stroke. Two pairs on one go to the more frequent. A stroke
  any other dictionary writes is left to it. A stroke a fold reads as the pair
  needs no entry: `folds.xfst` reads "a" before a word and "of", "is" and "to"
  after one, so `^TPOU` is a few with no entry. A stroke a fold reads as
  something else goes to the pair if the pair is the more frequent text: the
  most is `^TPHOEFT`, which the -T fold reads as "naive the".
- A pair's stroke with a suffix key, which a fold reads as the last word
  inflected, is an entry where the stressed word inflected, on either side, is
  the more common text: `^TKPW-G` is "going to".

Usage (needs cmudict and wordfreq, which the phrasing venv has, the corpora under
~/.config/plover/phrasing/corpora, a compiled stack, and HFST: run it in the dev
shell):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-two-word.py            # write the JSON, print the report
    $PY scripts/gen-two-word.py --check    # fail if it or the pairs are stale
"""
import argparse
import collections
import json
import os
import re
import sys

from . import DICTS, ROOT, corpora
from . import lexicon as lex
from . import vowels as g
from .resolve import candidates, compile_pipeline, resolve, unwritten_briefs
from .stack import Stack, retro_words
from .two_syllable import coda, with_coda, write_json

OUTPUT = os.path.join(DICTS, "two-word.json")
PAIRS = os.path.join(ROOT, "dictionaries", "pairs.lexc")
NAME = "two-word"

# How often two words must occur in a row in the corpora (17M tokens), about
# nine times a million words. Below it, the share of pairs that lose their
# stroke climbs: 20% of those over 500, 24% over 150, 29% over 50. The corpora
# are written prose, so "go home" (114) and "my way" (111) are below it.
MIN_COUNT = 150

# Words a fold writes beside another: "and" before (S-K-P-), "the" after (-T).
FOLDED_BEFORE = {"and"}
FOLDED_AFTER = {"the"}

# Unstressed beside a content word: articles, pronouns, prepositions,
# conjunctions and auxiliaries of one syllable.
FUNCTION = frozenset("""
    a an the
    me my mine you your yours he him his she her hers it its we us our ours they them their theirs
    this that these those there which who whom whose
    at by for from in of on to with as than through till
    and but or nor if so though
    am is are was were be been has have had do does did
    can could will would shall should may might must
""".split())


def stressed(a, b):
    """Which of two words has the stress, 0 or 1."""
    return 0 if b in FUNCTION and a not in FUNCTION else 1


def entry(a, b, which):
    """The pair's entry for contract-pair.xfst: each word's first aligned
    pronunciation, the stressed one's vowel 1 and the other's 0."""
    words = []
    for n, e in enumerate((a, b)):
        segs = [lex.Segment((s.phones[0][:-1] + ("1" if n == which else "0"),), s.letters, s.silent)
                if i == 1 else s for i, s in enumerate(e.segments)]
        words.append(" ".join(map(str, segs)))
    return "1 " + " + ".join(words)


def one_syllable():
    """{word: lexicon entry} for each word of one syllable the lexicon has."""
    return {w: es[0] for w, (_, es) in lex.entries(lex.vocabulary()).items() if len(es[0].segments) == 3}


def pairs(words, counts):
    """{"a b": entry} for the pairs in scope."""
    return {f"{a} {b}": entry(words[a], words[b], stressed(a, b))
            for (a, b), n in counts.items()
            if n >= MIN_COUNT and a != b and a not in FOLDED_BEFORE and b not in FOLDED_AFTER}


def pairs_lexc(found):
    return lex.lexc("Written by scripts/gen-two-word.py: each pair of words the two-word\n"
                    "dictionary could write, as contract-pair.xfst reads it. Do not edit.",
                    [(p, e, None) for p, e in sorted(found.items())])


# Pairs that take their stroke from a fold whatever it reads, where the reading
# is counted wrong: "a {^<^}" counts as "a".
OVER_FOLD = {"not as"}

# What an inflection may append, as `{:infl:…}` names it.
ENDINGS = {"ed": ("ed", "d"), "ing": ("ing",), "ofs": ("s", "es"), "ofz": ("s", "es")}


def spellings(translation):
    """The ways a translation may be written, as the corpora spell words: an
    inflection joined to the word before it, and an affix's letters. None for a
    translation that names any other operation, which no text spells."""
    out = [()]
    for word, meta in re.findall(r"([^\s{}]+)|\{([^}]*)\}", translation):
        if word:
            out = [o + tuple(corpora.WORD.findall(word.lower())) for o in out]
        elif m := re.fullmatch(r":infl:(\w+)", meta):
            stems = lambda w: {w, w[:-1], w + w[-1:], w[:-1] + "i"}
            out = [o[:-1] + (stem + end,) for o in out if o
                   for stem in stems(o[-1]) for end in ENDINGS.get(m[1], ())]
        elif meta.startswith(":"):
            return None
        else:
            out = [o + tuple(corpora.WORD.findall(re.sub("[&<>^]", " ", meta).lower())) for o in out]
    return out


# A suffix key on a pair's stroke, and the endings of the word it inflects.
INFLECTIONS = {"G": "ing", "D": "d", "S": "s"}


def inflections(word, kind):
    if kind == "ing":
        return {word + "ing", word[:-1] + "ing", word + word[-1] + "ing"}
    return g.regular_forms(word, kind)


def inflected(out, stack):
    """{stroke: text} for a pair's stroke with a suffix key, where the stressed
    word inflected reads more often on the other side of its partner than the
    fold's reading, which inflects the last word: `^TKPW-G` is "going to", not
    "to going". Only a stroke no entry holds."""
    wanted, readings = {}, {}
    folds = stack.with_layer(NAME, out).folds(
        with_coda(s, coda(s) + k) for s in out for k in INFLECTIONS if k not in coda(s))
    for s, pair in out.items():
        a, b = pair.split(" ")
        word, other = (a, b) if stressed(a, b) == 0 else (b, a)
        if word in FUNCTION:
            continue
        for k, kind in INFLECTIONS.items():
            f = with_coda(s, coda(s) + k)
            if f in folds:
                readings[f] = spellings(folds[f][0]) or []
                wanted[f] = [gram for form in inflections(word, kind) for gram in ((form, other), (other, form))]
    counts = corpora.ngrams("two-word-inflected", {gram for gs in wanted.values() for gram in gs}
                            | {r for rs in readings.values() for r in rs})
    held = stack.ask(wanted)
    found = {}
    for f, grams in wanted.items():
        best = max(grams, key=lambda gram: (counts[gram], gram))
        read = max((counts[r] for r in readings[f]), default=0)
        if not held[f] and best not in readings[f] and counts[best] >= MIN_COUNT and counts[best] > read:
            found[f] = " ".join(best)
    return found


def build(rule, counts):
    """(entries, report data), from `rule`, {pair: {outline: cost}}."""
    count = lambda p: counts[tuple(p.split(" "))]
    stack = Stack(g.written(), skip=(NAME,))
    strokes = {o for cs in rule.values() for o in cs}
    folds = stack.folds(strokes)
    held = {s: ("retro-words", "") for s in retro_words()}
    held.update((s, ("folds", t)) for s, (t, _) in folds.items())
    # A fold reads a stroke only where no entry does.
    others = {s: held[s] for s in strokes if s in held and held[s][0] == "folds"}
    others.update((s, found[0]) for s, found in stack.ask(strokes).items() if found)
    others.update((s, held[s]) for s in strokes if s in held and held[s][0] != "folds")
    # A pair takes a stroke from a fold that reads it as something else when
    # the pair is more common than what the fold writes.
    readings = {s: spellings(t) or [] for s, (t, _) in folds.items()}
    reading_counts = corpora.ngrams("two-word-fold-readings", {w for ws in readings.values() for w in ws if w})
    over_fold = lambda outline, pair, other: (
        other[0] == "folds" and (pair in OVER_FOLD
                                 or count(pair) > max((reading_counts[w] for w in readings[outline]), default=0)))
    out, lost, left = resolve(rule, lambda p: (-count(p), p), others, over_fold)
    # A pair a fold writes needs no entry. A fold's base may be one of this
    # dictionary's entries, which changes what it reads, so ask again with them.
    folded = {s: p for s, p in out.items() if others.get(s) == ("folds", p)}
    while True:
        kept = {s: p for s, p in out.items() if s not in folded}
        now = stack.with_layer(NAME, kept).folds(folded)
        still = {s: p for s, p in folded.items() if now.get(s, ("",))[0] == p}
        if still == folded:
            break
        folded = still
    for s in folded:
        del out[s]
    reordered = {s: t for s, t in inflected(out, stack).items() if s not in out}
    out.update(reordered)
    return dict(sorted(out.items())), dict(rule=rule, count=count, lost=lost, left=left,
                                          folded=folded, reordered=reordered)


def report(found, out, r):
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 8]) for i in range(0, len(items), 8))
    rule, count = r["rule"], r["count"]
    unwritten = sorted(set(found) - set(rule), key=lambda p: -count(p))
    print(f"{len(found)} pairs in scope, {len(rule)} with a stroke; {len(out)} entries")
    print(f"{len(r['folded'])} a fold writes, so they need no entry")
    print(f"{len(r['reordered'])} with a suffix key read better with the stressed word inflected:",
          " ".join(f"{s}={t.replace(' ', '_')}" for s, t in r["reordered"].items()))
    collide = len(r["lost"]) + len(r["left"])
    print(f"{collide} of {len(rule)} with a stroke ({collide / len(rule):.1%}) lose it to a pair or a dictionary")
    print(f"{len(unwritten)} the rule writes no stroke for:")
    print(wrap([f"{p.replace(' ', '_')}" for p in unwritten[:80]]))
    lost = sorted(r["lost"], key=lambda x: -count(x[0]))
    print(f"{len(lost)} lose their stroke to a more frequent pair:")
    print(wrap([f"{w.replace(' ', '_')}~{win.replace(' ', '_')}" for w, win, _ in lost[:80]]))
    left = sorted(r["left"], key=lambda x: -count(x[0]))
    by = collections.Counter(n for _, _, (n, _) in left)
    print(f"{len(left)} leave their stroke to another dictionary:", dict(by.most_common()))
    print(wrap([f"{w.replace(' ', '_')}={s}({n}:{t})" for w, s, (n, t) in left[:80]]))
    with open(os.path.join(DICTS, "user.json")) as f:
        user = json.load(f)
    by_pair = {p: s for s, p in out.items() if s.startswith("^")}
    theirs = sorted((p, s) for s, p in user.items() if p in found)
    print(f"{len(theirs)} user.json entries for pairs in scope:",
          " ".join(f"{p.replace(' ', '_')}={s}/{by_pair.get(p, next(iter(rule.get(p, ['-']))))}" for p, s in theirs))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the JSON or the pairs are stale")
    args = ap.parse_args()

    words = one_syllable()
    counts = corpora.bigrams("two-word-bigrams", set(words))
    found = pairs(words, counts)
    text = pairs_lexc(found)
    current = os.path.exists(PAIRS) and open(PAIRS).read() == text
    if args.check and not current:
        sys.exit(f"{PAIRS} is stale: run {sys.argv[0]}")
    if not args.check:
        if not current:
            with open(PAIRS, "w") as f:
                f.write(text)
        compile_pipeline(NAME)
    out, r = build(candidates(NAME), counts)
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
    else:
        write_json(out, OUTPUT)
        report(found, out, r)
    if (missing := unwritten_briefs(NAME, out)):
        sys.exit(f"tests/briefs/{NAME}.tsv: the rule no longer writes " + " ".join(f"{o}={t}" for o, t in missing))
