"""Write `~/.config/plover/two-word.json`: two words of one syllable, often written
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
  any other dictionary writes is left to it, and so is one a fold reads: the
  most would be `^TPHOEFT`, which is naive `^TPHOEF` and -T, "naive the".
- A stroke with a vowel whose onset or coda no one-syllable word's own stroke has
  is also written without `^`, as two-syllable.json does.

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
import sys

from . import PLOVER, ROOT, corpora
from . import lexicon as lex
from . import vowels as g
from .resolve import candidates, compile_pipeline, resolve
from .stack import Stack, retro_words
from .two_syllable import bare_entries, write_json

OUTPUT = os.path.join(PLOVER, "two-word.json")
PAIRS = os.path.join(ROOT, "dictionaries", "pairs.lexc")
NAME = "two-word"

# How often two words must occur in a row in the corpora (17M tokens), about
# thirty times a million words: under a thousand entries. The corpora are
# written prose, so "go home" (114) and "my way" (111) are below it.
MIN_COUNT = 500

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


def build(typey, rule, counts):
    """(entries, report data), from `rule`, {pair: outline}."""
    count = lambda p: counts[tuple(p.split(" "))]
    never = lambda outline, word, other: False
    stack = Stack(g.written(), skip=(NAME,))
    strokes = set(rule.values())
    held = {s: ("retro-words", "") for s in retro_words()}
    held.update((s, ("folds", t)) for s, t in stack.folds(strokes | {s[1:] for s in strokes}).items())
    others = {s: found[0] for s, found in stack.ask(strokes).items() if found}
    others.update((s, held[s]) for s in strokes if s in held)
    out, lost, left = resolve(rule, lambda p: (-count(p), p), others, never)
    plain, taken = bare_entries(NAME, out, typey, stack, held, never)
    out.update(plain)
    return dict(sorted(out.items())), dict(rule=rule, count=count, lost=lost, left=left,
                                          bare=plain, bare_taken=taken)


def report(found, out, r):
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 8]) for i in range(0, len(items), 8))
    rule, count = r["rule"], r["count"]
    unwritten = sorted(set(found) - set(rule), key=lambda p: -count(p))
    caret = sum(1 for o in out if o.startswith("^"))
    print(f"{len(found)} pairs in scope, {len(rule)} with a stroke; {len(out)} entries, "
          f"{caret} with ^ and {len(out) - caret} without")
    print(f"{len(unwritten)} the rule writes no stroke for:")
    print(wrap([f"{p.replace(' ', '_')}" for p in unwritten[:80]]))
    lost = sorted(r["lost"], key=lambda x: -count(x[0]))
    print(f"{len(lost)} lose their stroke to a more frequent pair:")
    print(wrap([f"{w.replace(' ', '_')}~{win.replace(' ', '_')}" for w, win, _ in lost[:80]]))
    left = sorted(r["left"], key=lambda x: -count(x[0]))
    by = collections.Counter(n for _, _, (n, _) in left)
    print(f"{len(left)} leave their stroke to another dictionary:", dict(by.most_common()))
    print(wrap([f"{w.replace(' ', '_')}={s}({n}:{t})" for w, s, (n, t) in left[:80]]))
    print(f"{len(r['bare'])} are also written without ^, and {len(r['bare_taken'])} could be "
          f"but that stroke is taken")
    with open(os.path.join(PLOVER, "stfst-user.json")) as f:
        user = json.load(f)
    by_pair = {p: s for s, p in out.items() if s.startswith("^")}
    theirs = sorted((p, s) for s, p in user.items() if p in found)
    print(f"{len(theirs)} stfst-user.json entries for pairs in scope:",
          " ".join(f"{p.replace(' ', '_')}={s}/{by_pair.get(p, rule.get(p, '-'))}" for p, s in theirs))


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
    out, r = build(g.Typey.load(), candidates(NAME), counts)
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
        return
    write_json(out, OUTPUT)
    report(found, out, r)
