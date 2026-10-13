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
- Two are one syllable around the stressed vowel, marked `^`: the left bank is
  every consonant before it, the right bank every consonant after it, and the
  chord is its chord XOR the other vowel's first letter (a `A`, e `E`, i and y
  `EU`, o `O`, u `U`), a key both have not pressed. An r-coloured other vowel
  is its R and adds no key. So report is `^PROERT`, happy `^HAEUP` and city
  `^S-T`.
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
- A stroke with a vowel whose onset or coda no one-syllable word's own stroke
  has is also written without `^`, since the bank already says two syllables:
  outside is `^OETSD` and `OETSD`.
- A common word of one syllable that Typey Type does not write is its plain
  stroke (sheikh).

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
import sys

from . import DICTS
from . import lexicon as lex
from . import vowels as g
from .resolve import (candidates, compile_pipeline, pipeline, relation, resolve, typey_gives_way,
                      unwritten_briefs)
from .stack import Stack, retro_words

OUTPUT = os.path.join(DICTS, "two-syllable.json")
NAME = "two-syllable"


# --- Inflections ---------------------------------------------------------------

SUFFIX_KEYS = {"S": "s", "D": "d"}


def with_coda(stroke, coda):
    mark = stroke[:1] if stroke.startswith("^") else ""
    s = g.Stroke.parse(stroke[len(mark):])
    return mark + str(g.Stroke(s.onset, s.vowel, s.star, "".join(k for k in "FRPBLGTSDZ" if k in coda)))


def coda(outline):
    return g.Stroke.parse(outline.lstrip("^")).coda


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


# --- Without the caret -----------------------------------------------------------

def banks(typey):
    """The onsets and codas of the one-syllable words' own strokes."""
    on, co = set(), set()
    for w in typey.words.values():
        for o in w.outlines:
            s = g.Stroke.parse(o)
            if g.is_own_stroke(w, s, typey.habit):
                on.add(s.onset)
                co.add(s.coda)
    return on, co


def bare(out, typey, strokes):
    """{stroke without `^`: word} for each entry whose bank says two syllables,
    and which the rule did not already offer without `^`."""
    on, co = banks(typey)
    found = {}
    for o, w in out.items():
        s = g.Stroke.parse(o[1:]) if o.startswith("^") else None
        if s and s.vowel and (s.onset not in on or s.coda not in co) and o[1:] not in strokes[w]:
            found[o[1:]] = w
    return found


def bare_entries(name, out, typey, strokes, stack, held, gives_way):
    """({stroke: word}, {stroke: (word, (dictionary, translation))}): the bare
    strokes of `out`, a dictionary's {stroke: word}, that it writes, and those
    something else keeps. `held` is {stroke: (dictionary, translation)} for what
    keeps a stroke the stack does not answer for."""
    plain = bare(out, typey, strokes)
    taken = {s: (plain[s], theirs[0]) for s, theirs in stack.ask(plain).items()
             if theirs and theirs[0][1] != plain[s] and not gives_way(s, plain[s], theirs[0])}
    taken.update((s, (w, (name, out[s]))) for s, w in plain.items() if s in out)
    taken.update((s, (w, held[s])) for s, w in plain.items() if s in held)
    return {s: w for s, w in plain.items() if s not in taken}, taken


def syllables(w):
    return sum(p[-1].isdigit() for p in g.prons(w)[0])


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
    # A plain stroke takes nothing: where it is taken, the word has the same
    # keys with `^`.
    # A pair's first outline in two-word outranks a word's second.
    pairs = {next(iter(cs)) for cs in candidates("two-word").values()}
    out, lost, left = resolve(strokes, lambda w: (-g.zipf(stem(w)), -g.zipf(w), w), others,
                              lambda s, w, other: "^" + s not in strokes[w] and syllables(stem(w)) > 1
                              and typey_way(s, w, other), firsts=pairs)
    written = {}
    for o, w in out.items():
        written.setdefault(w, o)
    folded = {s: w for s, w in out.items() if w in base and out.get(base[w][1][s]) == base[w][0]}
    for s in folded:
        del out[s]
    # A bare stroke takes a Typey Type one as a `^` stroke does, and also when
    # this dictionary writes that word: only keeps `^OEUPBL`, so omen is
    # `OEPBL`. The rest are reported.
    ours = set(out.values())
    plain, taken = bare_entries(NAME, out, typey, strokes, stack, retro,
                                lambda s, w, other: typey_way(s, w, other)
                                or (other[0] == "typey-type" and other[1] in ours))
    out.update(plain)
    return dict(sorted(out.items())), dict(strokes=strokes, base=base, folded=folded, written=written,
                                          lost=lost, left=left, stack=stack,
                                          bare=plain, bare_taken=taken)


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
    print(f"{len(have)} words have a stroke; coverage {sum(10 ** z(w) for w in have) / 1e6:.1f} "
          f"(the sum of their frequencies per million words)")
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
    b = sorted(r["bare"].items(), key=lambda x: (-z(x[1]), x))
    print(f"{len(b)} are also written without ^, and {len(r['bare_taken'])} could be but that stroke is taken:")
    print(wrap([f"{w}={s}" for s, w in b]))
    print(wrap([f"{w}={s}({n}:{t})" for s, (w, (n, t)) in r["bare_taken"].items()]))

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
    theirs = {t: s for s, t in user.items() if s.startswith("^")}
    every = collections.defaultdict(set)
    for o, w in out.items():
        every[w].add(o)
    ours = lambda w: r["written"].get(w, next(iter(strokes[w])))
    other = sorted((w, theirs[w], ours(w)) for w in strokes if w in theirs and theirs[w] not in every[w])
    print(f"{len(other)} user.json ^ entries the rule writes differently:",
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
