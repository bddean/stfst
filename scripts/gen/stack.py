"""What the rest of the Stenotype Extended stack writes, in Plover's syntax."""
import collections
import configparser
import copy
import importlib.util
import json
import os
import tomllib

import plover_tape
from plover_stfst import ol
from plover_stfst.record import FOLD, Record, Unsupported, parse_fold

from . import DICTS, PLOVER, RECIPE, ROOT, SYSTEM

_SYSTEM = plover_tape.System.load(SYSTEM)
# A fold names keys by tape symbol, and so does this module.
_KEYS = {k: k for k in _SYSTEM.order}


class Stack:
    """The Stenotype Extended stack in `plover.cfg`, highest first. A compiled
    artifact imported from JSON is read as that JSON, typey-type as `typey`, the
    dictionary being built, and the rest as the artifact. The dictionaries
    named in `skip` are left out."""

    def __init__(self, typey, skip=()):
        cfg = configparser.RawConfigParser()
        cfg.read(os.path.join(PLOVER, "plover.cfg"))
        with open(RECIPE, "rb") as f:
            dicts = tomllib.load(f)["dict"]
        sources = {d["name"]: os.path.join(ROOT, d["json"]) for d in dicts if "json" in d}
        self.priority = {d["name"]: d["priority"] for d in dicts}
        self.layers = []
        for e in json.loads(cfg["System: Stenotype Extended"]["dictionaries"]):
            if not e["enabled"]:
                continue
            path = os.path.join(PLOVER, os.path.expanduser(e["path"]))
            name = os.path.basename(path).rsplit(".", 1)[0]
            path = sources.get(name, path)
            if name in skip:
                continue
            if name == "typey-type":
                self.layers.append((name, typey))
            elif path.endswith(".json"):
                with open(path) as f:
                    self.layers.append((name, json.load(f)))
            elif path.endswith(".stfst") and os.path.exists(path):
                self.layers.append((name, ol.Automaton.open(os.path.join(path, "forward.hfstol"))))

    def with_layer(self, name, entries):
        """This stack with `entries`, {outline: translation}, as the dictionary
        `name`, at its priority in the recipe."""
        out = copy.copy(self)
        at = next((i for i, (n, _) in enumerate(self.layers)
                   if self.priority.get(n, float("inf")) < self.priority[name]), len(self.layers))
        out.layers = self.layers[:at] + [(name, entries)] + self.layers[at:]
        return out

    def ask(self, outlines):
        """{outline: [(dictionary, translation)]}, highest first."""
        outlines = sorted(set(outlines))
        found = collections.defaultdict(list)
        tapes = {o: _SYSTEM.tape(o) for o in outlines}
        for name, layer in self.layers:
            for o in outlines:
                if isinstance(layer, dict):
                    if o in layer:
                        found[o].append((name, layer[o]))
                elif (t := translation(layer, tapes[o])) is not None:
                    found[o].append((name, t))
        return found

    def folds(self, outlines):
        """{outline: (translation, base)} for each of `outlines` a fold would
        write if no entry held it: the first alternative whose base the stack
        writes, and what the stack writes for that base."""
        out = {}
        for o in sorted(set(outlines)):
            tape = _SYSTEM.tape(o)
            for alt in (a for _, layer in self.layers if not isinstance(layer, dict)
                        for a in fold_alternatives(layer, tape)):
                if (base := alt.base(tape)) is None:
                    continue
                base = _SYSTEM.outline("".join(sorted(base, key=_SYSTEM.order.index)))
                if (found := self.ask([base]).get(base)):
                    out[o] = (" ".join(x for x in (alt.before, found[0][1], alt.after) if x), found[0][1])
                    break
        return out

    def unwritable(self, translations):
        """Those of `translations` no outline in the stack writes."""
        by = collections.defaultdict(list)
        for _, layer in self.layers:
            if isinstance(layer, dict):
                for o, t in layer.items():
                    if t in translations:
                        by[t].append(o)
        answers = self.ask(o for os_ in by.values() for o in os_)
        return [t for t in translations if not any(answers[o][0][1] == t for o in by[t])]


def retro_words():
    """Strokes `retro-words.py` claims. Its artifact rewrites text and answers no
    lookup, so the stack cannot say."""
    path = os.path.join(DICTS, "retro-words.py")
    spec = importlib.util.spec_from_file_location("retro_words", path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return set(m._ENTRIES)


def fold_alternatives(fst, tape):
    """The fold alternatives an artifact holds for a one-stroke tape, with keys
    as tape symbols."""
    syms = fst.alphabet.tokenize(tape)
    out = []
    for record in fst.lookup_symbols(syms) if syms is not None else ():
        symbols = [fst.alphabet.symbol(s) for s in record]
        if symbols[:1] == [FOLD]:
            out += parse_fold(symbols, _KEYS)
    return out


def translation(fst, tape):
    """What an artifact writes for a tape, as Plover writes it, or None.

    The first record, as Plover's plugin reads it. One Plover cannot hold is
    given in the record language, since it is still an entry on that outline.
    """
    syms = fst.alphabet.tokenize(tape)
    for record in fst.lookup_symbols(syms) if syms is not None else ():
        symbols = [fst.alphabet.symbol(s) for s in record]
        # A fold answers only where nothing matched, so it is no entry here.
        if symbols[:1] == [FOLD]:
            continue
        try:
            return Record.parse(symbols).translation()
        except Unsupported:
            return "".join(symbols)
    return None
