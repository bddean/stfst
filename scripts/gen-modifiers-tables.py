#!/usr/bin/env python3
"""Transcribe the emily-modifiers tables from `emily-modifiers.py` into xfst.

Four tables, and one of them is not a table in the Python at all:

- **spelling**, chord to letter, 29 rows.
- **keysyms**, chord and variant to an X keysym name, plus the printable
  character each name has. `_KEYSYM_CHARS` is what decides between a keystroke
  and text, so it is data here too.
- **numbers**, chord to a digit or an F-key. This one is a rule in the Python,
  `sum(v for k, v in BITS.items() ...)` with three guards, and it is a table here
  because xfst has no arithmetic. The domain is subsets of seven keys, so the
  whole function is 512 rows and nothing is lost.
- **literal numbers**, the same arithmetic over nine bits, so 512 rows.

Enumerating a rule is usually the thing this format exists to avoid. It is right
here for one reason: these functions are finite. A verb lexicon times a subject
map is not.

Usage: scripts/gen-modifiers-tables.py > dictionaries/emily-modifiers-tables.xfst
"""
import contextlib
import importlib.util
import io
import itertools
import os
import sys

LEFT = "STKPWHR"
VARIANTS = ["[V=0]", "[V=1]", "[V=2]", "[V=3]"]
VOWELS = ["", "A", "O", "AO"]


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def literal(text):
    """A text as xfst symbols.

    `%` is not an escape inside `{...}`, so every non-alphanumeric character is
    its own quoted symbol and alphanumeric runs go in braces.
    """
    if text == "":
        return "0"
    out, run = [], ""
    for c in text:
        if c.isalnum():
            run += c
            continue
        if run:
            out.append("{" + run + "}")
            run = ""
        out.append("%" + c if c in '"\\' else '"' + c + '"')
    if run:
        out.append("{" + run + "}")
    return "[ " + " ".join(out) + " ]" if len(out) > 1 else out[0]


def keys(chord, order=LEFT):
    """A key set as its tape symbols, in steno order."""
    return " ".join('"%s"' % k for k in order if k in chord)


def relation(name, rows, comment):
    if not rows:
        return "! %s is empty.\ndefine %s [ 0 - 0 ] ;\n\n" % (name, name)
    body = " |\n    ".join("[ [ %s ] .x. [ %s ] ]" % (sel, out) for sel, out in rows)
    return "%sdefine %s [\n    %s ] ;\n\n" % (comment, name, body)


def char_of(em, keysym):
    """The printable character for a keysym, or None.

    A keysym with no text form has to stay a keystroke, which is the one thing
    that decides between the two record shapes.
    """
    return keysym if len(keysym) == 1 else em._KEYSYM_CHARS.get(keysym)


def keysym_rows(em):
    """chord and variant to a keysym name, and to its character where it has one."""
    names, chars = [], []
    for chord, entry in em.symbols.items():
        for v, name in enumerate(entry if isinstance(entry, list) else [entry] * 4):
            if not name:
                continue                      # an empty cell is the Python's KeyError
            sel = '%s "%s"' % (keys(chord), VARIANTS[v])
            names.append((sel, literal(name)))
            char = char_of(em, name)
            if char is not None:
                chars.append((sel, literal(char)))
    return names, chars


def number_rows(em):
    """chord, `#` and `O` to a digit or an F-key name.

    The Python's three guards, transcribed by running them: the low nibble is the
    digit, `T` with `P` makes it an F-key instead and `#` adds 16 to that, and `O`
    with no bits at all is the explicit zero. Whatever the guards reject has no
    row, which is the absence of a path.
    """
    names, chars = [], []
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            chord = "".join(combo)
            bits = sum(v for k, v in em.BITS.items() if k in chord and v <= 8)
            for hashmark in (False, True):
                for vowel in VOWELS:
                    name = None
                    if "T" in chord and "P" in chord:
                        f = bits + (16 if hashmark else 0)
                        if 1 <= f <= 24:
                            name = "F%d" % f
                    elif bits and bits <= 9:
                        name = str(bits)
                    elif not bits and "O" in vowel:
                        name = "0"
                    if name is None:
                        continue
                    sel = "%s%s %s" % ('"#" ' if hashmark else "",
                                       keys(chord), keys(vowel, "AO"))
                    names.append((sel.strip(), literal(name)))
                    if len(name) == 1:
                        chars.append((sel.strip(), literal(name)))
    return names, chars


def literal_number_rows(em):
    """chord, `^` and `#` to a decimal number, which is all 512 of them."""
    rows = []
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            chord = "".join(combo)
            base = sum(v for k, v in em.BITS.items() if k in chord)
            for hashmark in (False, True):
                for caret in (False, True):
                    value = base + (128 if caret else 0) + (256 if hashmark else 0)
                    sel = "%s%s%s" % ('"#" ' if hashmark else "",
                                      '"^" ' if caret else "", keys(chord))
                    rows.append((sel.strip() or "0", literal(str(value))))
    return rows


def spelling_rows(em):
    """A fingerspelling chord to its letter.

    The star is a literal-mode marker everywhere else, and the letters ignore it,
    so it is optional inside the row: it sits between the two vowel banks, which
    is where steno order puts it.
    """
    rows = []
    for chord, letter in em.spelling.items():
        left = [k for k in LEFT + "AO" if k in chord]
        right = [k for k in "EU" if k in chord]
        # `@EU` plus a payload is the reserved command namespace, and the Python
        # raises there before it reaches the letters, so those chords have no row.
        if right == ["E", "U"] and left:
            continue
        sel = " ".join(['"%s"' % k for k in left] + ['("*")']
                       + ['"%s"' % k for k in right])
        rows.append((sel, literal(letter)))
    return rows


def main(path):
    em = load(path, "emily_modifiers")
    print("\n".join([
        "! Generated by scripts/gen-modifiers-tables.py from emily-modifiers.py.",
        "! Edit that, not this. The rules that read these tables are in",
        "! emily-modifiers.xfst.",
        "",
    ]))
    names, chars = keysym_rows(em)
    print(relation("SymName", names,
                   "! A keysym name, per chord and variant.\n"), end="")
    print(relation("SymChar", chars,
                   "! The same, where the keysym has a printable character.\n"), end="")
    names, chars = number_rows(em)
    print(relation("NumName", names,
                   "! A digit or an F-key, per chord.\n"), end="")
    print(relation("NumChar", chars,
                   "! The same, where it is a single character, which is the digits.\n"),
          end="")
    print(relation("Spell", spelling_rows(em),
                   "! Fingerspelling.\n"), end="")
    print(relation("LitNum", literal_number_rows(em),
                   "! Every nine-bit literal number. xfst has no arithmetic, and this\n"
                   "! function is finite, so the whole of it is written out.\n"), end="")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1
         else os.path.expanduser("~/.config/plover/emily-modifiers.py"))
