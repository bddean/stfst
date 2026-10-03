#!/usr/bin/env python3
"""A recipe's dictionaries as rows a shell script can read.

    scripts/recipe.py RECIPE system   the system file
    scripts/recipe.py RECIPE dicts    name, kind, source, generators, goldens
    scripts/recipe.py RECIPE reserved NAME   the keys other dictionaries own

One tab-separated row per dictionary in recipe order. A source is expanded, and
several files are separated by spaces, as are goldens. The generators are those
of the dictionary's pipeline stages, then its own. An absent field is `-`.
"""
import os
import sys
import tomllib


def rows(recipe, base):
    at = lambda f: os.path.normpath(os.path.join(base, os.path.expanduser(f)))
    stages = {s["name"]: s for s in recipe.get("stage", [])}
    for d in recipe["dict"]:
        kind = next(k for k in ("xfst", "json", "plain") if k in d)
        files = d[kind] if isinstance(d[kind], list) else [d[kind]]
        generators = [stages[s].get("generator") for s in d.get("pipeline", [])] + [d.get("generator")]
        generators = list(dict.fromkeys(at(g) for g in generators if g))
        yield (d["name"], kind, " ".join(map(at, files)), " ".join(generators) or "-",
               " ".join(map(at, d.get("golden", []))) or "-")


def reserved(recipe, name):
    """`Recipe::reserved`: the keys another dictionary owns, which NAME may not hold."""
    own = next(d for d in recipe["dict"] if d["name"] == name).get("owns", "")
    return "".join(sorted({k for d in recipe["dict"] for k in d.get("owns", "")} - set(own)))


def main(path, what, name=None):
    with open(path, "rb") as f:
        recipe = tomllib.load(f)
    if what == "system":
        print(os.path.join(os.path.dirname(path), recipe["system"]))
    elif what == "dicts":
        for row in rows(recipe, os.path.dirname(path)):
            print("\t".join(row))
    elif what == "reserved":
        print(reserved(recipe, name))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(*sys.argv[1:4])
