#!/usr/bin/env python3
"""Golden cases for emily-modifiers, with `emily-modifiers.py` as the oracle.

Every layer is covered by its whole table rather than by a sample, because each
table is small and a transcription error is exactly what this catches: the 39
keysym rows in all four variants, all 29 fingerspelling chords against all 32
modifier combinations, every one of the 128 payload chords as a number, and all
512 literal numbers.

The skhd branch is out of scope (see the dictionary), so `_skhd_bindings` is
emptied here. Leaving it live would make the expectations depend on the machine's
skhdrc, which is not a property of the dictionary.

Tapes are written directly rather than parsed from Plover notation. A tape is the
keys in steno order, which is what makes that safe, and it keeps the file
readable: `@TRE` is `@`, `T-`, `R-`, `-E`.

Usage: scripts/gen-golden-modifiers.py > tests/golden/emily-modifiers.tsv
"""
import contextlib
import importlib.util
import io
import itertools
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plover_record

LEFT = "STKPWHR"
# The modifier keys, as tape symbols. The right bank is lowercase so that every
# key is one character.
MODS = {"F": "f", "P": "p", "L": "l", "T": "t", "D": "d"}
VOWELS = ["", "A", "O", "AO"]


def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def stroke(cons="", ao="", star=False, eu="", mods="", z=False,
           hashmark=False, caret=False):
    """The Plover notation the Python parses, assembled from its fields."""
    mid = ao + ("*" if star else "") + eu
    right = mods + ("Z" if z else "")
    out = ("#" if hashmark else "") + "@" + ("^" if caret else "") + cons + mid
    if right and not mid:
        out += "-"
    return out + right


def tape(cons="", ao="", star=False, eu="", mods="", z=False,
         hashmark=False, caret=False):
    """The same stroke as a key tape, which is the keys in steno order."""
    return ("#" if hashmark else "") + "@" + ("^" if caret else "") \
        + "".join(k for k in LEFT if k in cons) \
        + "".join(k for k in "AO" if k in ao) + ("*" if star else "") \
        + "".join(k for k in "EU" if k in eu) \
        + "".join(MODS[k] for k in "FPLTD" if k in mods) + ("z" if z else "")


LIT_FLAGS = [("F", "[ATTACH_L]"), ("P", "[ATTACH_R]"), ("L", "[CAP_NEXT]")]


def literal_record(em, fields):
    """The expectation for a literal-mode case, from the glyph table.

    Not from the Python's answer, deliberately. `_literal_symbol` builds it by
    concatenating `{^}` onto the glyph, and a glyph can be `\\` or `{`, so the
    string it returns is ambiguous where Plover reads it back. Its three flag rules
    are the oracle instead; the glyph itself still comes from the table.
    """
    entry = em.glyphs.get(fields.get("cons", ""))
    ao = fields.get("ao", "")
    variant = ("A" in ao) + 2 * ("O" in ao)
    glyph = entry[variant] if isinstance(entry, list) else entry
    if not glyph:
        return None
    extra = [tag for key, tag in LIT_FLAGS if key in fields.get("mods", "")]
    return plover_record.record(glyph, extra)


def main(path):
    em = load(path, "emily_modifiers")
    em._skhd_bindings = lambda: {}

    cases = []

    def add(note=None, **fields):
        cases.append((note, fields))

    add("keysyms: every row, every variant, bare and with modifiers")
    for chord in em.symbols:
        for ao in VOWELS:
            for mods in ("", "F", "FP", "FPLTD"):
                add(cons=chord, ao=ao, eu="E", mods=mods)

    add("literal mode: the glyph itself, and the top row off modifier duty")
    for chord in em.glyphs:
        for ao in VOWELS:
            for mods in ("", "F", "P", "L", "FPL"):
                add(cons=chord, ao=ao, star=True, eu="E", mods=mods)
    add("-Z is the same marker as the star, including both at once")
    for chord in ("HR", "TR", "TH"):
        add(cons=chord, eu="E", z=True)
        add(cons=chord, star=True, eu="E", z=True)

    add("numbers and F-keys: every payload chord")
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            for ao in ("", "O"):
                for hashmark in (False, True):
                    add(cons="".join(combo), ao=ao, eu="U", hashmark=hashmark)
    add("a digit or an F-key with modifiers held")
    for chord in ("R", "RW", "TPR"):
        for mods in ("F", "FP", "FPLTD"):
            add(cons=chord, eu="U", mods=mods)

    add("literal numbers: all 512, the payload as binary")
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            for hashmark in (False, True):
                for caret in (False, True):
                    add(cons="".join(combo), star=True, eu="U",
                        hashmark=hashmark, caret=caret)
    add("modifiers have nothing to modify on a literal number")
    add(cons="R", star=True, eu="U", mods="F")
    add(cons="R", eu="U", z=True)

    add("fingerspelling: every chord against every modifier combination")
    for chord in em.spelling:
        cons = "".join(k for k in LEFT if k in chord)
        ao = "".join(k for k in "AO" if k in chord)
        eu = "".join(k for k in "EU" if k in chord)
        for n in range(6):
            for combo in itertools.combinations("FPLTD", n):
                add(cons=cons, ao=ao, eu=eu, mods="".join(combo))
    add("the literal keys are inert on a letter")
    add(cons="KR", star=True)
    add(cons="KR", z=True)

    add("no payload, no entry: `^` is the 128 bit and nothing else")
    add(caret=True, cons="KR")
    add(caret=True, cons="KR", star=True)
    add()
    add(mods="F")
    add(star=True)
    add(z=True)
    add("`@EU` plus a payload is the reserved command namespace")
    add(cons="R", eu="EU")
    add(ao="A", eu="EU")
    add(cons="AOEU".replace("AO", ""), ao="AO", eu="EU")

    lines = [
        "! emily-modifiers. Expectations come from emily-modifiers.py, so this file",
        "! is an oracle and not a snapshot of our output. The skhd branch is out of",
        "! scope, so its bindings are emptied in the generator.",
        "! A line with no expectation asserts there is no entry.",
        "! Generated by scripts/gen-golden-modifiers.py; edit that, not this.",
    ]
    seen = set()
    skipped = 0
    for note, fields in cases:
        if note:
            lines.append("! " + note)
        key = tape(**fields)
        if key in seen:
            continue
        seen.add(key)
        try:
            answer = em.lookup((stroke(**fields),))
        except (KeyError, IndexError):
            answer = None
        if answer is None:
            lines.append(key)
            continue
        literal = (fields.get("star") or fields.get("z")) and \
            fields.get("eu") == "E" and fields.get("cons")
        try:
            want = (literal_record(em, fields) if literal
                    else plover_record.record(answer))
        except plover_record.Unsupported:
            skipped += 1
            continue
        lines.append(key if want is None else key + "\t" + want)
    print("\n".join(lines))
    if skipped:
        print("! %d cases skipped: no record for the translation" % skipped)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1
         else os.path.expanduser("~/.config/plover/emily-modifiers.py"))
