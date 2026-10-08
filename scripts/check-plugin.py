#!/usr/bin/env python3
"""Hold the Plover plugin to Plover, entry by entry.

Two questions, one per direction, over every entry of every imported dictionary
in the stack:

  - Does what the plugin *writes* land where Plover's own translation lands?
    The artifact's record is spelled back as a Plover translation and both are
    rendered by Plover's formatter between "run" and "y", so both boundaries
    show. This is what catches an attachment or a spacing bug.

  - Does what the plugin *reads* agree with what the compiler imported? The
    Python reads the same Plover translation into a record, and the record has to
    be the one the artifact holds for that outline. This is half of reverse
    lookup: a disagreement here is a suggestion Plover never finds.

The other half, the reverse automaton, has no oracle and needs none: the two
directions are the same relation, so forward lookup is the check. A sample of
entries is asked which outlines write them, and each answer must write the record
back.

Run under Plover's own python, which is the oracle:

  /Applications/Plover.app/.../python3.13 scripts/check-plugin.py [artifact ...]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "plover-plugin"))

from plover_render import renderer

# Which source each artifact was imported from. Only an imported dictionary has
# an oracle here: a generated one has no Plover translation to compare against,
# and its own checker covers it.
def _sources():
    import tomllib
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    with open(os.path.join(root, "recipe.toml"), "rb") as f:
        return {d["name"]: os.path.join(root, d["json"])
                for d in tomllib.load(f)["dict"] if "json" in d and not d.get("editable")}


SOURCES = _sources()


def reserved_keys(name):
    """The Plover keys another dictionary owns, which `compile` leaves out of NAME."""
    import tomllib
    import recipe
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    with open(os.path.join(root, "recipe.toml"), "rb") as f:
        r = tomllib.load(f)
    with open(os.path.join(root, r["system"]), "rb") as f:
        keys = tomllib.load(f)["keys"]
    return {keys[k] for k in recipe.reserved(r, name)}


def check(dictionary, name, render):
    from plover.steno import Stroke, normalize_steno
    from plover_stfst.record import Record, Unsupported

    raw = json.load(open(SOURCES[name]))
    reserved = reserved_keys(name)
    raw = {o: t for o, t in raw.items()
           if not any(reserved & set(Stroke.from_steno(s).keys()) for s in normalize_steno(o))}
    # The longest key, which is the one thing Plover needs that a cursor makes
    # unnecessary here. The compiler reads it off the automaton and the manifest
    # carries it, so the source is the oracle.
    longest = max(len(normalize_steno(o)) for o in raw)
    bad = {"writes": [], "reads": [], "missing": [], "back": [], "listed": []}
    answered = set()
    if dictionary.longest_key != longest:
        bad["strokes"] = [(name, longest, dictionary.longest_key)]
    counted = 0
    # A spread of entries for the reverse check, which costs a search each.
    stride = max(1, len(raw) // 200)
    for at, (outline, translation) in enumerate(raw.items()):
        try:
            key = normalize_steno(outline)
        except ValueError:
            continue
        held = dictionary._record(key)
        try:
            want = Record.from_translation(translation)
        except Unsupported:
            # An entry the importer refuses by name, and `stfst import --report`
            # lists all of them. The artifact has to refuse it too.
            if held is not None:
                bad["reads"].append((outline, translation, "refused here, kept there"))
            continue
        if held is None:
            bad["missing"].append((outline, translation, ""))
            continue
        counted += 1
        if str(held) != str(want):
            bad["reads"].append((outline, str(want), str(held)))
        try:
            spelled = held.translation()
        except Unsupported as e:
            bad["writes"].append((outline, translation, "no spelling: %s" % e))
            continue
        answered.add(key)
        # A newline cannot be compared a line at a time, and the golden files
        # already cover the entries that hold one.
        if spelled != translation and "\n" not in translation:
            ours, theirs = render(spelled), render(translation)
            if ours != theirs:
                bad["writes"].append((outline, "%r -> %r" % (translation, theirs),
                                      "%r -> %r" % (spelled, ours)))
        if at % stride == 0:
            bad["back"].extend(_back(dictionary, key, spelled, held))
    # A dictionary lists what it answers for. Plover's dictionary editor reads a
    # dictionary as a set of entries, and so does anything else that wants to
    # know what is in one rather than what one outline writes.
    listed = set(dictionary)
    for key in sorted(listed - answered):
        bad["listed"].append(("/".join(key), "an entry", "listed, answers nothing"))
    for key in sorted(answered - listed):
        bad["listed"].append(("/".join(key), "listed", "answers but is not listed"))
    return counted, bad


def _back(dictionary, key, spelled, held):
    """Every outline the reverse automaton offers has to write the record back.

    Ranking is checked where the answer is known: this record has a one-stroke
    outline, and nothing is shorter than one stroke.
    """
    from plover_stfst.record import Record
    out = []
    got = dictionary.reverse_lookup(spelled)
    for offered in got:
        wrote = dictionary._record(offered)
        if wrote is None or str(wrote) != str(held):
            out.append(("/".join(offered), str(held),
                        "writes %s" % (wrote and str(wrote))))
    if len(key) == 1 and got and len(got[0]) > 1:
        out.append(("/".join(key), "one stroke", "offered /".join(got[0])))
    return out


def main(paths):
    import plover.system as system
    from plover.registry import registry
    from plover.dictionary.base import load_dictionary
    from plover_stfst.dictionary import StfstDictionary
    registry.update()
    # What the entry point does, done by hand: the artifact is then loaded the
    # way Plover loads it, by extension, without installing anything.
    registry.register_plugin("dictionary", "stfst", StfstDictionary)
    system.setup("Stenotype Extended")
    # A keystroke and an engine command are not part of this comparison: both
    # sides of it write the same one, and only the text is in question.
    render = renderer()

    # Which artifacts Plover can host at all. A dictionary that needs something
    # Plover cannot say is refused by name and with a reason, which is the answer
    # and not a failure.
    loaded = {}
    for path in paths:
        name = os.path.basename(path)[:-len(".stfst")]
        try:
            loaded[name] = load_dictionary(path)
            print("%-20s loads, longest key %d" % (name, loaded[name].longest_key))
        except Exception as e:
            print("%-20s unavailable: %s" % (name, e))

    failed = 0
    for name, dictionary in sorted(loaded.items()):
        if name not in SOURCES:
            continue
        counted, bad = check(dictionary, name, render)
        total = sum(len(v) for v in bad.values())
        print("%-20s %7d entries, %d differ" % (name, counted, total))
        for kind, cases in bad.items():
            for outline, want, got in cases[:5]:
                print("  %s %s: want %s, got %s" % (kind, outline, want, got))
            if len(cases) > 5:
                print("  %s: %d more" % (kind, len(cases) - 5))
        failed += total
    return 1 if failed else 0


if __name__ == "__main__":
    import glob
    root = os.path.join(os.path.dirname(__file__), "..", "build", "dicts")
    sys.exit(main(sys.argv[1:] or sorted(glob.glob(os.path.join(root, "*.stfst")))))
