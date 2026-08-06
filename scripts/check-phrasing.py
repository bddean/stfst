#!/usr/bin/env python3
"""Check phrasing against phrasing.py, with the Python as the oracle.

A golden file cannot hold this dictionary: 128 subject sets times the clause
dimensions times 668 enders is tens of millions of strokes. So the coverage is
split the way the grammar is:

- **Exhaustive over the clause dimensions**, which is the part with rules in it.
  Every left-key set, modality corner, polarity, aspect, tense, contraction mode,
  question and emphasis, against a fixed handful of enders chosen to reach every
  shape: the empty slot, the lone tense key, an adverb, and verbs with a
  pre-satellite, a post-satellite and neither.
- **Exhaustive over the enders**, which are a table, against a handful of clause
  settings.

One divergence is deliberate, and it is asserted rather than ignored. See
`docs/phrasing.md`.

Usage: scripts/check-phrasing.py build/dicts/phrasing.stfst
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


def load(path):
    spec = importlib.util.spec_from_file_location("phrasing", path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def sample_enders(ph):
    """One ender per shape the grammar distinguishes."""
    def find(want):
        for chord, (lemma, sat, tense) in sorted(ph.RHS.items()):
            if tense != "pres":
                continue
            sats = ph.VERBS[lemma]["satellites"]
            pos = sats[sat - 1]["pos"] if sat else None
            if pos == want:
                return chord
        raise AssertionError(want)
    return ["", "D", sorted(ph.ADV)[0], find(None), find("pre"), find("post")]


def strokes(ph):
    """Every stroke to check, as (stroke, why) pairs."""
    out = []
    for ender in sample_enders(ph):
        for n in range(len(LEFT) + 1):
            for combo in itertools.combinations(LEFT, n):
                for ao in ("", "A", "O", "AO"):
                    for star in ("", "*"):
                        for eu in ("", "E", "U", "EU"):
                            for pre in ("", "#", "@", "#@", "^", "#^", "@^", "#@^"):
                                out.append((pre + "+" + "".join(combo) + ao + star
                                            + eu + ender, "clause"))
    for ender in sorted(ph.RHS) + sorted(ph.ADV):
        for left in ("K", "T", "SKWR", "PW", "HR", "STR", ""):
            for pre, mid in (("", ""), ("", "*"), ("#", ""), ("^", ""), ("#^", "*"),
                             ("", "AOU"), ("", "*E"), ("@", ""), ("@", "AO*EU")):
                out.append((pre + "+" + left + mid + ender, "ender"))
    return out


def run(cmd, lines):
    return subprocess.run(cmd, input="\n".join(lines) + "\n", capture_output=True,
                          text=True, check=True).stdout.splitlines()


def main(artifact):
    ph = load(os.path.expanduser("~/.config/plover/phrasing.py"))
    cases = strokes(ph)
    # Both sides must see the same stroke, so the compiler's own parser
    # canonicalises first: `+K-D` and `+Kd` are one tape and one test.
    tapes = {}
    for line in run([STFST, "tape", "--system", SYSTEM], [s for s, _ in cases]):
        f = line.split("\t")
        if len(f) >= 3 and f[1] != "!":
            tapes[f[0]] = f[2]

    seen, checks = {}, []
    for stroke, why in cases:
        canon = tapes.get(stroke)
        if canon is None or canon in seen:
            continue
        seen[canon] = why
        checks.append(canon)

    ours = {}
    for line in run([STFST, "lookup", artifact, "--system", SYSTEM], checks):
        f = line.split("\t")
        ours[f[0]] = None if len(f) < 3 or f[2] == "-" else f[2]

    bad = collections.Counter()
    show = collections.defaultdict(list)
    for stroke in checks:
        try:
            want = ph.lookup((stroke,))
        except (KeyError, IndexError):
            want = None
        got = ours.get(stroke)
        if got == want:
            continue
        # The one deliberate divergence: an adverb launchpad with no modal and no
        # aspect has no auxiliary, and the Python then drops negation, the
        # question and emphasis on the floor. We supply `do` to carry them.
        kind = ("launchpad" if want is not None and got is not None
                and _launchpad(ph, stroke) else "MISMATCH")
        bad[kind] += 1
        show[kind].append((stroke, want, got))

    print("phrasing: %d strokes, %d exhaustive over the clause dimensions"
          % (len(checks), sum(1 for c in checks if seen[c] == "clause")))
    for kind, n in sorted(bad.items()):
        print("  %s: %d, e.g. %s" % (kind, n, show[kind][:3]))
    if bad["MISMATCH"]:
        return 1
    if not bad["launchpad"]:
        print("  the launchpad divergence is gone: docs/phrasing.md is stale", file=sys.stderr)
        return 1
    print("  0 differ, %d deliberate" % bad["launchpad"])

    # The reverse direction has no oracle of its own, and the shared checker wants
    # a golden file, so hand it a sample of what forward lookup just agreed on.
    sample = os.path.join(HERE, "..", "build", "work", "phrasing-sample.tsv")
    with open(sample, "w") as f:
        for stroke in checks[::379]:
            if ours.get(stroke):
                f.write("%s\t%s\n" % (stroke, ours[stroke]))
    subprocess.run([os.path.join(HERE, "check-reverse.py"), artifact, sample],
                   check=True)
    return 0


def _launchpad(ph, stroke):
    """Is this the adverb launchpad the Python leaves without an auxiliary?"""
    p = ph._parse(stroke)
    if p is None or p["right"] not in ph.ADV:
        return False
    return (not (p["A"] or p["O"] or p["E"] or p["U"])
            and (p["neg"] or p["question"] or p["emph"]))


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
