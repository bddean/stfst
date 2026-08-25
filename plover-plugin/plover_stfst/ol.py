"""Reader for HFST's `hfstol` runtime format, in Python.

A second implementation of `crates/stfst-ol`, for a host whose interpreter is
what it is: Plover's. The layout is read off HFST's own `transducer.h`, the file
is mapped and nothing is decoded on open, and `scripts/crosscheck-py.sh` holds
this reading to the Rust one over the same tapes the Rust one is held to HFST
with.

Pure Python and no dependency, which is the point. HFST is GPLv3, `pyhfst` is a
second package to install, and neither is needed to walk three flat tables.
"""

import heapq
import mmap
import struct

EPSILON = 0
NO_SYMBOL = 0xFFFF
TRANSITION_TABLE_START = 1 << 31
INDEX_ENTRY = 6
TRANSITION_ENTRY = 8

_INDEX = struct.Struct("<HI")
_TRANSITION = struct.Struct("<HHI")
_U16 = struct.Struct("<H")
_U32 = struct.Struct("<I")


class OlError(Exception):
    pass


class Alphabet:
    """The symbol table. Only symbols an input tape may use have a number."""

    def __init__(self, symbols, input_count):
        self.symbols = symbols
        self.by_name = {}
        self.longest_input = 0
        for i, s in enumerate(symbols[:input_count]):
            if _is_flag_diacritic(s):
                raise OlError("hfstol automaton uses flag diacritics")
            if s in ("@_UNKNOWN_SYMBOL_@", "@_IDENTITY_SYMBOL_@", "@_DEFAULT_SYMBOL_@"):
                raise OlError("hfstol automaton uses unknown or identity input symbols")
            self.by_name[s] = i
            self.longest_input = max(self.longest_input, len(s))

    def symbol(self, s):
        """Epsilon renders as nothing, so a record is the concatenation."""
        return "" if s == EPSILON else self.symbols[s]

    def number(self, name):
        return self.by_name.get(name)

    def numbers(self, names):
        """A symbol sequence as numbers, or None if the automaton lacks one.

        Which is a different answer from "no entry": no path can hold a symbol
        this automaton has never heard of.
        """
        out = []
        for name in names:
            n = self.by_name.get(name)
            if n is None:
                return None
            out.append(n)
        return out

    def tokenize(self, tape):
        """Longest match over the input symbols. None if the tape is unreadable."""
        out, i = [], 0
        while i < len(tape):
            for j in range(min(i + self.longest_input, len(tape)), i, -1):
                n = self.by_name.get(tape[i:j])
                if n is not None:
                    out.append(n)
                    i = j
                    break
            else:
                return None
        return out

    def render(self, syms):
        return "".join(self.symbol(s) for s in syms)


def _is_flag_diacritic(s):
    """`@` operation `.` feature (`.` value) `@`, e.g. `@U.NEED.YES@`."""
    return (len(s) > 4 and s[0] == "@" and s[1] in "PNRDCU"
            and s[2] == "." and s[-1] == "@")


