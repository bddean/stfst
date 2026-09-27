"""What a pipeline relates, resolved into a dictionary: which word keeps an
outline two words want, and which outlines are left to the rest of the stack.

A pipeline (recipe.toml's `pipeline`) relates each word to the outlines a rule
gives it, and the compiler writes that relation out. `candidates` reads it,
`resolve` decides it, and each generator says only what its dictionary ranks
words by and when another dictionary's entry gives way.
"""
import collections
import os
import subprocess
import sys
import tomllib

import plover_tape

from . import RECIPE, ROOT, SYSTEM
from . import vowels as g

_SYSTEM = plover_tape.System.load(SYSTEM)


def candidates(name):
    """{word: outline} from what the compiler wrote for a dictionary's pipeline.
    A word the rule writes by several pronunciations takes the first's, as CMU
    orders them."""
    with open(RECIPE, "rb") as f:
        last = next(d for d in tomllib.load(f)["dict"] if d["name"] == name)["pipeline"][-1]
    path = os.path.join(ROOT, "build", "work", name, "pipeline", f"{last}.tsv")
    if not os.path.exists(path):
        sys.exit(f"{path} is missing: compile {name} first")
    best = {}
    with open(path) as f:
        for line in f:
            word, out = line.rstrip("\n").split("\t")
            rank, tape = out.split(" ", 1)
            if word not in best or int(rank) < best[word][0]:
                best[word] = (int(rank), tape)
    return {w: _SYSTEM.outline(tape) for w, (_, tape) in best.items()}


def compile_pipeline(name):
    """Bring a dictionary's pipeline up to date, which needs HFST: run in the
    dev shell."""
    stfst = os.path.join(ROOT, "target", "release", "stfst")
    if subprocess.run([stfst, "compile", RECIPE, "--dict", name, "--pipeline-only"], cwd=ROOT).returncode:
        sys.exit(f"compiling {name}'s pipeline failed")


def resolve(outlines, rank, taken, gives_way=lambda outline, word, other: False):
    """(entries, lost, left) for `outlines`, {word: outline}.

    Words on one outline go to the first by `rank`, a sort key. `taken` is
    {outline: (dictionary, translation)}, what the rest of the stack writes
    there, and the outline is left to it unless it writes the same word or
    `gives_way(outline, word, (dictionary, translation))`. Returns
    {outline: word}, [(loser, winner, outline)] and
    [(word, outline, (dictionary, translation))]."""
    by = collections.defaultdict(list)
    for w, o in outlines.items():
        by[o].append(w)
    out, lost, left = {}, [], []
    for o, ws in by.items():
        ws.sort(key=rank)
        other = taken.get(o)
        if other and other[1] != ws[0] and not gives_way(o, ws[0], other):
            left += [(w, o, other) for w in ws]
            continue
        out[o] = ws[0]
        lost += [(w, ws[0], o) for w in ws[1:]]
    return out, lost, left


def typey_gives_way(reserved=()):
    """Typey Type's entry gives way, as it does to the vowel theory, unless it
    is reserved (an affix) or a hundred times as frequent as the word."""
    return lambda outline, word, other: (other[0] == "typey-type" and outline not in reserved
                                         and g.zipf(other[1]) <= g.zipf(word) + g.DISPLACE)
