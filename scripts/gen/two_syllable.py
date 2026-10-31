"""Write `dictionaries/two-syllable.json`: a word written out as one stroke.

The rule is the pipeline recipe.toml names. `written.xfst` writes each
syllable's vowel as its chord, and the rewrites after it make the written-out
form shorter: a prefix's cluster (display is `STKPHRAEU`), -tion as -GS,
-ment, -ness, -ful and -less as their chords and -ly as its L, a leading
unstressed vowel dropped (append is `^PEPBD`), a u before a vowel as w, an
unstressed vowel between a consonant and l, m, n or r dropped (river is
`REUFR`), an r-coloured vowel right after the stressed one in its syllable
(iron is `AOEURPB`), and a middle t or d dropped. A Typey Type outline of two
strokes gets the prefix's cluster too (disappoint is `STKPOEUPBT`). What is
left is one stroke:

- One syllable is a plain stroke (river), or the same keys with `^` where the
  plain stroke is taken: matter is `^PHART`, since mart is `PHART`.
- Two are one syllable around the stressed vowel, marked by the other
  vowel's first letter: the left bank is every consonant before it, the right
  bank every consonant after it, and the chord is its own. The mark is `+` for
  a, `^` for e, `^+` for i and y, `+O` for o and `+U` for u, its keys OR'd into
  the stroke. An r-coloured other vowel is its R, and it and a syllable a
  rewrite dropped are `^`. So report is `^PRORT`, happy `^+HAP`, city
  `^+SEUT`, today `+TKAOEU` and about `+PWOUT`.
- More have no stroke.

A rewrite that is a second choice leaves the word's outline without it as the
first, and the reverse; `contract.xfst`, `theory.xfst` and `caret.xfst` write
the stroke. What this script decides is the rest, which is not a rule:

- An -s or -ed form is its word's stroke and the suffix key, and is not an
  entry, since the key folds (allowed is `^HRAOU` + `-D`). Nor is a regular -s,
  -ed or -ing form of any other word Typey Type writes (talking is `TAUBG` +
  `-G`).
- An outline goes to the word that wants it soonest, then to the more frequent,
  and a word then keeps every other outline of its own no word took.
  A stroke another dictionary in the stack writes is left to it, and a plain
  stroke takes none, not even one Typey Type gives up to the rules.
- A common word of one syllable that Typey Type does not write is its plain
  stroke (sheikh).
- A word that loses its stroke falls back to the two strokes the theory writes
  for it before contract.xfst joins them (animal is `APB/PHAL`), and where those
  are taken too, to its Typey Type outlines, the ones the rule superseded
  among them.

Usage (needs wordfreq, which the phrasing venv has, a compiled stack, and HFST
for the pipeline: run it in the dev shell):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-two-syllable.py            # write the JSON, print the report
    $PY scripts/gen-two-syllable.py --check    # fail if the committed JSON is stale
"""
import argparse
import collections
import json
import os
import re
import sys

from . import DICTS
from . import lexicon as lex
from . import vowels as g
from .resolve import (candidates, compile_pipeline, pipeline, relation, resolve, typey_gives_way,
                      unwritten_briefs)
from .stack import _SYSTEM, Stack, retro_words

OUTPUT = os.path.join(DICTS, "two-syllable.json")
NAME = "two-syllable"


# --- Inflections ---------------------------------------------------------------

SUFFIX_KEYS = {"S": "s", "D": "d"}


def marked(stroke):
    """(mark, the rest) of a stroke: the `^` and `+` before its left bank."""
    rest = stroke.lstrip("^+")
    return stroke[:len(stroke) - len(rest)], rest


def with_coda(stroke, coda):
    mark, rest = marked(stroke)
    s = g.Stroke.parse(rest)
    return mark + str(g.Stroke(s.onset, s.vowel, s.star, "".join(k for k in "FRPBLGTSDZ" if k in coda)))


def coda(outline):
    return g.Stroke.parse(marked(outline)[1]).coda


def compose(strokes, words):
    """Give each of `words` that inflects a word in `strokes` that word's
    outlines with the suffix key, at their cost, which is what the fold writes:
    allowed is allow and -D. Returns {inflection: (base, {its outline: the
    base's})}."""
    own = dict(strokes)
    base = {}
    for w in words:
        # The most frequent base, when a spelling inflects two.
        for b in sorted({w[:-1], w[:-2], w[:-3], w[:-3] + "y"} & own.keys(), key=lambda b: (-g.zipf(b), b)):
            key = next((k for k, kind in SUFFIX_KEYS.items() if w in g.regular_forms(b, kind)
                        and k not in coda(next(iter(own[b])))), None)
            if key:
                folds = {with_coda(o, coda(o) + key): o for o in own[b] if key not in coda(o)}
                strokes[w] = {f: own[b][o] for f, o in folds.items()}
                base[w] = (b, folds)
                break
    return base


