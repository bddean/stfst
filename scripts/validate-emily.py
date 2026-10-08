#!/usr/bin/env python3
"""Check emily-modifiers / emily-symbols for stroke collisions with the stack.

Runs every stroke of every enabled dictionary through both emily lookups.
Any stroke both sides claim is a collision: whichever dictionary sits higher
in the stack shadows the other. Python dicts are covered too: wm.py by its
BINDINGS keys; the phrasing engine needs '+' and Shrimple a fixed entry stroke,
neither of which an emily stroke can contain.

Usage: PLPY -m scripts.validate-emily  (or plain python3; no plover import)
"""
import importlib.util
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "dictionaries"

JSON_DICTS = [
    "user.json",
    "phrases.json",
    "commands.json",
    "semantic-suffixes.json",
    "semantic-retro.json",
    "typey-type-full.json",
]


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def wm_strokes():
    wm = load_module("wm", ROOT / "wm.py")
    return set(wm.BINDINGS)


# known overlaps, deliberately won by emily-symbols (it sits atop the stack):
# ' + O-variant digit-converts onto typey's 40U ({:}40)
ACCEPTED = {
    ("40U", "typey-type-full.json"),
}


def main():
    modifiers = load_module("emily_modifiers", ROOT / "emily-modifiers.py")
    symbols = load_module("emily_symbols", ROOT / "emily-symbols.py")

    sources = {name: json.loads((ROOT / name).read_text()) for name in JSON_DICTS}
    sources["wm.py"] = {s: "<wm command>" for s in wm_strokes()}

    collisions = 0
    for name, entries in sources.items():
        for outline, translation in entries.items():
            strokes = outline.split("/")
            for stroke in strokes:
                for dictname, mod in (("modifiers", modifiers), ("symbols", symbols)):
                    try:
                        out = mod.lookup((stroke,))
                    except (KeyError, AssertionError):
                        continue
                    if (stroke, name) in ACCEPTED:
                        continue
                    collisions += 1
                    kind = "WHOLE OUTLINE" if len(strokes) == 1 else "part of outline"
                    print(
                        f"emily-{dictname} claims {stroke!r} -> {out!r}; "
                        f"{name} has {outline!r} -> {translation!r} ({kind})"
                    )
    if collisions:
        print(f"\n{collisions} collision(s)")
        return 1
    print("no collisions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
