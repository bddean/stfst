#!/usr/bin/env python3
"""The whole Plover stack against the dictionaries it was compiled from.

`check-plugin.py` asks one dictionary about one entry. This asks the *stack* what
an outline writes, which is a different question: a dictionary that claims one
outline too many or one too few changes what a dictionary below it gets to say,
and no per-entry check can see that.

So the stack in `plover.cfg` is loaded twice, once as configured and once with
every artifact replaced by the file it was compiled from, and both are asked
every outline any of them defines. What is compared is what lands in the
document, rendered by Plover's own formatter, because two spellings of one
translation are not a difference and neither are two spellings of one keystroke.

`emily-modifiers.py` runs an skhd binding's shell command from `lookup`, so
walking its outlines launches applications. Nothing here may reach the desktop,
which is what the guard below is for.

Run under Plover's own python:

  /Applications/Plover.app/.../python3.13 scripts/check-stack.py
"""

import json
import os
import subprocess
import sys
import tomllib

_spawned = []


def _refuse(*args, **kwargs):
    _spawned.append(args[0] if args else kwargs.get("args"))
    raise RuntimeError("no subprocess from a checker")


subprocess.Popen = _refuse
subprocess.run = _refuse
subprocess.call = _refuse
subprocess.check_output = _refuse
os.system = _refuse

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
CONFIG = os.path.expanduser("~/.config/plover/")

sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(CONFIG, "plugins/mac/lib/python/site-packages"))

from plover_render import renderer

# Where a generated dictionary came from. An imported one names its own source in
# `recipe.toml`, so only these need a table.
GENERATED = {
    "emily-symbols": "emily-symbols.py",
    "emily-modifiers": "emily-modifiers.py",
    "phrasing": "phrasing.py",
    "shrimple": "Shrimple-WSI-English.py",
}

# Modifier nesting order and the `control`/`control_l` spellings are one
# keystroke: Plover aliases the names and presses every modifier before the key.
ALIAS = {"option": "alt", "command": "super", "windows": "super",
         "meta": "super", "function": "fn"}
MODIFIERS = frozenset(["shift", "control", "alt", "super", "fn"])


def sources():
    """Each artifact name against the dictionary it was compiled from."""
    out = {name: CONFIG + path for name, path in GENERATED.items()}
    with open(os.path.join(ROOT, "recipe.toml"), "rb") as f:
        for dict_ in tomllib.load(f)["dict"]:
            if "json" in dict_:
                out[dict_["name"]] = os.path.expanduser(dict_["json"])
    return out


def stack():
    """The enabled dictionaries of the live Stenotype Extended stack, in order."""
    text = open(os.path.join(CONFIG, "plover.cfg")).read()
    line = text.split("[System: Stenotype Extended]\n", 1)[1].split("\n", 1)[0]
    out = []
    for entry in json.loads(line.split("dictionaries = ", 1)[1]):
        if not entry["enabled"]:
            continue
        path = os.path.expanduser(entry["path"])
        out.append(path if os.path.isabs(path) else CONFIG + path)
    return out


def combo(text):
    from plover.key_combo import parse_key_combo
    names = []
    for name, down in parse_key_combo(text):
        if not down:
            continue
        name = name[:-2] if name.endswith(("_l", "_r")) else name
        names.append(ALIAS.get(name, name))
    return "%s+%s" % (",".join(sorted(set(n for n in names if n in MODIFIERS))),
                      " ".join(n for n in names if n not in MODIFIERS))


def load(paths):
    from plover.dictionary.base import load_dictionary
    return [(os.path.basename(p), load_dictionary(p)) for p in paths]


def ask(dicts, key):
    for name, d in dicts:
        try:
            value = d.get(key)
        except RuntimeError:
            # The skhd branch, which the guard above stopped. That dictionary
            # answers `{#}` once it has run the command.
            value = "{#}"
        if value is not None:
            return name, value
    return None, None


def corpus(configured):
    """Every outline any dictionary in the stack defines.

    An imported dictionary hands over its keys. A generated one has no key list,
    so its golden file stands in, which is every stroke its own checker covers.
    `phrasing` has neither, and its own checker is exhaustive.
    """
    from plover.steno import normalize_steno
    keys, by_name = set(), dict(configured)
    for name, path in sources().items():
        if path.endswith(".json"):
            for outline in json.load(open(path)):
                try:
                    keys.add(normalize_steno(outline))
                except ValueError:
                    pass
            continue
        golden = os.path.join(ROOT, "tests/golden/%s.tsv" % name)
        artifact = by_name.get(name + ".stfst")
        if artifact is None or not os.path.exists(golden):
            continue
        for line in open(golden):
            if line.startswith("!") or not line.strip():
                continue
            outline = artifact._outline(line.split("\t")[0])
            if outline:
                keys.add(outline)
    return sorted(keys)


def main():
    import plover.system
    from plover.registry import registry
    registry.update()
    plover.system.setup("Stenotype Extended")

    where = sources()
    configured = load(stack())
    compiled = {n for n, _ in configured if n.endswith(".stfst")}
    if not compiled:
        print("no artifact in the stack: nothing to compare against")
        return 0
    against = load([where[os.path.basename(p)[:-len(".stfst")]]
                    if p.endswith(".stfst") else p for p in stack()])

    render = renderer(combo=lambda x: "<%s>" % combo(x),
                      command=lambda x: "<<%s>>" % x)
    keys = corpus(configured)
    print("%d outlines, %d artifacts in the stack" % (len(keys), len(compiled)))
    differ, by = 0, {}
    for key in keys:
        an, av = ask(against, key)
        bn, bv = ask(configured, key)
        if av == bv:
            continue
        if av is not None and bv is not None and "\n" not in av:
            if render(av) == render(bv):
                continue
        differ += 1
        by[(an, bn)] = by.get((an, bn), 0) + 1
        if by[(an, bn)] <= 6:
            print("  %-22s %-20s %r -> %-20s %r"
                  % ("/".join(key), an, av, bn, bv))
    print("%d differ, %d subprocesses refused" % (differ, len(_spawned)))
    for (was, now), n in sorted(by.items(), key=lambda kv: -kv[1]):
        print("  %-24s -> %-24s %6d" % (was, now, n))
    return 1 if differ else 0


if __name__ == "__main__":
    sys.exit(main())