def inflection(w, known):
    """Whether `w` is a regular -s, -ed or -ing form of a word Typey Type writes."""
    for b in {w[:-1], w[:-2], w[:-3], w[:-3] + "e", w[:-4], w[:-3] + "y", w[:-2] + "y"}:
        if len(b) > 1 and b in known and (w in g.regular_forms(b, "s") | g.regular_forms(b, "d")
                                          or w in (b + "ing", b[:-1] + "ing", b + b[-1] + "ing")):
            return True
    return False


def syllables(w):
    return sum(p[-1].isdigit() for p in g.prons(w)[0])


def theory_outlines():
    """{word: [outline]}: the two strokes theory.tsv writes for each word, by
    rank, a prefix's cluster on the first."""
    out = collections.defaultdict(list)
    with open(dict(pipeline(NAME))["theory"]) as f:
        for line in f:
            w, rest = line.rstrip("\n").split("\t")
            _, *lead, strokes = rest.split(" ")
            strokes = strokes.split("/")
            if len(strokes) != 2:
                continue
            # An onset, the stress, the letters, then `:` and the chord.
            tapes = [re.sub(r"[0-2][A-Z]*:", "", s) for s in strokes]
            tapes[0] += "".join(lead).replace("^", "")
            o = "/".join(_SYSTEM.outline("".join(sorted(set(t), key=_SYSTEM.order.index))) for t in tapes)
            if o not in out[w]:
                out[w].append(o)
    return out


def typey_outlines():
    """{word: [outline]}: Typey Type's outlines of up to four strokes, the
    superseded ones among them, the fewest strokes first."""
    with open(g.SUPERSEDED) as f:
        superseded = json.load(f)
    out = collections.defaultdict(list)
    for o, w in list(g.written().items()) + list(superseded.items()):
        if o not in out[w] and o.count("/") < 4:
            out[w].append(o)
    for outlines in out.values():
        outlines.sort(key=lambda o: o.count("/"))
    return out


# The outlines a word that loses its stroke falls back to, in order.
FALLBACKS = {"theory": theory_outlines, "typey": typey_outlines}


def add_fallbacks(strokes, given):
    """Add to `strokes`, {word: {outline: cost}}, each fallback's outlines,
    from `given`, [(name, {word: [outline]})], in order and costing more than
    any word's own. Returns {(word, outline): name}."""
    last = max(c for cs in strokes.values() for c in cs.values())
    tagged = {}
    for n, (name, outlines) in enumerate(given):
        for w, cs in strokes.items():
            for o in outlines.get(w, ()):
                if o not in cs:
                    cs[o] = last + 1 + n
                    tagged[w, o] = name
    return tagged


def may_displace(outline, outlines, word):
    """Whether `outline`, one of the `outlines` of a word whose base is `word`,
    may take a stroke from Typey Type. A plain stroke takes nothing: where it
    is taken, the word has the same keys with `^`."""
    return syllables(word) > 1 and bool(marked(outline)[0] or "^" + outline not in outlines)


def build(typey, rule, vocabulary):
    """(entries, report data), from `rule`, {word: {outline: cost}}, over the
    lexicon's `vocabulary`."""
    words = sorted(vocabulary, key=lambda w: (-g.zipf(w), w))
    strokes = {w: s for w in words if (s := rule.get(w)) and (w not in typey.known or syllables(w) > 1)}
    base = compose(strokes, words)
    stem = lambda w: base[w][0] if w in base else w
    # A base ranks as its most frequent form, so exalted folds from exalt.
    z = {w: g.zipf(w) for w in strokes}
    for w, (b, _) in base.items():
        z[b] = max(z[b], z[w])
    strokes = {w: s for w, s in sorted(strokes.items())
               if z[w] >= lex.MIN_ZIPF and (w in base or not inflection(w, vocabulary))}
    fallback = add_fallbacks(strokes, [(name, f()) for name, f in FALLBACKS.items()])
    # two-word.json gives way to this dictionary, so it is not asked.
    stack = Stack(g.written(), skip=(NAME, "two-word"))
    every = sorted({o for cs in strokes.values() for o in cs})
    others = {s: found[0] for s, found in stack.ask(every).items() if found}
    # An outline Typey Type gives up to the rules is still its own here, so
    # what this dictionary takes does not turn on what it took last time.
    with open(g.SUPERSEDED) as f:
        others.update((s, ("typey-type", t)) for s, t in json.load(f).items() if s not in others)
    retro = {s: ("retro-words", "") for s in retro_words()}
    others.update((s, retro[s]) for s in every if s in retro)
    # An inflection ranks as its base, so the base that wins a stroke also
    # wins the stroke its suffix key folds to: agrees goes with agree. A Typey
    # Type entry gives way unless it is an affix or a hundred times as
    # frequent: compress is KPHRES here, so its old KPRES can be express.
    typey_way = typey_gives_way(typey.reserved)
    # A pair's first outline in two-word outranks a word's second, and a
    # fallback is only for a word that has no outline of its own.
    pairs = {next(iter(cs)) for cs in candidates("two-word").values()}
    out, lost, left = resolve(strokes, lambda w: (-g.zipf(stem(w)), -g.zipf(w), w), others,
                              lambda s, w, other: may_displace(s, strokes[w], stem(w))
                              and typey_way(s, w, other), firsts=pairs | {o for _, o in fallback})
    written = {}
    for o, w in out.items():
        written.setdefault(w, o)
    folded = {s: w for s, w in out.items() if w in base and out.get(base[w][1].get(s)) == base[w][0]}
    for s in folded:
        del out[s]
    return dict(sorted(out.items())), dict(strokes=strokes, base=base, folded=folded, written=written,
                                          fallback=fallback, lost=lost, left=left, stack=stack)


