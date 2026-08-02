"""Plover outlines as key tapes, for the golden generators.

A second implementation of `stfst_core::system`'s outline parser, reading the
same system file. Small on purpose, and here for the same reason
`plover_record.py` is: a generator that asked our compiler what an outline means
would write a snapshot of our own answer instead of an oracle.

It also replaces the hand-written tapes the earlier generators use. Those were
safe because a tape is the keys in steno order, but only while a human types them
correctly, and `TPOR` is `T- P- O- -R` for a reason no one remembers at the
keyboard.

Usage:

    import plover_tape
    system = plover_tape.System.load("systems/stenotype-extended.toml")
    system.tape("^TPOR")        # -> "^TPOr"
"""

import re


class System:
    def __init__(self, text):
        # tomllib is 3.11+, and this file has to run under whatever python is to
        # hand, so read the three fields directly. The format is fixed by the
        # loader on the Rust side, which rejects anything it cannot read.
        def field(name):
            m = re.search(r'^%s\s*=\s*"([^"]*)"' % name, text, re.M)
            if not m:
                raise ValueError("no %s in the system file" % name)
            return m.group(1)

        self.order = field("order")
        self.separator = field("separator")
        names = dict(re.findall(r'^"(.)"\s*=\s*"([^"]+)"', text, re.M))
        numbers = dict(re.findall(r'^"(\d)"\s*=\s*"(.)"', text, re.M))

        self.letters = []      # the letter each key is written with, in order
        self.sides = []
        for symbol in self.order:
            name = names[symbol]
            self.letters.append(name.strip("-").upper() if name != "#" else "#")
            self.sides.append("right" if name.startswith("-")
                              else "left" if name.endswith("-") else "center")
        self.first_right = self.sides.index("right")
        self.numbers = {d: self.order.index(s) for d, s in numbers.items()}

    @classmethod
    def load(cls, path):
        with open(path) as f:
            return cls(f.read())

    def stroke(self, chunk):
        """One written stroke as key indices, in steno order."""
        if any(c.isdigit() for c in chunk):
            # The number bar substitutes digits for letter keys, so undo that
            # first and parse one language rather than two.
            text = "#" + "".join(self.letters[self.numbers[c]] if c.isdigit() else c
                                 for c in chunk if c != "#")
        else:
            text = chunk
        out, pos = [], 0
        for c in text:
            if c == "-":
                pos = max(pos, self.first_right)
                continue
            found = next((i for i in range(pos, len(self.order))
                          if self.letters[i] == c), None)
            if found is None:
                raise ValueError("%r: %r is not a key here, or is out of order"
                                 % (chunk, c))
            out.append(found)
            pos = found + 1
        if not out:
            raise ValueError("%r: empty stroke" % chunk)
        return out

    def tape(self, outline):
        """A written outline as its key tape."""
        return self.separator.join(
            "".join(self.order[i] for i in self.stroke(chunk))
            for chunk in outline.split(self.separator))
