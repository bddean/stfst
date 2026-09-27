"""What the rest of the Stenotype Extended stack writes, in Plover's syntax."""
import collections
import configparser
import json
import os
import tomllib

import plover_tape
from plover_stfst import ol
from plover_stfst.record import Record, Unsupported

from . import PLOVER, RECIPE, SYSTEM

_SYSTEM = plover_tape.System.load(SYSTEM)


class Stack:
    """The Stenotype Extended stack in `plover.cfg`, highest first. A compiled
    artifact imported from JSON is read as that JSON, typey-type as `typey`, the
    dictionary being built, and the rest as the artifact."""

    def __init__(self, typey, skip=None):
        cfg = configparser.RawConfigParser()
        cfg.read(os.path.join(PLOVER, "plover.cfg"))
        with open(RECIPE, "rb") as f:
            sources = {d["name"]: os.path.expanduser(d["json"])
                       for d in tomllib.load(f)["dict"] if "json" in d}
        self.layers = []
        for e in json.loads(cfg["System: Stenotype Extended"]["dictionaries"]):
            if not e["enabled"]:
                continue
            path = os.path.join(PLOVER, os.path.expanduser(e["path"]))
            name = os.path.basename(path).rsplit(".", 1)[0]
            path = sources.get(name, path)
            if name == skip:
                continue
            if name == "typey-type":
                self.layers.append((name, typey))
            elif path.endswith(".json"):
                with open(path) as f:
                    self.layers.append((name, json.load(f)))
            elif path.endswith(".stfst") and os.path.exists(path):
                self.layers.append((name, ol.Automaton.open(os.path.join(path, "forward.hfstol"))))

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


def translation(fst, tape):
    """What an artifact writes for a tape, as Plover writes it, or None.

    The first record, as Plover's plugin reads it. One Plover cannot hold is
    given in the record language, since it is still an entry on that outline.
    """
    syms = fst.alphabet.tokenize(tape)
    for record in fst.lookup_symbols(syms) if syms is not None else ():
        symbols = [fst.alphabet.symbol(s) for s in record]
        try:
            return Record.parse(symbols).translation()
        except Unsupported:
            return "".join(symbols)
    return None