def write_json(d, path):
    with open(path, "w") as f:
        f.write("{\n" + ",\n".join(f"{json.dumps(k)}: {json.dumps(v)}" for k, v in d.items()) + "\n}\n")


def report(out, r):
    z = g.zipf
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 10]) for i in range(0, len(items), 10))
    strokes, lost, left = r["strokes"], r["lost"], r["left"]
    print(f"{len(strokes)} words in scope; {len(out)} entries, and {len(r['folded'])} "
          f"inflections the suffix keys fold")
    have = set(out.values()) | set(r["folded"].values())
    freq = lambda ws: sum(10 ** z(w) for w in ws)
    written = r["written"]
    per_word = sum((o.count("/") + 1) * 10 ** z(w) for w, o in written.items()) / freq(written)
    print(f"{len(have)} words have an outline; coverage {freq(have) / 1e6:.1f} (the sum of their "
          f"frequencies per million words), {freq(set(strokes) - have) / freq(strokes):.3%} of the "
          f"frequency in scope has none, {per_word:.3f} strokes per word")
    by = collections.defaultdict(list)
    for w, o in written.items():
        by[r["fallback"].get((w, o))].append(w)
    for name in ("theory", "typey"):
        print(f"{len(by[name])} words ({freq(by[name]) / freq(strokes):.3%}) fall back to the {name} "
              f"outlines:", " ".join(f"{w}={written[w]}" for w in sorted(by[name], key=lambda w: -z(w))[:30]))
    lost.sort(key=lambda x: (-z(x[0]), x))
    infl = r["base"]
    words = [x for x in lost if x[0] not in infl]
    print(f"{len(words)} words lose their stroke to another word, "
          f"{sum(1 for w, _, _ in words if z(w) > 4)} of zipf > 4, and {len(lost) - len(words)} "
          f"inflections:")
    print(wrap([f"{w}~{win}" for w, win, _ in words]))
    print(wrap([f"{w}~{win}" for w, win, _ in lost if w in infl]))
    left.sort(key=lambda x: (-z(x[0]), x[:2]))
    print(f"{len(left)} leave their stroke to another dictionary:")
    print(wrap([f"{w}={s}({n}:{t})" for w, s, (n, t) in left]))

    print("Each rewrite, the words in scope it rewrites: how many have an entry, lose their stroke to "
          "another word, and take another's:")
    stages = pipeline(NAME)
    names = [s for s, _ in stages]
    rewrites = stages[names.index("written"):names.index("split")]
    losers, winners = {w for w, _, _ in lost}, {w for _, w, _ in lost}
    rels = [relation(path) for _, path in rewrites]
    for (stage, _), before, after in zip(rewrites[1:], rels, rels[1:]):
        touched = {w for w in strokes if before.get(w) != after.get(w)}
        print(f"  {stage}: {len(touched)}, {len(touched & r['written'].keys())}, {len(touched & losers)}, "
              f"{len(touched & winners)}")

    with open(os.path.join(DICTS, "user.json")) as f:
        user = json.load(f)
    same = sorted(s for s, t in user.items() if out.get(s) == t)
    print(f"{len(same)} user.json entries the rule reproduces:", " ".join(f"{s}={user[s]}" for s in same))
    theirs = {t: s for s, t in user.items() if marked(s)[0]}
    every = collections.defaultdict(set)
    for o, w in out.items():
        every[w].add(o)
    ours = lambda w: r["written"].get(w, next(iter(strokes[w])))
    other = sorted((w, theirs[w], ours(w)) for w in strokes if w in theirs and theirs[w] not in every[w])
    print(f"{len(other)} user.json marked entries the rule writes differently:",
          " ".join(f"{w}={a}/{b}" for w, a, b in other))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the committed JSON is stale")
    args = ap.parse_args()

    if not args.check:
        compile_pipeline(NAME)
    out, r = build(g.Typey.load(), candidates(NAME), lex.vocabulary())
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
    else:
        write_json(out, OUTPUT)
        report(out, r)
    if (missing := unwritten_briefs(NAME, out)):
        sys.exit(f"tests/briefs/{NAME}.tsv: the rule no longer writes " + " ".join(f"{o}={t}" for o, t in missing))
