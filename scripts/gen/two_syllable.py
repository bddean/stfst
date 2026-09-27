"""Write `~/.config/plover/two-syllable.json`: a word of two syllables as one stroke.

`^` says the stroke holds two syllables. Either may carry the stress:

- the left bank is every consonant before the stressed vowel, in steno order;
- the right bank is every consonant after it, as one-syllable words write
  those sounds;
- the chord is the stressed vowel's chord under `dictionaries/typey-vowels.toml`
  XOR the other vowel's letter (a `A`, e `E`, i and y `EU`, o `O`, u `U`): a key
  both have is not pressed.

So report is `^PROERT` (o `O`, e `E`), happy `^HAEUP` (a `A`, y `EU`), about
`^PWAOUT` (ou `OU`, a `A`), and city `^S-T` (i `EU`, y `EU`: no vowel).

A prefix spelled with a cluster no English onset has takes that cluster instead
of `^`, and the rest of the word is one syllable by the table: display is
`STKPHRAEU`.

That rule is the pipeline recipe.toml names: `dictionaries/contract.xfst`
contracts the lexicon's two syllables to one, `theory.xfst` writes it and
`caret.xfst` adds the marks. What this script decides is the rest, which is not
a rule:

- An -s or -ed form is its word's stroke and the suffix key, and is not an
  entry, since the key folds (allowed is `^HRAOU` + `-D`). Nor is a regular -s,
  -ed or -ing form of any other word Typey Type writes (talking is `TAUBG` +
  `-G`).
- Two words on one stroke go to the more frequent. A stroke another dictionary
  in the stack writes is left to it.

Usage (needs wordfreq, which the phrasing venv has, a compiled stack, and HFST
for the pipeline: run it in the dev shell):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-two-syllable.py            # write the JSON, print the report
    $PY scripts/gen-two-syllable.py --check    # fail if the committed JSON is stale
"""
import argparse
import importlib.util
import json
import os
import re
import sys

from . import PLOVER
from . import vowels as g
from .resolve import candidates, compile_pipeline, resolve, typey_gives_way
from .stack import Stack

OUTPUT = os.path.join(PLOVER, "two-syllable.json")
NAME = "two-syllable"

# A word below this is not worth a stroke.
MIN_ZIPF = 3.0


# --- Inflections ---------------------------------------------------------------

SUFFIX_KEYS = {"S": "s", "D": "d"}


def with_coda(stroke, coda):
    mark = stroke[:1] if stroke.startswith("^") else ""
    s = g.Stroke.parse(stroke[len(mark):])
    return mark + str(g.Stroke(s.onset, s.vowel, s.star, "".join(k for k in "FRPBLGTSDZ" if k in coda)))


def compose(strokes, words):
    """Give each of `words` that inflects a word in `strokes` that word's stroke
    and the suffix key, which is what the fold writes: allowed is allow and -D.
    Returns {inflection: base}."""
    own = dict(strokes)
    base = {}
    for w in words:
        # The most frequent base, when a spelling inflects two.
        for b in sorted({w[:-1], w[:-2], w[:-3], w[:-3] + "y"} & own.keys(), key=lambda b: (-g.zipf(b), b)):
            key = next((k for k, kind in SUFFIX_KEYS.items() if w in g.regular_forms(b, kind)
                        and k not in g.Stroke.parse(own[b].lstrip("^")).coda), None)
            if key:
                strokes[w] = with_coda(own[b], g.Stroke.parse(own[b].lstrip("^")).coda + key)
                base[w] = b
                break
    return base


def inflection(w, known):
    """Whether `w` is a regular -s, -ed or -ing form of a word Typey Type writes."""
    for b in {w[:-1], w[:-2], w[:-3], w[:-3] + "e", w[:-4], w[:-3] + "y", w[:-2] + "y"}:
        if len(b) > 1 and b in known and (w in g.regular_forms(b, "s") | g.regular_forms(b, "d")
                                          or w in (b + "ing", b[:-1] + "ing", b + b[-1] + "ing")):
            return True
    return False


# --- The stack -------------------------------------------------------------------

