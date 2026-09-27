#!/usr/bin/env python3
"""Fail when `plover.cfg` and `recipe.toml` disagree about the stack.

Plover's Stenotype Extended list loads what the recipe builds, and its order is
priority there as `priority` is here. So each dictionary the recipe builds is in
that list, and the enabled ones come in the recipe's priority order. An entry the
recipe does not build (`stfst-user.json`, `semantic-retro.json`) is Plover's own
and is not compared.

Usage: scripts/check-plover-cfg.py [recipe.toml] [plover.cfg]
"""
import configparser
import json
import os
import sys
import tomllib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PLOVER = os.path.expanduser("~/.config/plover")
SECTION = "System: Stenotype Extended"


def resolve(path, base):
    return os.path.normpath(os.path.join(base, os.path.expanduser(path)))


def problems(recipe_path, cfg_path):
    with open(recipe_path, "rb") as f:
        recipe = tomllib.load(f)
    base = os.path.dirname(os.path.abspath(recipe_path))
    priority = {d["name"]: d["priority"] for d in recipe["dict"]}
    # A dictionary Plover reads as the JSON file it is compiled from is the same
    # dictionary, so the JSON path names it too.
    by_path = {resolve(os.path.join("build", "dicts", n + ".stfst"), ROOT): n for n in priority}
    by_path.update((resolve(d["json"], base), d["name"]) for d in recipe["dict"] if "json" in d)

    cfg = configparser.RawConfigParser()
    cfg.read(cfg_path)
    entries = json.loads(cfg[SECTION]["dictionaries"])
    out, listed, enabled = [], set(), []
    for e in entries:
        path = resolve(e["path"], PLOVER)
        name = by_path.get(path)
        if name is None:
            if path.startswith(resolve("build/dicts", ROOT) + os.sep):
                out.append(f"plover.cfg loads {e['path']}, which recipe.toml does not build")
            continue
        listed.add(name)
        if e["enabled"]:
            enabled.append(name)
    out += [f"recipe.toml builds {n}, which plover.cfg does not list" for n in priority if n not in listed]
    for above, below in zip(enabled, enabled[1:]):
        if priority[above] <= priority[below]:
            out.append(f"plover.cfg puts {above} above {below}, and recipe.toml gives them "
                       f"priorities {priority[above]} and {priority[below]}")
    return out


def main(recipe_path=os.path.join(ROOT, "recipe.toml"), cfg_path=os.path.join(PLOVER, "plover.cfg")):
    if not os.path.exists(cfg_path):
        print(f"skipped: no {cfg_path}")
        return
    found = problems(recipe_path, cfg_path)
    if found:
        sys.exit("\n".join(found))
    print("plover.cfg lists the recipe's dictionaries in its order")


if __name__ == "__main__":
    main(*sys.argv[1:])
