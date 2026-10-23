#!/usr/bin/env python3
"""Does Plover write what `stfst translate` writes, where a lookup is not enough?

A held tail and a fold are the two mechanisms whose Plover half is not a
dictionary. A tail leaves the artifact inside a meta and `plover_stfst.host`
writes it when a translation puts a word on screen, and a fold is a step of the
translator's own. So what has to be checked is a whole engine and not one
lookup: a real translator, the real artifacts, the meta and the extension wired
the way the entry points wire them.

The questions are the fixture's own. `tests/ahead/*.strokes` is a stroke stream
with the Rust host's answer beside it, so this replays it through Plover and
compares the text at the end of each case. Two hosts, one answer.

Run under Plover's own python, which is the oracle:

  /Applications/Plover.app/.../python3.13 scripts/check-hold.py [fixture ...]
"""
import glob
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(ROOT, "plover-plugin"))

BUFFER, UNDO = "!buffer", "!undo"

# Every fixture whose answer depends on something a lookup cannot be told. Named
# one by one rather than globbed, because `tests/retro-words/reach.strokes` is
# not a question both hosts can be asked: it asserts which strokes have no entry,
# and Plover writes the raw steno for one where `stfst-board` writes nothing.
DEFAULT = [
    "tests/ahead/ahead.strokes",
    "tests/folds/folds.strokes",
    "tests/frames/frames.strokes",
    "tests/hole/article.strokes",
    "tests/hole/hole.strokes",
    "tests/repeat-word/repeat-word.strokes",
    "tests/retro-words/ops.strokes",
    "tests/translate/complete.strokes",
]


def cases(strokes_path, expected_path):
    """Each case as its seed, its strokes, and the text the Rust host ended with.

    A case is a `!buffer` line and what follows it, in both files, so the two
    split the same way and nothing has to count lines. A buffer that names text
    is text nobody stroked, which Plover has no `seed` for: what it does have is
    a translation, so the seed goes in as one.
    """
    strokes, seeds = [], []
    for line in open(strokes_path):
        line = line.rstrip()
        if line.startswith(BUFFER):
            strokes.append([])
            seeds.append(line[len(BUFFER):].strip())
            continue
        line = line.strip()
        if not line or (line.startswith("!") and line != UNDO):
            continue
        if strokes:
            strokes[-1].append(line)
    texts = []
    for line in open(expected_path):
        parts = line.rstrip("\n").split("\t")
        if line.startswith(BUFFER):
            texts.append("")
            continue
        if texts and len(parts) > 1 and parts[-1].startswith('"'):
            # The text column is `{:?}` from Rust, so a quote in the text is
            # escaped and a backslash doubled.
            texts[-1] = (parts[-1][1:-1].replace('\\"', '"')
                         .replace("\\\\", "\\"))
    return [(seed, s, t) for seed, s, t in zip(seeds, strokes, texts) if s]


def engine_for(translator):
    """As much engine as the extension asks for, spelled as Plover spells it.

    One attribute, because the extension listens to the translator itself:
    Plover's `translated` hook is a formatter listener and is passed actions,
    and a hold is read off a translation. Which order the formatter and this
    run in is a set's business, which is why nothing in `host` depends on it.
    """
    return type("Engine", (), {"_translator": translator})()


def main(paths):
    import plover.system as system
    from plover.registry import registry
    from plover.dictionary.base import load_dictionary
    from plover.formatting import Formatter
    from plover.steno import Stroke, normalize_steno
    from plover.steno_dictionary import StenoDictionaryCollection
    from plover.translation import Translation, Translator
    from plover_stfst.dictionary import StfstDictionary
    from plover_stfst import host
    from plover_stfst.extension import Extension

    registry.update()
    # What the entry points do, done by hand, so nothing has to be installed.
    registry.register_plugin("dictionary", "stfst", StfstDictionary)
    for meta in ("stfst_copy", "stfst_retro", "stfst_hold", "stfst_frame",
                 "stfst_drop"):
        registry.register_plugin("meta", meta, getattr(host, meta))
    system.setup("Stenotype Extended")

    bad = total = 0
    for fixture in paths:
        # In priority order, highest first, which is the order Plover reads a
        # collection in and the order `Stack::open` sorts a recipe into. A
        # fixture that listed them any other way would be asking the two hosts
        # different questions.
        listed = os.path.join(os.path.dirname(fixture), "artifacts")
        dicts = [load_dictionary(found)
                 for p in open(listed) if p.strip()
                 for found in sorted(glob.glob(os.path.join(ROOT, p.strip())))]
        expected = fixture[:-len(".strokes")] + ".expected"
        for seed, strokes, want in cases(fixture, expected):
            buf = [""]
            out = type("Output", (), {
                "send_backspaces":
                    lambda s, n: buf.__setitem__(0, buf[0][:len(buf[0]) - n]),
                "send_string": lambda s, x: buf.__setitem__(0, buf[0] + x),
                "send_key_combination": lambda s, x: None,
                "send_engine_command": lambda s, x: None,
            })()
            formatter = Formatter()
            formatter.set_output(out)
            translator = Translator()
            translator.set_dictionary(StenoDictionaryCollection(list(dicts)))
            translator.add_listener(formatter.format)
            Extension(engine_for(translator)).start()
            if seed:
                # Text the fixture starts from. A translation of its own, so a
                # window reads it the way it reads anything else written.
                translator.translate_translation(
                    Translation([Stroke.from_steno("TEFT")], seed))
                translator.flush()
            for steno in strokes:
                keys = "*" if steno == UNDO else normalize_steno(steno)[0]
                translator.translate_stroke(Stroke.from_steno(keys))
                # Plover tells its listeners on the flush, which the engine does
                # once per stroke.
                translator.flush()
            got = buf[0].strip()
            total += 1
            if got == want.strip():
                continue
            bad += 1
            print("%s: %s%s\n  plover %r\n  stfst  %r"
                  % (os.path.basename(fixture), "%r " % seed if seed else "",
                     "/".join(strokes), got, want))
    print("%d cases, %d differ" % (total, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    args = sys.argv[1:] or DEFAULT
    sys.exit(main([os.path.join(ROOT, p) if not os.path.isabs(p) else p
                   for p in args]))
