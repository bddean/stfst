#!/usr/bin/env python3
"""Write `~/.config/plover/leading-vowel.json`: a leading syllable merged into the stroke.

A word of two syllables stressed on the second (report, suppose, about) is
written as one stroke, and `^` says a syllable was merged in before the
stressed one:

- the left bank is every consonant before the stressed vowel, the first
  syllable's and the second's, in steno order (suppose is `SP`);
- the chord is the stressed vowel's chord under `dictionaries/typey-vowels.toml`,
  with the first syllable's vowel letter added (a `A`, e `E`, i `EU`, o `O`,
  u `U`);
- the right bank is the stressed syllable's coda, as a one-syllable word with
  the same coda writes it.

So report is `^PROERT`, support `^SPOURT`, about `^PWAOUT`. An -s or -ed form is
its word's stroke and the suffix key, and is not an entry, since the key folds
(allowed is `^HRAOU` + `-D`).

Two words on one stroke go to the more frequent. A stroke another dictionary in
the stack writes is left to it.

Usage (needs cmudict and wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-leading-vowel.py            # write the JSON, print the report
    $PY scripts/gen-leading-vowel.py --check    # fail if the committed JSON is stale
"""
import argparse
import collections
import importlib.util
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("gen_typey_vowels", os.path.join(HERE, "gen-typey-vowels.py"))
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)

OUTPUT = os.path.join(g.PLOVER, "leading-vowel.json")
NAME = "leading-vowel"

# A word below this is not worth a stroke.
MIN_ZIPF = 3.0

PREFIX_VOWEL = {"a": "A", "e": "E", "i": "EU", "o": "O", "u": "U", "y": "EU"}

# The first way of writing each consonant on the left. z spelled s is S.
LEFT = {p: keys[0] for p, keys in g.LEFT.items()}


def two_syllables(pron):
    """(the pronunciation without its first vowel, whether the two vowels are
    adjacent), when the second of two vowels has the main stress. An r-coloured
    first vowel leaves its R."""
    vs = [i for i, p in enumerate(pron) if p[-1].isdigit()]
    if len(vs) != 2 or pron[vs[0]][-1] == "1" or pron[vs[1]][-1] != "1":
        return None
    i, j = vs
    er = pron[i][:-1] == "ER"
    return pron[:i] + ["R"] * er + pron[i + 1:], j == i + 1 and not er


def split(word, hiatus):
    """(the first vowel's letters, the rest of the word's Spelling), or None when
    the rest is not one syllable. In a hiatus (react, create) the first vowel is
    one letter of the run."""
    runs = g.vowel_runs(word)
    if not runs:
        return None
    i, j, v = runs[0]
    if hiatus:
        if len(v) < 2:
            return None
        v, j = v[0], i + 1
    sp = g.spell(word[j:])
    if not sp or bool(sp.onset) == hiatus:
        return None
    return v, sp


class Coda:
    """How one-syllable words write a coda: the keys, and whether the star is
    the coda's, that the vowel theory gave most words with the same sounds and
    letters, or failing that the same sounds. A coda no word has is written as
    two that some words have."""

    def __init__(self, typey, result):
        self.by = collections.defaultdict(collections.Counter)
        for stroke, word in result.owner.items():
            if word in result.kept:
                continue
            w = typey.words[word]
            s = g.Stroke.parse(stroke)
            star = s.star and w.consonant_star
            for _, coda in w.phones:
                self.by[(tuple(coda), w.letters_after)][(s.coda, star)] += 1
                self.by[tuple(coda)][(s.coda, star)] += 1

    def __call__(self, phones, letters=None):
        phones = tuple(phones)
        for key in ((phones, letters), phones):
            if self.by.get(key):
                return self.by[key].most_common(1)[0][0]
        # The split whose rarer half the most words attest.
        halves = [(phones[:k], phones[k:]) for k in range(1, len(phones))]
        halves = [h for h in halves if self.by.get(h[0]) and self.by.get(h[1])]
        if not halves:
            return None
        a, b = max(halves, key=lambda h: min(sum(self.by[x].values()) for x in h))
        (ka, sa), (kb, sb) = self(a), self(b)
        return "".join(c for c in "FRPBLGTSDZ" if c in ka + kb), sa or sb


def stroke_for(word, table, coda):
    """The rule's stroke for `word`, or None when the word is not in its scope."""
    for pron in g.prons(word):
        two = two_syllables(pron)
        if not two:
            continue
        rest, hiatus = two
        parts = split(word, hiatus)
        st = g.stressed(rest)
        if not parts or not st:
            continue
        v1, sp = parts
        onset, sound, co = st
        rule = g.rule_for(table, sp, sound)
        right = coda(co, re.sub(r"g?h$", "", sp.coda))
        if not rule or right is None or any(p not in LEFT for p in onset):
            continue
        left = set()
        for p in onset:
            left |= set("S" if p == "Z" and "z" not in word else LEFT[p])
        vowel = set(rule.keys) | set(PREFIX_VOWEL.get(v1[0], ""))
        keys, star = right
        return "^" + str(g.Stroke("".join(k for k in "STKPWHR" if k in left),
                                  "".join(k for k in "AOEU" if k in vowel),
                                  rule.star or star, keys))
    return None


