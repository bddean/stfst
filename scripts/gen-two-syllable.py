#!/usr/bin/env python3
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
`STKPHRAEU` (see PREFIXES).

An -s or -ed form is its word's stroke and the suffix key, and is not an entry,
since the key folds (allowed is `^HRAOU` + `-D`). Nor is a regular -s, -ed or
-ing form of any other word Typey Type writes (talking is `TAUBG` + `-G`).

Two words on one stroke go to the more frequent. A stroke another dictionary in
the stack writes is left to it.

Usage (needs cmudict and wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-two-syllable.py            # write the JSON, print the report
    $PY scripts/gen-two-syllable.py --check    # fail if the committed JSON is stale
"""
import argparse
import collections
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("gen_typey_vowels", os.path.join(HERE, "gen-typey-vowels.py"))
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)

OUTPUT = os.path.join(g.PLOVER, "two-syllable.json")
NAME = "two-syllable"

# A word below this is not worth a stroke.
MIN_ZIPF = 3.0

# The unstressed vowel, by its first letter.
LETTER = {"a": "A", "e": "E", "i": "EU", "o": "O", "u": "U", "y": "EU"}

# The first way of writing each consonant on the left. z spelled s is S.
LEFT = {p: keys[0] for p, keys in g.LEFT.items()}


def second_stressed(pron):
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


def first_stressed(word, pron):
    """(onset, sound, the rest's consonants, the first vowel's Spelling, the
    second vowel's letters) when the first of two vowels has the main stress
    and the spelling has two vowel groups. A final e is a vowel only in -le and
    -re (little, acre)."""
    vs = [i for i, p in enumerate(pron) if p[-1].isdigit()]
    if len(vs) != 2 or pron[vs[0]][-1] != "1":
        return None
    runs = g.vowel_runs(word)
    if len(runs) > 1 and runs[-1][2] == "e" and runs[-1][1] == len(word) and word[-2:] not in ("le", "re"):
        runs = runs[:-1]
    if len(runs) != 2:
        return None
    i, j = vs
    sound = pron[i][:-1]
    rest = pron[i + 1:j] + ["R"] * (pron[j][:-1] == "ER") + pron[j + 1:]
    if sound == "ER":
        rest = ["R"] + rest
    elif rest[:1] == ["R"]:
        sound += "R"
    (a, b, v1), (c, _, v2) = runs
    return pron[:i], sound, rest, g.Spelling(word[:a], v1, word[b:c]), v2


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
                # By sound alone an inflection misleads: backed would make kt -BGD.
                if w.stem == w.word:
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


# A prefix written as a left-bank cluster no English onset has, so the cluster
# itself says a syllable came first and the stroke needs no `^`. The rest of the
# word is written as a one-syllable word: display is STK + play. Each is the
# prefix's letters and the phonemes they must sound as, a vowel being `V`.
PREFIXES = [
    (("dis", "des", "dys"), ("D", "V", "S|Z"), "STK"),
    (("int", "ent"), ("V", "N", "T"), "SPW"),
    (("ex",), ("V", "K|G", "S|Z"), "KP"),
    (("comb",), ("K", "V", "M", "B"), "KPW"),
    (("emb",), ("V", "M", "B"), "KPW"),
    (("comm", "com"), ("K", "V", "M"), "KPH"),
]


def prefixed(word, pron):
    """(prefix keys, the rest's (onset, sound, coda), the rest's Spelling), or None."""
    for spellings, phones, keys in PREFIXES:
        spelling = next((x for x in spellings if word.startswith(x)), None)
        if not spelling or len(pron) <= len(phones):
            continue
        if not all(p[-1].isdigit() if want == "V" else p in want.split("|")
                   for p, want in zip(pron, phones)):
            continue
        rest = pron[len(phones):]
        sp = g.spell(word[len(spelling):])
        # A y-glide with no y spelled is the vowel's: commute is KPH + ute.
        if rest[:1] == ["Y"] and sp and not sp.onset.startswith("y"):
            rest = rest[1:]
        st = g.stressed([p[:-1] + "1" if p[-1].isdigit() else p for p in rest])
        if st and sp:
            return keys, st, sp
    return None


def stroke_for(word, table, coda):
    """The rule's stroke for `word`, or None when the word is not in its scope."""
    return next((s for pron in g.prons(word) if (s := stroke_of(word, pron, table, coda))), None)


def stroke_of(word, pron, table, coda):
    """The rule's stroke for a spelling with this pronunciation, or None."""
    if sum(p[-1].isdigit() for p in pron) == 2 and (pre := prefixed(word, pron)):
        keys, (onset, sound, co), sp = pre
        rule = g.rule_for(table, sp, sound)
        right = coda(co, re.sub(r"g?h$", "", sp.coda)) if co else ("", False)
        if not rule or right is None or any(p not in LEFT for p in onset):
            return None
        left = set(keys)
        for p in onset:
            left |= set(LEFT[p])
        return str(g.Stroke("".join(k for k in "STKPWHR" if k in left), rule.keys,
                            rule.star or right[1], right[0]))
    if two := second_stressed(pron):
        rest, hiatus = two
        parts = split(word, hiatus)
        st = g.stressed(rest)
        if not parts or not st:
            return None
        other, sp = parts
        onset, sound, co = st
        letters = re.sub(r"g?h$", "", sp.coda)
    elif one := first_stressed(word, pron):
        onset, sound, co, sp, other = one
        letters = None
    else:
        return None
    rule = g.rule_for(table, sp, sound)
    right = coda(co, letters) if co else ("", False)
    if not rule or right is None or any(p not in LEFT for p in onset):
        return None
    left = set()
    for p in onset:
        left |= set("S" if p == "Z" and "z" not in word else LEFT[p])
    vowel = set(rule.keys) ^ set(LETTER.get(other[0], ""))
    keys, star = right
    stroke = g.Stroke("".join(k for k in "STKPWHR" if k in left),
                      "".join(k for k in "AOEU" if k in vowel), rule.star or star, keys)
    if str(stroke) in ("-", "*"):
        return None
    return "^" + str(stroke).rstrip("-")


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
    path = os.path.join(g.PLOVER, "retro-words.py")
    spec = importlib.util.spec_from_file_location("retro_words", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return set(m._ENTRIES)


def build(typey, table, result):
    """(entries, report data)."""
    coda = Coda(typey, result)
    words = sorted((w for w in typey.known if re.fullmatch("[a-z]+", w)), key=lambda w: (-g.zipf(w), w))
    strokes = {w: s for w in words if (s := stroke_for(w, table, coda))}
    base = compose(strokes, words)
    # A base ranks as its most frequent form, so exalted folds from exalt.
    z = {w: g.zipf(w) for w in strokes}
    for w, b in base.items():
        z[b] = max(z[b], z[w])
    strokes = {w: s for w, s in sorted(strokes.items())
               if z[w] >= MIN_ZIPF and (w in base or not inflection(w, typey.known))}
    stack = g.Stack(g.written())
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
        # A Typey Type entry gives way, as it does to the vowel theory, unless
        # it is an affix or a hundred times as frequent: compress is KPHRES
        # here, so its old KPRES can be express.
        if s in others and others[s][0] == "typey-type" and s not in typey.reserved \
                and g.zipf(others[s][1]) <= g.zipf(ws[0]) + g.DISPLACE:
            del others[s]
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
