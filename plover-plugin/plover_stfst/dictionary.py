"""The `plover.dictionary` entry point: an artifact directory Plover can load.

Plover asks a dictionary two questions: what does this outline write, and what
writes this text. An artifact answers both, so the plugin is a translation of
vocabulary and not of machinery: strokes become a key tape, a record becomes a
Plover translation, and the reverse automaton answers the second question that
`phrasing.py` currently answers with ten nested loops.

What Plover cannot host is refused at load rather than mistranslated. A
dictionary that reads a context slot, carries a state, splices a bound word or
rewrites a window needs the host to say something on every stroke that Plover's
dictionary API has no way to say. The manifest declares each of those, so the
refusal is one comparison and it happens before a stroke arrives.
"""

import os
import tomllib

import plover.system
from plover import log
from plover.registry import registry
from plover.steno import Stroke
from plover.steno_dictionary import StenoDictionary

from .ol import Automaton
from .record import PLUGIN_COMMANDS, Record, Unsupported

# The host contract this plugin implements: `FST-DICT-DESIGN.md` §5, items 1 to
# 4. Items 5 and 6 need a Plover API that does not exist, so a dictionary that
# uses them is unavailable here rather than broken.
HOST_VERSION = 1

# What Plover writes for itself. Every one is a built-in meta, so no dictionary
# needs a plugin to get it; `infl` is the exception and is checked for by name.
BUILTIN_METAS = frozenset(["retro.cap", "retro.upper", "retro.lower",
                           "retro.currency"])

# How many outlines a reverse lookup returns, shortest first. A generative
# dictionary writes one word with unboundedly many outlines, so "every outline"
# is not a finite answer and the suggestions window wants the few shortest.
REVERSE_LIMIT = 8


