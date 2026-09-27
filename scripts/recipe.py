#!/usr/bin/env python3
"""A recipe's dictionaries as rows a shell script can read.

    scripts/recipe.py RECIPE system   the system file
    scripts/recipe.py RECIPE dicts    name, kind, source, generator, goldens

One tab-separated row per dictionary in recipe order. A source is expanded, and
several files are separated by spaces, as are goldens. An absent field is `-`.
"""
import os
import sys
import tomllib


def rows(recipe, base):
    at = lambda f: os.path.normpath(os.path.join(base, os.path.expanduser(f)))
    for d in recipe["dict"]:
        kind = next(k for k in ("xfst", "json", "plain") if k in d)
        files = d[kind] if isinstance(d[kind], list) else [d[kind]]
        generator = d.get("generator")
        yield (d["name"], kind, " ".join(map(at, files)), at(generator) if generator else "-",
               " ".join(map(at, d.get("golden", []))) or "-")


def main(path, what):
    with open(path, "rb") as f:
        recipe = tomllib.load(f)
    if what == "system":
        print(os.path.join(os.path.dirname(path), recipe["system"]))
    elif what == "dicts":
        for row in rows(recipe, os.path.dirname(path)):
            print("\t".join(row))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(*sys.argv[1:3])
