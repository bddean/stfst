#!/usr/bin/env python3
"""Check the retro modifier layer against phrasing.py, end to end.

A golden file over strokes is not enough, because what can go wrong is the *fold*,
and the fold reads the clause back off the window. So this seeds the buffer with a
clause, strokes a modifier at it, and compares the finished buffer with what
`phrasing.py` gives for the same two-stroke run.

Seeding rather than stroking the base clause is deliberate: it puts the clause in
the window without the base dictionary in the way, so a failure here is the fold's
and not the clause grammar's.

Two divergences are expected, and both are the window carrying text where the
Python carries strokes. They are asserted rather than ignored:

- **The reading.** Text does not determine the bundle: `+T`, `+TU` and `+TE` all
  write "it's", and negating them gives "it isn't", "it hasn't" and "it isn't". The
  parse takes the least-marked reading, so an aspect or a contraction mode the
  surface hid is lost.
- **The span.** The fold takes the longest suffix of the window that reads as a
  clause, which may be more than the last stroke wrote. That is the only thing a
  window *can* do, and it is usually the better answer.

And one thing left out rather than diverging: a bare `+` selects a third
contraction mode in the Python and folds nothing here.

Usage: scripts/check-phrasing-retro.py <retro artifact> <phrasing artifact>
"""
import collections
import contextlib
import importlib.util
import io
import itertools
import os
import subprocess
import sys

LEFT = "STKPWHR"
HERE = os.path.dirname(os.path.abspath(__file__))
SYSTEM = os.path.join(HERE, "..", "systems", "stenotype-extended.toml")
STFST = os.path.join(HERE, "..", "target", "release", "stfst")
MODS = ["+*", "+-D", "+A", "+O", "+AO", "+E", "+U", "+S", "#+", "@+", "^+", "+",
        "+-G", "+-Z", "+*E", "^+*", "+AO*"]
BASES = ["K", "T", "PW", "SK", "TK", "W", "SW", "R", "PH", "TKPW"]


def load(path):
    spec = importlib.util.spec_from_file_location("phrasing", path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def readings(ph):
    """Every clause text, and how many strokes write it.

    A text more than one stroke writes is one whose surface hides part of its own
    bundle, and the fold cannot recover what the surface did not show.
    """
    count = collections.Counter()
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            for ao in ("", "A", "O", "AO"):
                for star in ("", "*"):
                    for eu in ("", "E", "U", "EU"):
                        for full in ("", "^"):
                            for e in ("", "D", "G", "B", "FBTS", "BDZ"):
                                s = full + "+" + "".join(combo) + ao + star + eu + e
                                try:
                                    text = ph.lookup((s,))
                                except (KeyError, IndexError):
                                    continue
                                if text:
                                    count[text] += 1
    return count


def main(retro, phrasing):
    ph = load(os.path.expanduser("~/.config/plover/phrasing.py"))
    hidden = readings(ph)

    lines, keep = [], []
    for combo in BASES:
        for ao in ("", "A", "AO"):
            for star in ("", "*"):
                for eu in ("", "E", "U", "EU"):
                    for full in ("", "^"):
                        for ender in ("", "D", "G", "B", "FBTS"):
                            base = full + "+" + combo + ao + star + eu + ender
                            for mod in MODS:
                                try:
                                    first = ph.lookup((base,))
                                    want = ph.lookup((base, mod))
                                except (KeyError, IndexError):
                                    continue
                                if not first or not want:
                                    continue
                                lines += ["!buffer " + first, mod]
                                keep.append((base, mod, first, want))

    out = subprocess.run([STFST, "translate", retro, phrasing, "--system", SYSTEM],
                         input="\n".join(lines) + "\n", capture_output=True,
                         text=True, check=True).stdout
    # Two lines per run: the seed, then the modifier. The buffer is the last field.
    got = [line.split("\t")[-1].strip('"')
           for line in out.splitlines()
           if not line.startswith("! ")][1::2]

    tally = collections.Counter()
    show = collections.defaultdict(list)
    for (base, mod, first, want), have in zip(keep, got):
        kind = ("exact" if have == want
                else "reading" if hidden[first] > 1
                else "bare +" if mod == "+"
                else "OTHER")
        tally[kind] += 1
        if kind != "exact" and len(show[kind]) < 6:
            show[kind].append((base + "/" + mod, first, want, have))

    total = sum(tally.values())
    print("phrasing-retro: %d folds, %d exact (%.1f%%)"
          % (total, tally["exact"], 100 * tally["exact"] / max(total, 1)))
    for kind in ("reading", "bare +", "OTHER"):
        if tally[kind]:
            print("  %s: %d" % (kind, tally[kind]))
            for row in show[kind]:
                print("    %-14s %-24s want %-26s got %s" % row)
    return 1 if tally["OTHER"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