class StfstDictionary(StenoDictionary):
    readonly = True

    def __init__(self):
        super().__init__()
        self._fst = None
        self._back = None
        self._keys = {}          # Plover key name -> tape symbol number
        self._names = {}         # tape symbol -> Plover key name
        self._separator = None      # the tape symbol between strokes
        self._separator_sym = None  # and its number in the forward alphabet
        self._tape = {}          # steno -> tape symbol numbers, memoised
        self._weight = []

    def _load(self, filename):
        manifest = _manifest(filename)
        name = manifest["dict"]["name"]
        needs = manifest["requires"]
        if needs["host"] > HOST_VERSION:
            raise ValueError(
                "%s needs host %d, and Plover is host %d: it reads %s, which "
                "Plover has no way to tell a dictionary"
                % (name, needs["host"], HOST_VERSION, _why(needs)))
        missing = sorted(set(needs["metas"]) - _metas())
        if missing:
            raise ValueError("%s asks for the meta %s, which this Plover does "
                             "not have" % (name, ", ".join(missing)))
        declined = sorted(set(needs["commands"]) - _commands())
        if declined:
            # A command is a request, so a host that cannot honour one declines
            # it and the dictionary still loads.
            log.info("%s: this Plover will decline %s", name, ", ".join(declined))

        # A cursor discovers the longest match, so no dictionary here declares a
        # longest key. Plover asks about a whole outline, so it needs one before
        # the first stroke, and the compiler reads it off the automaton.
        strokes = manifest["input"].get("strokes")
        if strokes is None:
            raise ValueError("%s has no longest outline, so Plover cannot bound "
                             "its lookups: give it `strokes` in the recipe" % name)
        self._longest_key = strokes
        unknown = [k for k in needs["keys"] if k not in plover.system.KEYS]
        if unknown:
            raise ValueError("%s is for another steno system: this one has no "
                             "key %s" % (name, ", ".join(unknown)))

        # The keys and the tape symbols are the same list in steno order, which is
        # what makes a stroke a tape and a tape an outline.
        order = manifest["input"]["order"]
        if len(order) != len(needs["keys"]):
            raise ValueError("%s names %d keys and %d tape symbols"
                             % (name, len(needs["keys"]), len(order)))
        self._fst = Automaton.open(os.path.join(filename, "forward.hfstol"))
        alphabet = self._fst.alphabet
        self._keys = dict(zip(needs["keys"], (alphabet.number(s) for s in order)))
        self._names = dict(zip(order, needs["keys"]))
        self._separator = manifest["input"]["separator"]
        self._separator_sym = alphabet.number(self._separator)

        back = os.path.join(filename, "reverse.hfstol")
        if os.path.exists(back):
            self._back = Automaton.open(back)
            # One key weighs one and the separator weighs more than a stroke can
            # hold, so a cheaper tape is one with fewer strokes, then fewer keys.
            self._weight = [1] * len(self._back.alphabet.symbols)
            sep = self._back.alphabet.number(self._separator)
            if sep is not None:
                self._weight[sep] = len(needs["keys"]) + 1

    # ---- what an outline writes

    def __getitem__(self, key):
        value = self.get(key)
        if value is None:
            raise KeyError(repr(key))
        return value

    def get(self, key, fallback=None):
        record = self._record(key)
        if record is None:
            return fallback
        try:
            return record.translation()
        except Unsupported as e:
            # A record Plover cannot spell is reported and then treated as no
            # entry, because writing an approximation of it would be worse.
            log.info("%s: %s writes %s, which Plover cannot: %s",
                     self.path, "/".join(key), record, e)
            return fallback

    def _record(self, key):
        """The first record this artifact holds for an outline, or None.

        More than one means the dictionary is ambiguous for this outline. Plover
        asks for a single translation, so the first is the answer and the order is
        the automaton's.
        """
        cursor = self._fst.cursor()
        for n, steno in enumerate(key):
            if n:
                if self._separator_sym is None:
                    return None
                cursor.step(self._separator_sym)
            syms = self._symbols(steno)
            if syms is None:
                return None
            for sym in syms:
                cursor.step(sym)
            if not cursor.live():
                return None
        alphabet = self._fst.alphabet
        for output in cursor.outputs():
            try:
                return Record.parse([alphabet.symbol(s) for s in output])
            except Unsupported as e:
                log.error("%s: %s does not parse: %s", self.path, "/".join(key), e)
        return None

    def _symbols(self, steno):
        """One written stroke as tape symbol numbers, in steno order.

        Plover's own parser decides which keys a stroke holds, so the number bar
        and the hyphen convention are its business and not this plugin's.
        """
        if steno not in self._tape:
            try:
                keys = Stroke.from_steno(steno).keys()
            except ValueError:
                keys = ()
            # A key this dictionary's alphabet never uses means no entry of its
            # can hold this stroke.
            got = [self._keys.get(k) for k in keys]
            self._tape[steno] = None if not got or None in got else got
        return self._tape[steno]

    # ---- what writes some text

    def reverse_lookup(self, value):
        if self._back is None:
            return []
        try:
            record = Record.from_translation(value)
        except Unsupported:
            # A translation this format has no meaning for is written by no
            # outline here, which is an answer rather than an error.
            return []
        alphabet = self._back.alphabet
        input_syms = alphabet.numbers(record.symbols())
        if input_syms is None:
            return []
        tapes, _ = self._back.cheapest(input_syms, self._weight, REVERSE_LIMIT,
                                       REVERSE_LIMIT * 4096)
        out = []
        for tape in tapes:
            outline = self._outline(alphabet.render(tape))
            if outline is not None:
                out.append(outline)
        return out

    def _outline(self, tape):
        """A key tape as Plover's stroke tuple."""
        strokes = []
        for chunk in tape.split(self._separator):
            keys = [self._names.get(symbol) for symbol in chunk]
            if not keys or None in keys:
                return None
            strokes.append(Stroke(keys).rtfcre)
        return tuple(strokes)


def _manifest(path):
    with open(os.path.join(path, "manifest.toml"), "rb") as f:
        return tomllib.load(f)


def _why(needs):
    """Which part of the host contract this dictionary needs, by name."""
    wants = []
    if needs["context"]:
        wants.append("the context slot " + ", ".join(needs["context"]))
    if needs["state"]:
        wants.append("a carried state")
    if needs["registers"]:
        wants.append("%d bound words" % needs["registers"])
    if needs["words"]:
        wants.append("%d words of what is already written" % needs["words"])
    return " and ".join(wants)


def _metas():
    """The metas this Plover can do, which is the built-ins plus what is
    installed."""
    have = set(BUILTIN_METAS)
    names = {p.name for p in registry.list_plugins("meta")}
    if "infl" in names:
        have.add("infl")
    return have


def _commands():
    """The commands this Plover can honour.

    Advisory only: a command is a request, so a dictionary that asks for one
    Plover does not have still loads, and the name is what this decides.
    """
    have = {"mode." + m for m in ("caps", "title", "lower", "camel", "snake",
                                  "reset_case", "set_space", "reset_space", "reset")}
    have |= {"engine.undo", "engine.repeat_stroke", "engine.toggle_star"}
    have |= {"plover." + c for c in ("suspend", "resume", "toggle",
                                     "add_translation", "lookup", "suggestions",
                                     "configure", "focus", "quit")}
    # A command plugin is registered under Plover's name for it, and the stack
    # commands have a name of their own here, so the table maps them back.
    theirs = {v: k for k, v in PLUGIN_COMMANDS.items()}
    have |= {theirs.get(p.name, "plover." + p.name)
             for p in registry.list_plugins("command")}
    return have
