"""What a pipeline relates, which a generator resolves into a dictionary.

A pipeline (recipe.toml's `pipeline`) relates each word to the outlines a rule
gives it, and the compiler writes that relation out. `candidates` reads it.
"""
import os
import subprocess
import sys
import tomllib

import plover_tape

from . import RECIPE, ROOT, SYSTEM

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