class Automaton:
    """A mapped `hfstol` file: a symbol table, an index table, a transition table."""

    def __init__(self, data):
        self.data = data
        p = 0
        if data[:4] == b"HFST":
            # "HFST" NUL, then a length, then that many bytes of properties, then
            # one more NUL which the length does not count.
            n = self._u16(5)
            p = 7 + n
            if len(data) <= p or data[p] != 0:
                raise OlError("not an hfstol automaton: unterminated property block")
            p += 1
        input_count = self._u16(p)
        symbol_count = self._u16(p + 2)
        self.index_len = self._u32(p + 4)
        self.trans_len = self._u32(p + 8)
        # Two counts we do not need, then nine flags stored a word each.
        flags_at = p + 20
        if self._u32(flags_at) != 0:
            raise OlError("hfstol automaton uses a feature this reader does not have: weights")
        # An epsilon cycle that emits output has no finite set of outputs, so the
        # closure below would not terminate. HFST records whether one is there.
        if self._u32(flags_at + 7 * 4) != 0:
            raise OlError("hfstol automaton uses a feature this reader does not have: "
                          "input epsilon cycles")
        p = flags_at + 9 * 4

        if input_count > symbol_count:
            raise OlError("not an hfstol automaton: more input symbols than symbols")
        symbols = []
        for _ in range(symbol_count):
            end = data.find(b"\0", p)
            if end < 0:
                raise OlError("not an hfstol automaton: truncated symbol table")
            symbols.append(bytes(data[p:end]).decode("utf-8"))
            p = end + 1

        self._start = None
        self.index_at = p
        self.trans_at = p + self.index_len * INDEX_ENTRY
        end = self.trans_at + self.trans_len * TRANSITION_ENTRY
        # The tables are written in native byte order, so this is also the check
        # that catches an artifact built on a big-endian host.
        if end != len(data):
            raise OlError("not an hfstol automaton: table sizes do not match file size")
        self.alphabet = Alphabet(symbols, input_count)

    @classmethod
    def open(cls, path):
        with open(path, "rb") as f:
            # Mapped, so an artifact costs no resident memory beyond the pages a
            # lookup touches. `mmap` is also what `struct.unpack_from` and `find`
            # both read straight out of, which a memoryview is not.
            return cls(mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ))

    def _u16(self, at):
        return self._read(_U16, at)

    def _u32(self, at):
        return self._read(_U32, at)

    def _read(self, field, at):
        try:
            return field.unpack_from(self.data, at)[0]
        except struct.error:
            raise OlError("not an hfstol automaton: truncated header") from None

    def is_final(self, loc):
        """A state's own slot says whether it accepts: NO_SYMBOL with target 1."""
        if loc < TRANSITION_TABLE_START:
            if loc >= self.index_len:
                return False
            return _INDEX.unpack_from(self.data, self.index_at + loc * INDEX_ENTRY) \
                == (NO_SYMBOL, 1)
        q = loc - TRANSITION_TABLE_START
        if q >= self.trans_len:
            return False
        sym, out, target = _TRANSITION.unpack_from(
            self.data, self.trans_at + q * TRANSITION_ENTRY)
        return sym == NO_SYMBOL and target == 1

    def arcs(self, loc, want):
        """Every transition out of `loc` reading `want`, as (output, target).

        Epsilon is not a special case: `want = EPSILON` finds the epsilon
        transitions by the same rule, because HFST reserves symbol 0 the slot it
        would occupy anyway.
        """
        if loc < TRANSITION_TABLE_START:
            # An index state keeps its accepting slot at `loc` and the entry for
            # symbol `s` at `loc + 1 + s`, which is what makes lookup a jump
            # rather than a scan.
            slot = loc + 1 + want
            if slot >= self.index_len:
                return ()
            sym, target = _INDEX.unpack_from(self.data, self.index_at + slot * INDEX_ENTRY)
            if sym != want or target < TRANSITION_TABLE_START:
                return ()
            group = target - TRANSITION_TABLE_START
        else:
            # A transition state has one accepting slot too, and its transitions
            # follow it. An index target points straight at a symbol's group; a
            # transition target points at a state.
            group = loc - TRANSITION_TABLE_START + 1
        out = []
        at = self.trans_at + group * TRANSITION_ENTRY
        unpack, data = _TRANSITION.unpack_from, self.data
        while group < self.trans_len:
            sym, output, target = unpack(data, at)
            if sym != want:
                break
            out.append((output, target))
            group += 1
            at += TRANSITION_ENTRY
        return out

    def out_arcs(self, loc):
        """Every transition out of `loc`, as (input, output, target).

        `arcs` answers what one symbol does, which is what a cursor asks. A walk
        of the whole language asks what the state does.
        """
        if loc < TRANSITION_TABLE_START:
            return [(want, output, target)
                    for want in range(len(self.alphabet.symbols))
                    for output, target in self.arcs(loc, want)]
        out = []
        group = loc - TRANSITION_TABLE_START + 1
        at = self.trans_at + group * TRANSITION_ENTRY
        while group < self.trans_len:
            sym, output, target = _TRANSITION.unpack_from(self.data, at)
            if sym == NO_SYMBOL:
                break
            out.append((sym, output, target))
            group += 1
            at += TRANSITION_ENTRY
        return out

    def pairs(self):
        """Every (input, output) the automaton relates, as symbol tuples.

        None where a cycle puts infinitely many in the language, which is the
        answer for a dictionary that writes a syllable at a time with no end.
        """
        out = []
        if self.is_final(0):
            out.append(((), ()))
        stack = [(0, (), (), iter(self.out_arcs(0)))]
        on_path = {0}
        while stack:
            loc, tape, record, arcs = stack[-1]
            for sym, output, target in arcs:
                if target in on_path:
                    return None
                tape = tape if sym == EPSILON else tape + (sym,)
                record = record if output == EPSILON else record + (output,)
                if self.is_final(target):
                    out.append((tape, record))
                on_path.add(target)
                stack.append((target, tape, record, iter(self.out_arcs(target))))
                break
            else:
                on_path.discard(loc)
                stack.pop()
        return out

    def cursor(self):
        # The closure of state zero is the same every time, and for an automaton
        # that guesses early it is most of what a lookup costs.
        if self._start is None:
            self._start = Cursor(self).frontier
        return Cursor(self, self._start)

    def lookup(self, tape):
        """Every record for a tape, or None if the tape is not readable here."""
        syms = self.alphabet.tokenize(tape)
        if syms is None:
            return None
        return [self.alphabet.render(r) for r in self.lookup_symbols(syms)]

    def lookup_symbols(self, syms):
        cursor = self.cursor()
        for s in syms:
            cursor.step(s)
        return cursor.outputs()

    def cheapest(self, input_syms, weight, limit, budget):
        """The `limit` cheapest outputs for the whole input, cheapest first.

        A forward lookup folds a frontier over a tape, and that works because
        many paths agree on one record. The reverse relation does not converge:
        one word is written by many outlines. So this is a cheapest-first search,
        which asks the right question, not "every outline" but "the few
        shortest".

        Returns (outputs, truncated). `truncated` means a bound ended the search,
        so more outputs may exist; every one returned is still no dearer than any
        one left behind.
        """
        outputs = []
        seen = set()
        # Ordered by cost, then by how far through the input a node is, so a tie
        # goes to the node nearer the end and a run is reproducible.
        heap = [(0, 0, 0, ())]
        arcs, is_final = self.arcs, self.is_final
        for _ in range(budget):
            if not heap:
                return outputs, False
            cost, neg_pos, loc, out = heapq.heappop(heap)
            pos = -neg_pos
            if pos == len(input_syms) and is_final(loc):
                if out not in outputs:
                    outputs.append(out)
                    if len(outputs) >= limit:
                        return outputs, True
            # An input epsilon consumes nothing, so it keeps `pos`.
            steps = [(EPSILON, pos)]
            if pos < len(input_syms):
                steps.append((input_syms[pos], pos + 1))
            for want, next_pos in steps:
                for sym, target in arcs(loc, want):
                    if sym == EPSILON:
                        step, at = out, cost
                    else:
                        step = out + (sym,)
                        at = cost + (weight[sym] if sym < len(weight) else 1)
                    key = (next_pos, target, step)
                    if key not in seen:
                        seen.add(key)
                        heapq.heappush(heap, (at, -next_pos, target, step))
        return outputs, True


