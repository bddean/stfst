#!/usr/bin/env python3
"""Predict what a delayed-alignment artifact would cost, before building one.

`docs/M0.md` leaves two open questions about the encoding: whether sharing
translation tails makes the artifact smaller, and how much of it the output side
occupies. Both are answerable from the import pairs alone, because the minimal
acyclic automaton over a set of strings is unique. So build it for each encoding
and count.

Usage: scripts/encoding-experiment.py build/pairs.tsv
"""
import resource
import sys

MEMORY_LIMIT_MB = 3000  # a DAWG over 145,000 entries should need a tenth of this


def rss_mb():
    peak = resource.getrusage(resource.RUSAGE_SELF).ru_maxrss
    return peak / (1024 * 1024) if sys.platform == "darwin" else peak / 1024


class Node:
    __slots__ = ("final", "kids", "id")
    _next = 0

    def __init__(self):
        self.final = False
        self.kids = {}
        Node._next += 1
        self.id = Node._next

    def key(self):
        return (self.final, tuple(sorted((s, k.id) for s, k in self.kids.items())))


def dawg(words):
    """Daciuk's incremental construction. Words must arrive sorted."""
    root = Node()
    register = {}
    previous = ()
    unchecked = []

    def minimise(down_to):
        while len(unchecked) > down_to:
            parent, symbol, child = unchecked.pop()
            key = child.key()
            if key in register:
                parent.kids[symbol] = register[key]
            else:
                register[key] = child

    for n, word in enumerate(words):
        common = 0
        while common < min(len(word), len(previous)) and word[common] == previous[common]:
            common += 1
        minimise(common)
        node = unchecked[-1][2] if unchecked else root
        for symbol in word[common:]:
            child = Node()
            node.kids[symbol] = child
            unchecked.append((node, symbol, child))
            node = child
        node.final = True
        previous = word
        if n % 10000 == 0 and rss_mb() > MEMORY_LIMIT_MB:
            sys.exit(f"stopping at {n} words: {rss_mb():.0f} MB resident")
    minimise(0)

    states = {id(root)}
    arcs = 0
    stack = [root]
    while stack:
        node = stack.pop()
        arcs += len(node.kids)
        for kid in node.kids.values():
            if id(kid) not in states:
                states.add(id(kid))
                stack.append(kid)
    return len(states), arcs


def main(path):
    pairs = []
    for line in open(path):
        tape, symbols = line.rstrip("\n").split("\t")
        pairs.append((list(tape), symbols.split("\x1f") if symbols else []))

    ins = sum(len(t) for t, _ in pairs)
    outs = sum(len(r) for _, r in pairs)
    records = {tuple(r) for _, r in pairs}
    print(f"{len(pairs)} entries, {ins} input symbols, {outs} output symbols")
    print(f"{len(records)} distinct records behind them, "
          f"{len(pairs) / len(records):.2f} outlines each")

    # Pairwise is what lexc does: pair symbol i with symbol i and pad the short
    # side with epsilon. Delayed reads the whole outline, then writes.
    def pairwise(tape, record):
        n = max(len(tape), len(record))
        return tuple((tape[i] if i < len(tape) else "", record[i] if i < len(record) else "")
                     for i in range(n))

    def delayed(tape, record):
        return tuple((s, "") for s in tape) + tuple(("", s) for s in record)

    # Two baselines. The gap between `inputs` and `pairwise` is what carrying
    # the output costs; `records` alone is what a shared output side could be.
    def inputs(tape, _record):
        return tuple((s, "") for s in tape)

    def records_only(_tape, record):
        return tuple(("", s) for s in record)

    for name, encode in (
        ("inputs", inputs),
        ("records", records_only),
        ("pairwise", pairwise),
        ("delayed", delayed),
    ):
        Node._next = 0
        words = sorted(encode(t, r) for t, r in pairs)
        states, arcs = dawg(words)
        print(f"{name:9} {states:8} states {arcs:8} arcs   "
              f"{arcs * 8 / 1e6:6.2f} MB of transitions at 8 bytes each")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "build/pairs.tsv")
