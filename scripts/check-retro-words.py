#!/usr/bin/env python3
"""Check retro-words against retro-words.py, end to end, over many buffers.

A golden file over strokes is not enough here. What can go wrong is the *rewrite*,
and a rewrite is wrong only for some text: a rule that drops a character leaves
the substitution one out of step for everything after it, which shows up as
nonsense two words later. So this runs every entry the Python has against a set of
buffers chosen to hit the corners, and compares the finished text.

The Python's ops are pure functions of a word window, so they run without Plover.
Its meta loop is repeated here rather than called, because the real one wants a
formatting context.

Two divergences are expected and asserted rather than ignored:

- The Python splits a word on `[^0-9A-Za-z]+`, so it breaks `naive` at `ï`. Class
  symbols know that a letter is a letter, so we do not. Buffers here are ASCII, and
  `docs/retro.md` records the difference.
- A tab and a newline have no source syntax, so separator menu rows 7 and 8 do not
  exist. Their strokes are skipped.

Usage: scripts/check-retro-words.py build/dicts/retro-words.stfst
"""
import contextlib
import json
import importlib.util
import io
import os
import subprocess
import sys

BINARY = "./target/release/stfst"
SYSTEM = "systems/stenotype-extended.toml"

BUFFERS = [
    "the quick brown fox",
    "hello world",
    "one",
    "ALL CAPS here",
    "Already Capped Words",
    "mixedCase getUserName HTTPServer",
    "kebab-case snake_case dotted.name",
    "wrapped (already) here",
    "hello (there world)",
    "trailing punctuation, and more.",
    "x1 y2 z3",
    "a b c d e f g h i j k l m n o p q r s",
    "quoted \"thing\" here",
    "the end",
]


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def cells_of(buffer):
    """The buffer as the Python's cells: a word and the whitespace after it."""
    words = buffer.split(" ")
    return [[w, " " if n + 1 < len(words) else ""] for n, w in enumerate(words)]


def expect(rw, buffer, arg):
    """What retro-words.py's ops make of this buffer, as finished text."""
    cells = cells_of(buffer)
    work = [c[:] for c in cells]
    start = len(cells)
    for part in arg.strip().split("+"):
        result = rw._subop(part)(work)
        if result is None:
            continue
        at, new = result
        work = work[:at] + new
        start = min(start, at)
    if start >= len(cells):
        return buffer
    head = "".join(t + ws for t, ws in cells[:start])
    return head + "".join(t + ws for t, ws in work[start:])


REACH_BUFFER = "one two three"


def reachable(artifact, rw, skip):
    """Which of the 1,024 strokes in the layout have an entry, against the Python.

    Every entry is total, so an entry always answers something, which makes "has an
    entry" a question with an answer. A grammar that invented a stroke shows up here
    and nowhere else.
    """
    import itertools
    strokes, lines = [], []
    for n in range(11):
        for combo in itertools.combinations("EUFRPBLGTS", n):
            outline = "^A" + "".join(combo)
            strokes.append(outline)
            lines.append("!buffer " + REACH_BUFFER)
            lines.append(outline)
    out = subprocess.run(
        [BINARY, "translate", artifact, "--system", SYSTEM, "--strokes", "/dev/stdin"],
        input="\n".join(lines) + "\n", capture_output=True, text=True)
    answers = [l for l in out.stdout.splitlines() if not l.startswith("!")]
    bad = 0
    for outline, line in zip(strokes, answers):
        ours = line.split("\t")[1] != "-"
        arg = rw._ENTRIES.get(outline)
        theirs = arg is not None and not any(
            part.split(".")[0] in ("join", "split") and int(part.split(".")[1]) in skip
            for part in arg.split("+"))
        if ours != theirs:
            bad += 1
            print("FAIL %s: entry=%s, python=%s" % (outline, ours, theirs))
    print("%d strokes, %d wrong" % (len(strokes), bad))
    return bad


def main(artifact, path):
    rw = load(path, "retro_words")
    skip = {i for i, sep in enumerate(rw.SEPARATORS) if not sep.replace(" ", "").isprintable()
            or any(c.isspace() and c != " " for c in sep)}

    cases = []
    lines = []
    for outline, arg in rw._ENTRIES.items():
        if any(part.split(".")[0] in ("join", "split") and int(part.split(".")[1]) in skip
               for part in arg.split("+")):
            continue
        for buffer in BUFFERS:
            cases.append((outline, arg, buffer))
            lines.append("!buffer " + buffer)
            lines.append(outline)

    out = subprocess.run(
        [BINARY, "translate", artifact, "--system", SYSTEM, "--strokes", "/dev/stdin"],
        input="\n".join(lines) + "\n", capture_output=True, text=True)
    if out.returncode != 0:
        print(out.stderr.strip(), file=sys.stderr)
        return 1

    # Every stroke line answers with the text in its last column; the buffer lines
    # answer too, so take them in pairs.
    answers = [l.split("\t")[-1] for l in out.stdout.splitlines()
               if not l.startswith("!") or l.startswith("!buffer")]
    # The harness prints the text with Rust's `{:?}`, which reads as JSON here.
    got = [json.loads(a) for a in answers[1::2]]

    bad = 0
    for (outline, arg, buffer), ours in zip(cases, got):
        theirs = expect(rw, buffer, arg)
        if ours != theirs:
            bad += 1
            if bad <= 10:
                print("FAIL %s (%s)\n  buffer %r\n  python %r\n  ours   %r"
                      % (outline, arg, buffer, theirs, ours))
    print("%d cases, %d failed" % (len(cases), bad))
    return 1 if bad or reachable(artifact, rw, skip) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "build/dicts/retro-words.stfst",
                  sys.argv[2] if len(sys.argv) > 2
                  else os.path.expanduser("~/.config/plover/retro-words.py")))
