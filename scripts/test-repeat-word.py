"""End-to-end tests for repeat-word.py + repeat-idioms.json, driving the real
Plover translator/formatter. Run with Plover's bundled python:

  /Applications/Plover.app/.../python3.13 scripts/test-repeat-word.py

Covers the generic X-conn-X reduplication across every stem family and a sample
of idiom overrides pulled straight from the built dictionary.
"""

import importlib.util
import json
import os

import plover.system as system
from plover.registry import registry

DICTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dictionaries")

registry.update()
system.setup("Stenotype Extended")

from plover.steno import Stroke, normalize_steno
from plover.steno_dictionary import StenoDictionary, StenoDictionaryCollection
from plover.translation import Translator
from plover.formatting import Formatter

_spec = importlib.util.spec_from_file_location("repeat_word", os.path.join(DICTS, "repeat-word.py"))
rw = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(rw)
IDIOMS = json.load(open(os.path.join(DICTS, "repeat-idioms.json")))

# base words for the generic reduplication cases (their outlines are arbitrary)
WORDS = {"PHOR": "more", "PWABG": "back", "TPAEUS": "face", "TKAEU": "day",
         "SAOD": "side", "WOPB": "one", "AOER": "year"}


class PyDict(StenoDictionary):
    def __init__(self):
        super().__init__()
        self._longest_key = 1
        self.readonly = True

    def __getitem__(self, key):
        return rw.lookup(key)

    def get(self, key, default=None):
        try:
            return rw.lookup(key)
        except KeyError:
            return default

    def reverse_lookup(self, text):
        return set(rw.reverse_lookup(text))


def _engine():
    base = StenoDictionary()
    base._longest_key = 1
    for stroke, word in WORDS.items():
        base[(stroke,)] = word
    idioms = StenoDictionary()
    idioms._longest_key = 2
    for outline, phrase in IDIOMS.items():
        idioms[tuple(outline.split("/"))] = phrase
    coll = StenoDictionaryCollection([idioms, PyDict(), base])
    out = []
    output = type("O", (), {
        "send_backspaces": lambda s, n: out.append(("bs", n)),
        "send_string": lambda s, t: out.append(("str", t)),
        "send_key_combination": lambda s, c: None,
        "send_engine_command": lambda s, c: None})()
    fmt = Formatter()
    fmt.set_output(output)
    tr = Translator()
    tr.set_dictionary(coll)
    tr.add_listener(fmt.format)
    return tr, out


def _render(out):
    buf = ""
    for kind, val in out:
        if kind == "str":
            buf += val
        elif kind == "bs":
            buf = buf[:-val] if val <= len(buf) else ""
    return buf


def run(strokes):
    tr, out = _engine()
    for s in strokes.split("/"):
        tr.translate(Stroke(normalize_steno(s)[0]))
    return _render(out).strip()


# generic reduplication across every stem family, incl. in/on/over
GENERIC = [
    ("PHOR/^SKP", "more and more"), ("PWABG/^O", "back to back"),
    ("PWABG/^TO", "back to back"), ("SAOD/^PWEU", "side by side"),
    ("TPAEUS/^-F", "face of face"), ("TPAEUS/^*F", "face of face"),
    ("SAOD/^-T", "side the side"), ("SAOD/^TP-R", "side for side"),
    ("SAOD/^TPOR", "side for side"), ("WOPB/^OB", "one on one"),
    ("WOPB/^OPB", "one on one"), ("TKAEU/^TPH", "day in day"),
    ("TKAEU/^EUPB", "day in day"), ("AOER/^OEFR", "year over year"),
]
# idiom overrides straight from the built dictionary
IDIOM_PHRASES = ["men and women", "black and white", "two or three",
                 "point of view", "break the ice", "keep in mind",
                 "father in law", "eye for an eye", "in and out",
                 "this or that", "dead and gone", "kick the bucket",
                 "head over heels", "wolf in sheep's clothing",
                 "rags to riches", "cards on the table", "up for grabs"]


def main():
    fails = 0
    for strokes, want in GENERIC:
        got = run(strokes)
        ok = got == want
        fails += not ok
        print(("ok  " if ok else "FAIL"),
              f"{strokes:14} -> {got!r}" + ("" if ok else f"  (want {want!r})"))
    for phrase in IDIOM_PHRASES:
        outline = next((k for k, v in IDIOMS.items() if v == phrase), None)
        if outline is None:
            print("FAIL", f"(no entry for {phrase!r})")
            fails += 1
            continue
        got = run(outline)
        ok = got == phrase
        fails += not ok
        print(("ok  " if ok else "FAIL"),
              f"{outline:16} -> {got!r}" + ("" if ok else f"  (want {phrase!r})"))
    print(f"\n{fails} failures")
    return fails


if __name__ == "__main__":
    raise SystemExit(main())
