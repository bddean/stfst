"""Write `dictionaries/theory-tables.xfst`: the tables `dictionaries/theory.xfst`
writes a syllable's stroke by.

- `Left`, each onset phoneme's keys: the first way `vowels.LEFT` writes it.

- `VowelRules`, the vowel table of `dictionaries/typey-vowels.toml`: the onset's
  letters, a space, then the vowel's sound and spelling, to its chord. The first
  rule that matches wins.
- `CodaSpelled` and `CodaSounded`, how one-syllable words write a coda: the keys,
  and whether the star is the coda's, that the vowel theory gave most words with
  the same sounds and letters, or failing that the same sounds.
- `CodaSplit`, a coda no word has, written as two that some words have: the
  split whose rarer half the most words attest.

Usage (needs cmudict and wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-theory.py            # write the tables
    $PY scripts/gen-theory.py --check    # fail if they are stale
"""
import argparse
import collections
import os
import sys

from . import ROOT
from . import vowels as g

TABLES = os.path.join(ROOT, "dictionaries", "theory-tables.xfst")
RIGHT = "FRPBLGTSDZ"


class Coda:
    """How the vowel theory's one-syllable words write each coda, counted."""

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

    def spelled(self):
        """{(phonemes, letters): (keys, star)}."""
        return {k: c.most_common(1)[0][0] for k, c in self.by.items() if k and isinstance(k[0], tuple)}

    def sounded(self):
        """{phonemes: (keys, star)}."""
        return {k: c.most_common(1)[0][0] for k, c in self.by.items() if not k or isinstance(k[0], str)}

    def splits(self):
        """{phonemes: (keys, star)} for each coda no word has that two codas
        some words have make: the split whose rarer half the most words attest."""
        sounded = self.sounded()
        count = {p: sum(self.by[p].values()) for p in sounded}
        out = {}
        for p in {a + b for a in sounded for b in sounded if a and b} - sounded.keys():
            halves = [(p[:k], p[k:]) for k in range(1, len(p)) if p[:k] in sounded and p[k:] in sounded]
            a, b = max(halves, key=lambda h: min(count[h[0]], count[h[1]]))
            (ka, sa), (kb, sb) = sounded[a], sounded[b]
            out[p] = "".join(c for c in RIGHT if c in ka + kb), sa or sb
        return out


# --- xfst ------------------------------------------------------------------------

def phones(ps):
    return " Dot ".join(ps) if ps else "0"


def letters(text):
    return "{" + text + "}" if text else "0"


def keys(coda, star):
    """The tape of a coda's keys, the star first."""
    out = ['"*"'] * star + [k.lower() for k in RIGHT if k in coda]
    return " ".join(out) or "0"


def chord(text):
    """The tape of a chord, in steno order."""
    return " ".join('"*"' if k == "*" else k for k in "AO*EU" if k in text)


def union(items, sep="\n    | "):
    return "[ " + sep.join(items) + " ]"


def either(items):
    return union(items, " | ")


def vowel_rules(table):
    lines = []
    for i, r in enumerate(table, 1):
        after = f"[ Letter* {either([letters(a) for a in r.after])} ]" if r.after else "Letter*"
        sound = either(r.sound) if r.sound else "?"
        spelling = either([letters(s) for s in r.spelling])
        lines.append(f"! {r.name}\n"
                     f"define VowelRule{i} [ {after} Gap [ [ {sound} Eq {spelling} ] .x. [ {chord(r.chord)} ] ] ] ;")
    n = len(table)
    lines.append(f"define VowelRules{n} VowelRule{n} ;")
    for i in range(n - 1, 0, -1):
        lines.append(f"define VowelRules{i} PU(VowelRule{i}, VowelRules{i + 1}) ;")
    lines.append("define VowelRules VowelRules1 ;")
    return "\n".join(lines)


def table(name, comment, entries, size=64):
    """`define NAME` as the union of `entries`, a line each. A union of thousands
    is slow to parse flat, so it is built from unions of `size`."""
    if len(entries) <= size:
        return f"! {comment}\ndefine {name} " + union(entries) + " ;"
    groups = [entries[i:i + size] for i in range(0, len(entries), size)]
    parts = [f"define {name}{n} " + union(group) + " ;" for n, group in enumerate(groups, 1)]
    return "\n".join([f"! {comment}"] + parts + [f"define {name} " + either(
        [f"{name}{n}" for n in range(1, len(groups) + 1)]) + " ;"])


def tables(rules, coda):
    spelled, sounded, splits = coda.spelled(), coda.sounded(), coda.splits()
    rel = os.path.relpath(g.TABLE, ROOT)
    entry = lambda upper, v: f"[ [ {upper} ] .x. [ {keys(*v)} ] ]"
    return "\n\n".join([
        f"! Written by scripts/gen-theory.py from {rel} and the vowel theory's strokes.\n"
        "! Do not edit: see scripts/gen/theory.py for what these are.",
        "define Left " + either([f"[ {p} .x. {letters(ways[0])} ]" for p, ways in g.LEFT.items()]) + " ;",
        vowel_rules(rules),
        table("CodaSpelled", f"{len(spelled)} codas by sound and spelling.",
              [entry(f"{phones(p)} Eq {letters(l)}", v) for (p, l), v in sorted(spelled.items()) if p]),
        table("CodaSounded", f"{len(sounded)} codas by sound.",
              [entry(phones(p), v) for p, v in sorted(sounded.items()) if p]),
        table("CodaSplit", f"{len(splits)} codas no word has, as two that some do.",
              [entry(phones(p), v) for p, v in sorted(splits.items())]),
    ]) + "\n"


def load():
    """(the vowel table, the Coda of the vowel theory as it resolves today)."""
    typey = g.Typey.load()
    table, _ = g.load_table()
    return table, Coda(typey, g.resolve(typey, table))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the tables are stale")
    args = ap.parse_args()
    text = tables(*load())
    if args.check:
        if not os.path.exists(TABLES) or open(TABLES).read() != text:
            sys.exit(f"{TABLES} is stale: run {sys.argv[0]}")
        return
    with open(TABLES, "w") as f:
        f.write(text)
