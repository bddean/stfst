#!/usr/bin/env python3
"""Measure how much of Plover's orthography the artifacts reproduce.

`scripts/check-ortho-rules.py` checks the rules themselves, candidate for
candidate. This checks the whole join: the rules, the word list that ranks their
candidates, and the host's fifteen lines of choosing between them. Plover's own
`add_suffix` is the oracle, which means running under Plover's python:

  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/check-ortho.py

The pairs come from `typey-type-full.json`: every word it writes as a single
translation, against the suffixes its own `{^…}` entries use. That weights the
sample the way real writing does rather than the way a rule list does.

It also holds the plugin's half of what is ours on both sides: the completion of
a join that makes no word, and the words a ladder climbs to.

Lowercase words only, and that is the case that happens rather than a convenient
one: a formatter capitalises after it joins, so a word reaching a join is lowercase
unless a dictionary already wrote a capital. `docs/parity.md` has the number for
capitalised words and names every difference behind it.
"""
import json
import os
import subprocess
import sys

SUFFIXES = ["s", "ed", "ing", "er", "est", "ly", "ion", "able", "y", "en"]
# The ladder `-L` climbs in `user.json`.
LADDER = "ly:le,el,al,il,ol,ul/ly/able,ible/ably,ibly"
BINARY = "./target/release/stfst"


def words(limit):
    raw = json.load(open(os.path.join(os.path.dirname(__file__), "..", "dictionaries", "typey-type-full.json")))
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

    # The completion is ours on both sides, so it has to agree exactly wherever
    # the join it starts from does.
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "plover-plugin"))
    from plover_stfst.morph import join_or_complete
    ours = subprocess.run(
        [BINARY, "ortho", "--complete", "--pairs", "/dev/stdin"],
        input="\n".join("%s\t%s" % p for p in pairs) + "\n",
        capture_output=True, text=True)
    if ours.returncode != 0:
        print(ours.stderr.strip(), file=sys.stderr)
        return 1
    joined = [(p, a, join_or_complete(*p)) for p, a, b, c in
              zip(pairs, ours.stdout.splitlines(), theirs, mine) if b == c]
    completed = sum(1 for (w, s), a, b in joined if b != add_suffix(w, s))
    bad = [(p, a, b) for p, a, b in joined if a != b]
    print("%d completed, %d differ from the plugin" % (completed, len(bad)))
    for (word, suffix), a, b in bad[:15]:
        print("  %s + %s: plugin %r, ours %r" % (word, suffix, b, a))

    # A ladder is the same two halves again, over every word of the sample.
    from plover_stfst.morph import climb, parse_ladder
    sample = words(limit)
    ours = subprocess.run(
        [BINARY, "ladder", "--ladder", LADDER, "--words", "/dev/stdin"],
        input="\n".join(sample) + "\n", capture_output=True, text=True)
    if ours.returncode != 0:
        print(ours.stderr.strip(), file=sys.stderr)
        return 1
    ladder = parse_ladder(LADDER)
    climbed = [(w, a.split("\t"), climb(w, ladder))
               for w, a in zip(sample, ours.stdout.splitlines())]
    worse = [c for c in climbed if c[1] != c[2]]
    print("%d ladders, %d differ from the plugin" % (len(climbed), len(worse)))
    for word, a, b in worse[:15]:
        print("  %s: plugin %r, ours %r" % (word, b, a))
    return 1 if bad or worse else 0


if __name__ == "__main__":
    sys.exit(main(int(sys.argv[1]) if len(sys.argv) > 1 else 4000))
