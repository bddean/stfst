#!/usr/bin/env python3
"""Check the retro modifier layer against phrasing.py.

`phrasing.py` takes a clause stroke and up to seven modifiers after it, and
re-realizes the whole run. This dictionary does the same thing over the same run
written newest-first, so a run the writer strokes as

    +T  then  +*  then  ^+

is the outline `^+/+*/+T` here. That reversal is the whole reason it is affordable,
and it is why this checker can run with no host change at all: the reversed run is
an ordinary multi-stroke outline, so `stfst lookup` answers it. What the host still
has to learn is to hand a dictionary its own run in that order.

Coverage is the product of the dimensions a modifier can touch, because that is
where the rules are: every field of the delta, alone and in the combinations that
interact. Runs of one, two and three modifiers, against clauses that reach every
shape the fold has to read back.

Usage: scripts/check-phrasing-retro.py <retro artifact>
"""
import collections
import contextlib
import importlib.util
import io
import itertools
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SYSTEM = os.path.join(HERE, "..", "systems", "stenotype-extended.toml")
STFST = os.path.join(HERE, "..", "target", "release", "stfst")

# One modifier per field of the delta, plus the ones that interact: `A O` is set
# absolutely so the last wins, `*` and `#` toggle, and `@` counts.
MODS = ["+*", "+-D", "+A", "+O", "+AO", "+E", "+U", "+S", "#+", "@+", "^+", "+",
        "+-G", "+-Z", "+*E", "^+*", "+AO*", "#+*"]
BASES = ["K", "T", "PW", "SK", "TK", "W", "SW", "R", "PH", "TKPW", "HR", ""]


def load(path):
    spec = importlib.util.spec_from_file_location("phrasing", path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def clauses():
    for combo in BASES:
        for ao in ("", "A", "AO"):
            for star in ("", "*"):
                for eu in ("", "E", "U", "EU"):
                    for full in ("", "^"):
                        for ender in ("", "D", "G", "B", "FBTS"):
                            yield full + "+" + combo + ao + star + eu + ender


def runs(ph):
    """Every (run, expected) pair, the run oldest-first as the writer strokes it."""
    out = []
    for base in clauses():
        try:
            if not ph.lookup((base,)):
                continue
        except (KeyError, IndexError):
            continue
        for one in MODS:
            out.append((base, (one,)))
        for a, b in itertools.combinations(MODS, 2):
            out.append((base, (a, b)))
        for a in ("+*", "@+", "+A"):
            for b in ("^+", "+E", "#+"):
                out.append((base, (a, b, "+-D")))
    return out


def main(retro):
    ph = load(os.path.expanduser("~/.config/plover/phrasing.py"))
    cases, want = [], []
    for base, mods in runs(ph):
        try:
            text = ph.lookup((base,) + mods)
        except (KeyError, IndexError):
            text = None
        if not text:
            continue
        # Newest-first, so the clause is last and the delta is known before it.
        cases.append("/".join(reversed((base,) + mods)))
        want.append(text)

    out = subprocess.run([STFST, "lookup", retro, "--system", SYSTEM],
                         input="\n".join(cases) + "\n", capture_output=True,
                         text=True, check=True).stdout
    got = {}
    for line in out.splitlines():
        f = line.split("\t")
        got[f[0]] = None if len(f) < 3 or f[2] == "-" else f[2]

    tally = collections.Counter()
    show = collections.defaultdict(list)
    for outline, expected in zip(cases, want):
        have = got.get(outline)
        if have == expected:
            tally["exact"] += 1
            continue
        # The clause grammar's one deliberate divergence reaches here too: an
        # adverb launchpad with no auxiliary drops negation, the question and
        # emphasis in the Python, and do-support is what English does. See
        # docs/phrasing.md.
        kind = "launchpad" if _launchpad(ph, outline) else "OTHER"
        tally[kind] += 1
        if len(show[kind]) < 6:
            show[kind].append((outline, expected, have))

    total = sum(tally.values())
    print("phrasing-retro: %d runs, %d exact (%.1f%%)"
          % (total, tally["exact"], 100 * tally["exact"] / max(total, 1)))
    for kind in ("launchpad", "OTHER"):
        if tally[kind]:
            print("  %s: %d" % (kind, tally[kind]))
            for row in show[kind]:
                print("    %-22s want %-24s got %s" % row)
    return 1 if tally["OTHER"] else 0


def _launchpad(ph, outline):
    """Is the clause an adverb launchpad the run leaves without an auxiliary?

    The Python has nothing for negation, the question or emphasis to sit on there
    and drops them; this supplies `do`, which is what English does.
    """
    strokes = outline.split("/")
    base = ph._parse(strokes[-1])
    if base is None or base["right"] not in ph.ADV:
        return False
    fields = [base] + [ph._parse(s) for s in strokes[:-1]]
    if any(f is None for f in fields):
        return False
    if any(f["A"] or f["O"] or f["E"] or f["U"] for f in fields):
        return False
    return any(f["neg"] or f["question"] or f["emph"] for f in fields)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