def reserved():
    """Strokes `retro-words.py` claims. Its artifact rewrites text and answers no
    lookup, so the stack cannot say."""
    path = os.path.join(PLOVER, "retro-words.py")
    spec = importlib.util.spec_from_file_location("retro_words", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return set(m._ENTRIES)


def build(typey, rule):
    """(entries, report data), from `rule`, {word: outline}."""
    words = sorted((w for w in typey.known if re.fullmatch("[a-z]+", w)), key=lambda w: (-g.zipf(w), w))
    strokes = {w: s for w in words if (s := rule.get(w))}
    base = compose(strokes, words)
    # A base ranks as its most frequent form, so exalted folds from exalt.
    z = {w: g.zipf(w) for w in strokes}
    for w, b in base.items():
        z[b] = max(z[b], z[w])
    strokes = {w: s for w, s in sorted(strokes.items())
               if z[w] >= MIN_ZIPF and (w in base or not inflection(w, typey.known))}
    stack = Stack(g.written())
    others = {s: theirs[0] for s, found in stack.ask(strokes.values()).items()
              if (theirs := [(n, t) for n, t in found if n != NAME])}
    retro = reserved()
    others.update((s, ("retro-words", "")) for s in strokes.values() if s in retro)
    # An inflection ranks as its base, so the base that wins a stroke also
    # wins the stroke its suffix key folds to: agrees goes with agree. A Typey
    # Type entry gives way unless it is an affix or a hundred times as
    # frequent: compress is KPHRES here, so its old KPRES can be express.
    out, lost, left = resolve(strokes, lambda w: (-g.zipf(base.get(w, w)), -g.zipf(w), w), others,
                              typey_gives_way(typey.reserved))
    folded = {s: w for s, w in out.items() if w in base and out.get(strokes.get(base[w])) == base[w]}
    for s in folded:
        del out[s]
    return dict(sorted(out.items())), dict(strokes=strokes, base=base, folded=folded,
                                          lost=lost, left=left, stack=stack)


def write_json(d, path):
    with open(path, "w") as f:
        f.write("{\n" + ",\n".join(f"{json.dumps(k)}: {json.dumps(v)}" for k, v in d.items()) + "\n}\n")


def report(out, r):
    z = g.zipf
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 10]) for i in range(0, len(items), 10))
    strokes, lost, left = r["strokes"], r["lost"], r["left"]
    print(f"{len(strokes)} words in scope; {len(out)} entries, and {len(r['folded'])} "
          f"inflections the suffix keys fold")
    lost.sort(key=lambda x: (-z(x[0]), x))
    infl = r["base"]
    words = [x for x in lost if x[0] not in infl]
    print(f"{len(words)} words lose their stroke to a more frequent word, "
          f"{sum(1 for w, _, _ in words if z(w) > 4)} of zipf > 4, and {len(lost) - len(words)} "
          f"inflections:")
    print(wrap([f"{w}~{win}" for w, win, _ in words]))
    print(wrap([f"{w}~{win}" for w, win, _ in lost if w in infl]))
    left.sort(key=lambda x: (-z(x[0]), x[:2]))
    print(f"{len(left)} leave their stroke to another dictionary:")
    print(wrap([f"{w}={s}({n}:{t})" for w, s, (n, t) in left]))

    with open(os.path.join(PLOVER, "user.json")) as f:
        user = json.load(f)
    same = sorted(s for s, t in user.items() if out.get(s) == t)
    print(f"{len(same)} user.json entries the rule reproduces:", " ".join(f"{s}={user[s]}" for s in same))
    theirs = {t: s for s, t in user.items() if s.startswith("^")}
    other = sorted((w, theirs[w], s) for w, s in strokes.items() if w in theirs and theirs[w] != s)
    print(f"{len(other)} user.json ^ entries the rule writes differently:",
          " ".join(f"{w}={a}/{b}" for w, a, b in other))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the committed JSON is stale")
    args = ap.parse_args()

    if not args.check:
        compile_pipeline(NAME)
    out, r = build(g.Typey.load(), candidates(NAME))
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
        return
    write_json(out, OUTPUT)
    report(out, r)