class Cursor:
    """Walking the automaton one input symbol at a time.

    A steno host does not have the whole outline when it starts looking: it has
    one stroke, then another. So the traversal is a frontier of live paths that a
    symbol advances, and a plain lookup is that frontier folded over a tape.

    The frontier can be wide, so this is a search and not a walk: an automaton
    that emits output the input has not yet decided has to guess, and every guess
    is a live path until a symbol kills it.
    """

    __slots__ = ("fst", "frontier")

    def __init__(self, fst, frontier=None):
        self.fst = fst
        if frontier is not None:
            self.frontier = list(frontier)
            return
        self.frontier = [(0, ())]
        self._close()

    def clone(self):
        """A cursor in the same place. A path is immutable, so this is a copy of
        the list holding them."""
        return Cursor(self.fst, self.frontier)

    def live(self):
        """How many paths are live. Zero means no stroke can extend what has been
        read into an entry."""
        return len(self.frontier)

    def step(self, sym):
        out, seen = [], set()
        self._advance(self.frontier, sym, out, seen)
        self.frontier = out
        self._close(seen)

    def _advance(self, paths, sym, out, seen):
        """Where these paths go on reading `sym`, minus what is already there."""
        arcs = self.fst.arcs
        for loc, emitted in paths:
            for got, target in arcs(loc, sym):
                path = (target, emitted if got == EPSILON else emitted + (got,))
                if path not in seen:
                    seen.add(path)
                    out.append(path)

    def _close(self, seen=None):
        """Follow every epsilon transition, so the frontier only ever holds states
        a real symbol can leave."""
        if seen is None:
            seen = set(self.frontier)
        frontier = self.frontier
        # A generation at a time, into the frontier itself. A path already seen
        # ends the closure, because a repeat has nothing new below it.
        at = 0
        while at < len(frontier):
            end = len(frontier)
            self._advance(frontier[at:end], EPSILON, frontier, seen)
            at = end

    def outputs(self):
        """Every record reachable by what has been read so far."""
        is_final = self.fst.is_final
        out = []
        for loc, emitted in self.frontier:
            if is_final(loc) and emitted not in out:
                out.append(emitted)
        return out
