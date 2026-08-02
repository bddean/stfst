#!/usr/bin/env python3
"""Measure how much of Plover's orthography the reference host reproduces.

`crates/stfst-text/src/ortho.rs` is twelve of Plover's thirty-eight rules, so the
coverage is a claim and this is the number behind it. Plover's own `add_suffix` is
the oracle, which means running under Plover's python:

  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/check-ortho.py

The pairs come from `typey-type-full.json`: every word it writes as a single
translation, against the suffixes its own `{^…}` entries use. That weights the
sample the way real writing does rather than the way a rule list does.
"""
import json
import os
import subprocess
import sys

SUFFIXES = ["s", "ed", "ing", "er", "est", "ly", "ion", "able", "y", "en"]
BINARY = "./target/release/stfst"


def words(limit):
    raw = json.load(open(os.path.expanduser("~/.config/plover/typey-type-full.json")))
    seen = []
    for value in raw.values():
        if value.isalpha() and value.islower() and len(value) > 2:
            seen.append(value)
        if len(seen) >= limit:
            break
    return seen


def main(limit=4000):
    import plover.system as system
    from plover.registry import registry
    from plover.orthography import add_suffix

    registry.update()
    system.setup("English Stenotype")

    pairs = [(w, s) for w in words(limit) for s in SUFFIXES]
    theirs = [add_suffix(w, s) for w, s in pairs]

    # Ours, through the record language: the word, then the suffix as `{^…}`.
    lines = []
    for word, suffix in pairs:
        lines.append("!buffer " + word)
        lines.append(suffix)
    ours = subprocess.run(
        [BINARY, "ortho", "--pairs", "/dev/stdin"],
        input="\n".join("%s\t%s" % p for p in pairs) + "\n",
        capture_output=True, text=True)
    if ours.returncode != 0:
        print(ours.stderr.strip(), file=sys.stderr)
        return 1
    mine = ours.stdout.splitlines()

    agree = sum(1 for a, b in zip(mine, theirs) if a == b)
    print("%d pairs, %d agree (%.1f%%)" % (len(pairs), agree, 100 * agree / len(pairs)))
    shown = 0
    for (word, suffix), a, b in zip(pairs, mine, theirs):
        if a != b and shown < 15:
            print("  %s + %s: plover %r, ours %r" % (word, suffix, b, a))
            shown += 1
    return 0


if __name__ == "__main__":
    sys.exit(main(int(sys.argv[1]) if len(sys.argv) > 1 else 4000))
