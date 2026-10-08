#!/usr/bin/env python3
"""Transcribe retro-words.py's menus into xfst, as two files.

Two, because of what depends on what. The characters the rules must be able to
see come from the menus, so they have to be defined before the rules; the
dispatch calls the rules, so it has to come after them. The recipe lists the
three files in that order.

    retro-words-chars.xfst    the characters the menus need on the tape
    retro-words.xfst          the rules, hand written
    retro-words-menus.xfst    the dispatch, and `Grammar`

The dispatch is enumerated rather than factored: one row per stroke the layout
reaches, 114 of them, which is the Python's entry set. Each row consumes its keys
and names a rule, so the enumeration is the *entry list* and the rules are still
one each.

Two separator menu entries are dropped. A tab and a newline have no source
syntax: `lexc` reads `\\n` as two characters and a literal one crashes
`hfst-xfst`. So their rows are absent and their strokes have no entry, which is
the limit FST-DICT-DESIGN.md §9 already records, met a second time.

Usage: scripts/gen-retro-tables.py [retro-words.py] [dictionaries/]
"""
import contextlib
import importlib.util
import io
import itertools
import os
import sys

# The menu keys and their bit values, in steno order.
BITS = [("l", 1), ("g", 2), ("t", 4), ("s", 8)]
# The case dial: the scope key, the mode key, and the direction keys, in steno
# order. Each row is (keys, transform, mode) with the keys already in order.
SCOPE = [("f", "All"), ("", "First")]
MODE = [("r", "Last"), ("", "Find")]
DIR = [("p", "Up"), ("b", "Down")]


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def glyph(c):
    """A character as the tape symbol the host sends for it.

    Every character escaped with `%`, which is the one spelling that works for
    all of them: a quote cannot be written inside `"..."`, and a bracket cannot
    be written inside `{...}`.
    """
    return "".join("%" + x for x in "[=" + c + "]")


def literal(text):
    """A text as xfst output symbols. Empty text is epsilon."""
    if text == "":
        return "0"
    parts = ["%" + c if c in '"\\' else '"' + c + '"' for c in text]
    return "[ %s ]" % " ".join(parts) if len(parts) > 1 else parts[0]


def keys(letters):
    """Keys consumed, in steno order, emitting nothing."""
    if not letters:
        return ""
    return " ".join('Kill("%s")' % k for k in letters) + " "


def menu(index):
    """The menu keys that select an index."""
    for n in range(len(BITS) + 1):
        for combo in itertools.combinations(BITS, n):
            if sum(v for _, v in combo) == index:
                return "".join(k for k, _ in combo)
    return None


def printable(text):
    return all(not c.isspace() or c == " " for c in text)


def case_rows():
    """The case dial alone: `^A` plus the dial keys."""
    rows = []
    for scope_key, scope in SCOPE:
        for mode_key, mode in MODE:
            for dir_key, direction in DIR:
                rule = "%s(%s%s)" % (mode, direction, scope)
                rows.append((scope_key + mode_key + dir_key, rule))
    return rows


def verb_rows(rw):
    """Every verb row: join with and without the dial, split, and wrap."""
    join, split, wrap = [], [], []
    for index, sep in enumerate(rw.SEPARATORS):
        selector = menu(index)
        if selector is None:
            continue
        if not printable(sep):
            print("! separator %d (%r) holds a control character, so it has no row"
                  % (index, sep), file=sys.stderr)
            continue
        join.append((selector, "Join(%s)" % literal(sep)))
        for dial, rule in case_rows():
            join.append((dial + selector,
                         "Then(%s, Join(%s))" % (rule, literal(sep))))
        if index == 0:
            split.append((selector, "SplitShape"))
        else:
            pattern = "[ %s ]" % " ".join(glyph(c) for c in sep)
            split.append((selector, "Split(%s)" % pattern))
    for index, (open_, close) in enumerate(rw.PAIRS):
        selector = menu(index)
        if selector is None or not (printable(open_) and printable(close)):
            continue
        args = "%s, %s, %s, %s" % (glyph(open_), literal(open_),
                                   glyph(close), literal(close))
        wrap.append((selector, "WrapFind(%s)" % args))
        wrap.append(("r" + selector, "Wrap(%s, %s)" % (literal(open_), literal(close))))
    return join, split, wrap


def block(name, prefix, rows, comment):
    body = " |\n    ".join("[ %s%sTotal(%s) ]" % (prefix, keys(k), rule)
                           for k, rule in rows)
    return "%sdefine %s [\n    %s ] ;\n\n" % (comment, name, body)


def main(path, out_dir):
    rw = load(path, "retro_words")
    chars = sorted({c for sep in rw.SEPARATORS for c in sep if printable(sep)}
                   | {c for pair in rw.PAIRS for text in pair for c in text})

    with open(os.path.join(out_dir, "retro-words-chars.xfst"), "w") as f:
        f.write("\n".join([
            "! Generated by scripts/gen-retro-tables.py from retro-words.py.",
            "! Edit that, not this.",
            "!",
            "! The characters the menus need to be visible on the tape. Everything",
            "! else in a window reaches the rules as its class, which is what keeps",
            "! the alphabet small and the casing in the host.",
            "",
            "define SigChar [ %s ] ;" % " | ".join(glyph(c) for c in chars),
            "",
        ]))

    join, split, wrap = verb_rows(rw)
    with open(os.path.join(out_dir, "retro-words-menus.xfst"), "w") as f:
        f.write("\n".join([
            "! Generated by scripts/gen-retro-tables.py from retro-words.py.",
            "! Edit that, not this. The rules these rows name are in retro-words.xfst.",
            "!",
            "! One row per stroke the layout reaches. Each row consumes its keys and",
            "! names one rule, so the table is the entry set and not a rule set.",
            "",
        ]))
        f.write(block("CaseOp", "", case_rows(),
                      "! The case dial on its own.\n"))
        f.write(block("JoinOp", 'Kill("E") ', join,
                      "! Join, with the dial composed in front of it where the dial keys\n"
                      "! are held: case, then close the gap, which is what makes `^AEP`\n"
                      "! plus a menu bit camelCase.\n"))
        f.write(block("SplitOp", 'Kill("U") ', split,
                      "! Split. Index 0 breaks on word shape; the rest break on their\n"
                      "! own separator.\n"))
        f.write(block("WrapOp", 'Kill("E") Kill("U") ', wrap,
                      "! Wrap, and widen an existing wrap. `-R` takes the literal last\n"
                      "! word instead.\n"))
        f.write("! `^A` opens the namespace and the four verbs partition the rest.\n")
        f.write("define Grammar Canon([ Kill(\"^\") Kill(A) "
                "[ CaseOp | JoinOp | SplitOp | WrapOp ] ]) ;\n")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1
         else os.path.join(os.path.dirname(__file__), "..", "dictionaries", "retro-words.py"),
         sys.argv[2] if len(sys.argv) > 2 else "dictionaries")
