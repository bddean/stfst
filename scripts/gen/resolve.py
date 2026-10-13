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


def pipeline(name):
    """[(stage, file)] for each stage of a dictionary's pipeline after the
    first: the file the compiler wrote, of what the pipeline relates up to
    that stage."""
    with open(RECIPE, "rb") as f:
        stages = next(d for d in tomllib.load(f)["dict"] if d["name"] == name)["pipeline"]
    return [(s, os.path.join(ROOT, "build", "work", name, "pipeline", f"{s}.tsv")) for s in stages[1:]]


def relation(path):
    """{word: {what it relates to}} from a stage's file."""
    out = collections.defaultdict(set)
    with open(path) as f:
        for line in f:
            word, to = line.rstrip("\n").split("\t")
            out[word].add(to)
    return out


def candidates(name):
    """{word: {outline: cost}} from what the compiler wrote for a dictionary's
    pipeline, in the order the word takes them: by the pronunciation's place in
    CMU's order, then by the cost, which is the rewrites the word has another
    outline without (a `'` each, less those all its outlines have), then the
    shorter."""
    path = pipeline(name)[-1][1]
    if not os.path.exists(path):
        sys.exit(f"{path} is missing: compile {name} first")
    out = {}
    for word, tos in relation(path).items():
        found = sorted((int(rank.rstrip("'")), rank.count("'"), len(tape), tape)
                       for rank, tape in (to.split(" ", 1) for to in tos))
        least = min(cost for _, cost, _, _ in found)
        out[word] = {}
        for _, cost, _, tape in found:
            out[word].setdefault(_SYSTEM.outline(tape), cost - least)
    return out


def unwritten_briefs(name, out):
    """[(outline, translation)] of `tests/briefs/NAME.tsv` that `out`, a
    dictionary's {outline: translation}, does not write. Those are the user's
    hand briefs that the rule came to write, so their entries left user.json."""
    path = os.path.join(ROOT, "tests", "briefs", f"{name}.tsv")
    if not os.path.exists(path):
        return []
    with open(path) as f:
        rows = [line.rstrip("\n").split("\t") for line in f if line.strip()]
    return [(o, t) for o, t in rows if out.get(o) != t]


def compile_pipeline(name):
    """Bring a dictionary's pipeline up to date, which needs HFST: run in the
    dev shell."""
    stfst = os.path.join(ROOT, "target", "release", "stfst")
    if subprocess.run([stfst, "compile", RECIPE, "--dict", name, "--pipeline-only"], cwd=ROOT).returncode:
        sys.exit(f"compiling {name}'s pipeline failed")


def resolve(outlines, rank, taken, gives_way=lambda outline, word, other: False, firsts=frozenset()):
    """(entries, lost, left) for `outlines`, {word: {outline: cost}}.

    An outline goes to the word that wants it at the least cost, and among
    those to the first by `rank`, a sort key; a word takes the first of its
    outlines it can, and then every other one no word took. `taken` is
    {outline: (dictionary, translation)}, what the rest of the stack writes
    there, and the outline is left to it unless it writes the same word or
    `gives_way(outline, word, (dictionary, translation))`. An outline in
    `firsts` goes only to a word that takes it first. Returns {outline: word}, and for each word with no
    outline, why its first had to go: [(loser, winner, outline)] and [(word,
    outline, (dictionary, translation))]."""
    order = sorted(outlines, key=rank)
    out, placed = {}, set()

    def free(o, w):
        other = taken.get(o)
        return o not in out and not (other and other[1] != w and not gives_way(o, w, other))

    for cost in sorted({c for cs in outlines.values() for c in cs.values()}):
        for w in order:
            if w in placed:
                continue
            for o, c in outlines[w].items():
                if c == cost and free(o, w):
                    out[o] = w
                    placed.add(w)
                    break
    for w in order:
        for o in outlines[w] if w in placed else ():
            if o not in firsts and free(o, w):
                out[o] = w
    lost, left = [], []
    for w in order:
        if w not in placed:
            o = next(iter(outlines[w]))
            if o in out:
                lost.append((w, out[o], o))
            else:
                left.append((w, o, taken[o]))
    return out, lost, left


def typey_gives_way(reserved=()):
    """Typey Type's entry gives way, as it does to the vowel theory, unless it
    is reserved (an affix) or a hundred times as frequent as the word."""
    return lambda outline, word, other: (other[0] == "typey-type" and outline not in reserved
                                         and g.zipf(other[1]) <= g.zipf(word) + g.DISPLACE)
