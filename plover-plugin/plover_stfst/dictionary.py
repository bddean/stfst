"""The `plover.dictionary` entry point: an artifact directory Plover can load.

Plover asks a dictionary two questions: what does this outline write, and what
writes this text. An artifact answers both, so the plugin is a translation of
vocabulary and not of machinery: strokes become a key tape, a record becomes a
Plover translation, and the reverse automaton answers the second question that
`phrasing.py` answered with ten nested loops.

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

from . import hold, window
from .ol import Automaton
from .record import PLUGIN_COMMANDS, Record, Unsupported

# What this plugin cannot do, by the manifest field that asks for it. Not a host
# version: the version is one number and these are separate abilities, and the
# gate has to be the ability. `[AFTER]` is absent because `plover_stfst.hold` is
# what does it.
#
# Every one of these needs the host to say something on the input tape *before*
# the answer, and Plover's dictionary API has one argument: the outline. A
# dictionary that asks is unavailable here rather than broken.
CANNOT = {
    "words": lambda n: "%d words of what is already written" % n,
    "registers": lambda n: "%d bound words" % n,
    "state": lambda _: "a carried state",
    "context": lambda slots: _slots(slots),
}

# The context slots this plugin can answer. `CAP` is whether a capital is
# pending, which the formatter's last action says; `W1` is the case of a bound
# word, and nothing binds one here.
SLOTS = ("CAP",)

# What Plover writes for itself. Every one is a built-in meta, so no dictionary
# needs a plugin to get it; `infl` is the exception and is checked for by name.
BUILTIN_METAS = frozenset(["retro.cap", "retro.upper", "retro.lower",
                           "retro.currency"])

# How many outlines a reverse lookup returns, shortest first. A generative
# dictionary writes one word with unboundedly many outlines, so "every outline"
# is not a finite answer and the suggestions window wants the few shortest.
REVERSE_LIMIT = 8

# How many nodes the search may look at per outline it wants. A phrasing
# automaton emits keys the text has not yet chosen, so the cheap end of its
# search holds tens of thousands of nodes and a word it does not write costs the
# whole budget. Over 120 sampled translations against every artifact here, 512
# returns what 4096 returns and costs an eighth as much.
REVERSE_BUDGET = 512


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
        self._cursors = {}       # outline -> the cursor after it, or None
        self._older = {}
        self._generation = 0
        self._entries = None     # outline -> translation, walked on first ask
        self._weight = []
        self._next = 0           # words of the *next* unit this reads, if any
        self._context = ()       # the context slots it reads, in table order
        self._significant = ""   # the characters it named, so they reach it raw

    def _load(self, filename):
        manifest = _manifest(filename)
        name = manifest["dict"]["name"]
        needs = manifest["requires"]
        wants = _why(needs)
        if wants:
            raise ValueError(
                "%s reads %s, which Plover has no way to tell a dictionary"
                % (name, wants))
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
        # A frame reads the text the next unit writes, so its answer waits for a
        # word: `plover_stfst.hold` is what holds the stroke, and it needs to
        # know this dictionary by name to ask it again.
        self._next = needs.get("next", 0)
        self._context = tuple(s for s in SLOTS if s in needs["context"])
        self._significant = needs.get("significant", "")
        if self._next:
            hold.frames.append(self)
        # A stroke asks about one outline per length and again on a retry, so a
        # generation wide enough for a stroke's worth of questions is one that
        # never drops what the next stroke wants. Two of them are kept.
        self._generation = 4 * strokes + 32
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

    # ---- what it holds

    def __iter__(self):
        return iter(self._listing())

    def __len__(self):
        return len(self._listing())

    def items(self):
        return self._listing().items()

    def __contains__(self, key):
        return self.get(key) is not None

    def _listing(self):
        """Every outline this artifact holds, against what it writes.

        Walked out of the automaton on the first ask and then held. A dictionary
        that writes a syllable at a time relates one word to outlines with no
        longest, so it lists none rather than a prefix of them.
        """
        if self._entries is not None:
            return self._entries
        self._entries = {}
        if self._next:
            # Half of every tape here is text the writer has not written, so
            # there is no outline-to-translation pair to list.
            log.info("%s: a frame writes what it is shown, so it lists nothing",
                     self.path)
            return self._entries
        pairs = self._fst.pairs()
        if pairs is None:
            log.info("%s: outlines with no longest, so it lists none", self.path)
            return self._entries
        alphabet = self._fst.alphabet
        for tape, record in pairs:
            outline = self._outline(alphabet.render(tape))
            if outline is None:
                continue
            try:
                self._entries[outline] = Record.parse(
                    [alphabet.symbol(s) for s in record]).translation()
            except Unsupported:
                # What `get` answers nothing for is not listed either, so the two
                # agree on what this dictionary holds.
                pass
        return self._entries

    # ---- what an outline writes

    def __getitem__(self, key):
        value = self.get(key)
        if value is None:
            raise KeyError(repr(key))
        return value

    def get(self, key, fallback=None):
        if self._next:
            return self._frame_marker(key) or fallback
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

    # ---- a frame: the text it reads is not written yet
    #
    # Plover asks one question, "what does this outline mean", and a frame's
    # answer depends on what the *next* stroke means, which another dictionary
    # owns. So the answer here is a marker that writes nothing, and the extension
    # in `plover_stfst.hold` asks again once a unit has written a word.

    def frame_words(self):
        """The most words a frame of this dictionary can be shown."""
        return self._next

    def _frame_marker(self, key):
        """The held translation for an outline some frame begins, or None.

        Live and not final: whether the entry matches at all is unknown until the
        window arrives, which is the whole reason the stroke waits. A dictionary
        of frames therefore has to sit below everything in `plover.cfg`, so that
        it answers only where nothing else did, exactly as it is a fallback in
        `stfst-board`.
        """
        if not any(self._frame_cursor(key, cap) for cap in ("off", "on")):
            return None
        return "%sframe:%d:%s}" % (hold.HOLD, hold.frames.index(self),
                                   "/".join(key))

    def _frame_cursor(self, key, cap):
        """A cursor over the context slots and then the keys, or None if dead.

        Not the memoised walk `_cursor` does: a frame's keys are read under a
        prefix, and it is asked once per hold rather than once per stroke.
        """
        alphabet = self._fst.alphabet
        cursor = self._fst.cursor()
        # A slot with no value here is one `SLOTS` claims and nothing computes,
        # which is a bug in this plugin rather than in the dictionary.
        values = {"CAP": cap}
        for slot in self._context:
            sym = alphabet.number("[%s=%s]" % (slot, values[slot]))
            if sym is None:
                return None
            cursor.step(sym)
        for n, steno in enumerate(key):
            if n and self._separator_sym is not None:
                cursor.step(self._separator_sym)
            syms = self._symbols(steno)
            if syms is None:
                return None
            for sym in syms:
                cursor.step(sym)
        return cursor if cursor.live() else None

    def frame(self, key, span, cap):
        """What a frame writes over the span it was shown, or None.

        The window goes on the tape after the keys, one symbol per character, and
        what comes back is a record whose class glyphs stand for those characters
        in order. So the casing is applied here, with Python's own Unicode rules,
        and the transducer only decided where.
        """
        cursor = self._frame_cursor(tuple(key), cap)
        if cursor is None:
            return None
        alphabet = self._fst.alphabet
        symbols, chars = window.encode(span, self._significant)
        for symbol in symbols:
            sym = alphabet.number(symbol)
            if sym is None:
                # A class this dictionary's rules never mention, so the stroke
                # does not translate: the visible answer, rather than a rule
                # matching half a word.
                return None
            cursor.step(sym)
        for output in cursor.outputs():
            try:
                return Record.parse([alphabet.symbol(s) for s in output]).rewrite(chars)
            except Unsupported as e:
                log.info("%s: %s over %r writes what Plover cannot: %s",
                         self.path, "/".join(key), span, e)
        return None

    def _record(self, key):
        """The first record this artifact holds for an outline, or None.

        More than one means the dictionary is ambiguous for this outline. Plover
        asks for a single translation, so the first is the answer and the order is
        the automaton's.
        """
        cursor = self._cursor(tuple(key))
        if cursor is None:
            return None
        alphabet = self._fst.alphabet
        for output in cursor.outputs():
            try:
                return Record.parse([alphabet.symbol(s) for s in output])
            except Unsupported as e:
                log.error("%s: %s does not parse: %s", self.path, "/".join(key), e)
        return None

    def _cursor(self, key):
        """The cursor after this outline, or None where no entry extends it.

        Plover hands over a whole outline, so a lookup would walk from the start
        every time. It asks about one outline per length though, and each of them
        is what it asked one stroke shorter on the stroke before, so the walk is a
        step from a cursor already taken.
        """
        if not key:
            return None
        for held in (self._cursors, self._older):
            if key in held:
                return held[key]
        cursor = None
        if len(key) == 1:
            cursor = self._fst.cursor()
        elif self._separator_sym is not None:
            base = self._cursor(key[:-1])
            if base is not None:
                cursor = base.clone()
                cursor.step(self._separator_sym)
        syms = None if cursor is None else self._symbols(key[-1])
        if syms is None:
            cursor = None
        else:
            for sym in syms:
                cursor.step(sym)
            if not cursor.live():
                cursor = None
        if len(self._cursors) >= self._generation:
            self._older, self._cursors = self._cursors, {}
        self._cursors[key] = cursor
        return cursor

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
                                       REVERSE_LIMIT * REVERSE_BUDGET)
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
    """What this dictionary asks for that Plover cannot say, by name.

    Empty for one it can host, which is what makes this the load gate. A field
    this plugin can answer in part answers for itself, and says nothing.
    """
    wants = [say(needs[field]) for field, say in CANNOT.items()
             if needs.get(field)]
    return " and ".join(w for w in wants if w)


def _slots(declared):
    """The context slots this plugin cannot answer, if any."""
    unknown = sorted(set(declared) - set(SLOTS))
    if not unknown:
        return None
    return "the context slot " + ", ".join(unknown)


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