# --- Inflections ---------------------------------------------------------------

def regular_forms(base, kind):
    """The regular -s or -ed spellings of `base`."""
    b = base
    if kind == "s":
        forms = {b + "s"}
        if b[-1:] in "sxz" or b[-2:] in ("ch", "sh"):
            forms.add(b + "es")
        if b[-1:] == "y" and b[-2:-1] not in "aeiou":
            forms.add(b[:-1] + "ies")
        return forms
    forms = {b + "d" if b.endswith("e") else b + "ed"}
    if len(b) > 2 and b[-1] not in "aeiouwxy" and b[-2] in "aeiou" and b[-3] not in "aeiou":
        forms.add(b + b[-1] + "ed")
    if b[-1:] == "y" and b[-2:-1] not in "aeiou":
        forms.add(b[:-1] + "ied")
    return forms


SUFFIX_KEYS = {"S": "s", "D": "d"}


def with_coda(stroke, coda):
    s = g.Stroke.parse(stroke[1:])
    return "^" + str(g.Stroke(s.onset, s.vowel, s.star, "".join(k for k in "FRPBLGTSDZ" if k in coda)))


def compose(strokes, words):
    """Give each of `words` that inflects a word in `strokes` that word's stroke
    and the suffix key, which is what the fold writes: allowed is allow and -D.
    Returns {inflection: base}."""
    base = {}
    for w in words:
        for b in (w[:-1], w[:-2], w[:-3], w[:-3] + "y"):
            for key, kind in SUFFIX_KEYS.items():
                coda = g.Stroke.parse(strokes[b][1:]).coda if b in strokes else key
                if key not in coda and w in regular_forms(b, kind):
                    strokes[w] = with_coda(strokes[b], coda + key)
                    base[w] = b
    return base


# --- The stack -------------------------------------------------------------------

def reserved():
    """Strokes `retro-words.py` claims. Its artifact rewrites text and answers no
    lookup, so the stack cannot say."""
    path = os.path.join(g.PLOVER, "retro-words.py")
    spec = importlib.util.spec_from_file_location("retro_words", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return set(m._ENTRIES)


def build(typey, table, result):
    """(entries, report data)."""
    coda = Coda(typey, result)
    words = [w for w in typey.known if re.fullmatch("[a-z]+", w)]
    strokes = {w: s for w in words if (s := stroke_for(w, table, coda))}
    base = compose(strokes, words)
    strokes = {w: s for w, s in sorted(strokes.items()) if g.zipf(w) >= MIN_ZIPF}
    with open(g.OUTPUT) as f:
        stack = g.Stack(json.load(f))
    answers = stack.ask(strokes.values())
    retro = reserved()
    others = {}
    for s, found in answers.items():
        found = [(n, t) for n, t in found if n != NAME]
        if found:
            others[s] = found[0]
    others.update((s, ("retro-words", "")) for s in strokes.values() if s in retro)

    # An inflection ranks as its base, so the base that wins a stroke also
    # wins the stroke its suffix key folds to: agrees goes with agree.
    by = collections.defaultdict(list)
    for w, s in strokes.items():
        by[s].append(w)
    out, lost, left = {}, [], []
    for s, ws in by.items():
        ws.sort(key=lambda w: (-g.zipf(base.get(w, w)), -g.zipf(w), w))
        # Another dictionary writing the rule's own choice is the same entry.
        if s in others and others[s][1] != ws[0]:
            left += [(w, s, others[s]) for w in ws]
            continue
        out[s] = ws[0]
        lost += [(w, ws[0], s) for w in ws[1:]]
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

    old = json.loads(subprocess.run(["git", "-C", g.PLOVER, "show", f"HEAD:./{NAME}.json"],
                                    capture_output=True, text=True).stdout or "{}")
    new_words = set(out.values()) | set(r["folded"].values())
    gone = sorted(set(old.values()) - new_words, key=lambda w: (-z(w), w))
    print(f"old file: {len(old)} entries for {len(set(old.values()))} words; "
          f"{len(set(old.values()) & new_words)} of them have a stroke here, by entry or fold")
    orphans = r["stack"].unwritable(gone)
    print(f"{len(gone)} lose their ^ stroke, and {len(orphans)} of those no outline in the stack writes:")
    print(wrap(orphans))
    print("the rest keep an ordinary outline:")
    print(wrap([w for w in gone if w not in orphans][:80]))

    with open(os.path.join(g.PLOVER, "user.json")) as f:
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

    typey = g.Typey.load()
    table, _ = g.load_table()
    result = g.resolve(typey, table)
    out, r = build(typey, table, result)
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
        return
    write_json(out, OUTPUT)
    report(out, r)


if __name__ == "__main__":
    main()
