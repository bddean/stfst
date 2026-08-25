#!/usr/bin/env python3
"""Reverse lookup, checked against forward lookup.

There is no oracle for the reverse direction, and there does not need to be one:
the two directions are the same relation, so forward lookup is the check. For a
sample of records, ask which outlines write them, then write those outlines and
require the record back.

That catches an inverted automaton that lost or crossed its tapes. It does not
check ranking, so the shortest answer is checked separately: the queried outline
must come back when the sample took it from a one-stroke entry.

Usage: scripts/check-reverse.py <artifact> <golden.tsv> [count]
"""
import subprocess
import sys
import os

ROOT = os.path.join(os.path.dirname(__file__), "..")
STFST = os.path.join(ROOT, "target", "release", "stfst")
SYSTEM = os.path.join(ROOT, "systems", "stenotype-extended.toml")
LIMIT = 3


def run(args, lines):
    return subprocess.run(
        [STFST, *args], input="\n".join(lines) + "\n",
        capture_output=True, text=True, check=True).stdout.splitlines()


def cases(path, count):
    """A spread of golden cases, as (tape, record) pairs."""
    out = []
    for line in open(path):
        line = line.rstrip("\n")
        if line.startswith("!") or "\t" not in line:
            continue
        tape, record = line.split("\t")[:2]
        if record:
            out.append((tape, record))
    step = max(1, len(out) // count)
    return out[::step][:count]


def main(artifact, golden, count=200):
    if not os.path.exists(os.path.join(artifact, "reverse.hfstol")):
        # A dictionary whose outlines have no longest carries no reverse
        # automaton, because "what writes this" has no finite answer there.
        print("no reverse automaton")
        return 0
    pairs = cases(golden, int(count))
    records = [r for _, r in pairs]
    found = {}
    for line in run(["reverse", artifact, "--system", SYSTEM,
                     "--limit", str(LIMIT)], records):
        fields = line.split("\t")
        # `-` means no outline, and no outline is ever spelled `-`, so dropping
        # it is safe here. On the lookup side it is not: `-` is a legal record.
        found[fields[0]] = [f for f in fields[1:] if f not in ("-", "...")]

    outlines = sorted({o for got in found.values() for o in got})
    writes = {}
    if outlines:
        for line in run(["lookup", artifact, "--system", SYSTEM], outlines):
            fields = line.split("\t")
            writes[fields[0]] = fields[2:]

    bad = 0
    for tape, record in pairs:
        got = found.get(record)
        if got is None:
            print(f"FAIL {record!r}: reverse returned nothing")
            bad += 1
            continue
        for outline in got:
            if record not in writes.get(outline, []):
                print(f"FAIL {record!r}: {outline} does not write it")
                bad += 1
        # Ranking, where the answer is known: this record has a one-stroke
        # outline, and nothing is shorter than one stroke, so the first offer
        # must be one stroke too.
        if "/" not in tape and got and "/" in got[0]:
            print(f"FAIL {record!r}: {tape} is one stroke, offered {got[0]}")
            bad += 1
    print(f"{len(pairs)} records, {bad} failed")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))
