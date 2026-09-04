#!/usr/bin/env python3
"""Does a held tail land in Plover where `stfst translate` puts it?

`[AFTER]` is the one mechanism whose Plover half is not a dictionary. The tail
leaves the artifact inside a meta and `plover_stfst.hold` writes it when a
translation puts a word on screen, so what has to be checked is a whole engine
and not one lookup: a real translator, the real artifacts, the meta and the
extension wired the way the entry points wire them.

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


def cases(strokes_path, expected_path):
    """Each case as its strokes and the text the Rust host ended with.

    A case is a `!buffer` line and what follows it, in both files, so the two
    split the same way and nothing has to count lines. Only an empty buffer:
    Plover cannot start from text nobody stroked, so a fixture that seeds one is
    not a question this can ask.
    """
    strokes = []
    for line in open(strokes_path):
        line = line.strip()
        if line.startswith(BUFFER):
            if line != BUFFER:
                raise SystemExit("%s: a seeded buffer is not a question Plover "
                                 "can be asked" % strokes_path)
            strokes.append([])
            continue
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
    return [(s, t) for s, t in zip(strokes, texts) if s]


def engine_for(translator):
    """As much engine as the extension asks for.

    The hook it connects to is a translator listener here. Which order the
    formatter and this run in is Plover's business and a set's, which is why
    nothing in `hold` depends on it.
    """
    return type("Engine", (), {
        "translator": translator,
        "hook_connect": lambda s, name, fn: translator.add_listener(
            lambda undo, do, prev: fn(undo, do)),
        "hook_disconnect": lambda s, name, fn: None,
    })()


def main(paths):
    import plover.system as system
    from plover.registry import registry
    from plover.dictionary.base import load_dictionary
    from plover.formatting import Formatter
    from plover.steno import Stroke, normalize_steno
    from plover.steno_dictionary import StenoDictionaryCollection
    from plover.translation import Translator
    from plover_stfst.dictionary import StfstDictionary
    from plover_stfst.hold import Ahead, stfst_frame, stfst_hold, waiting

    registry.update()
    # What the entry points do, done by hand, so nothing has to be installed.
    registry.register_plugin("dictionary", "stfst", StfstDictionary)
    registry.register_plugin("meta", "stfst_hold", stfst_hold)
    registry.register_plugin("meta", "stfst_frame", stfst_frame)
    system.setup("Stenotype Extended")

    bad = total = 0
    for fixture in paths:
        # In priority order, highest first, which is the order Plover reads a
        # collection in and the order `Stack::open` sorts a recipe into. A
        # fixture that listed them any other way would be asking the two hosts
        # different questions.
        listed = os.path.join(os.path.dirname(fixture), "artifacts")
        dicts = [load_dictionary(os.path.join(ROOT, p.strip()))
                 for p in open(listed) if p.strip()]
        expected = fixture[:-len(".strokes")] + ".expected"
        for strokes, want in cases(fixture, expected):
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
            waiting.clear()
            Ahead(engine_for(translator)).start()
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
            print("%s: %s\n  plover %r\n  stfst  %r"
                  % (os.path.basename(fixture), "/".join(strokes), got, want))
    print("%d cases, %d differ" % (total, bad))
    return 1 if bad else 0


if __name__ == "__main__":
    args = sys.argv[1:] or [f for d in ("ahead", "hole", "frames")
                            for f in sorted(glob.glob(
                                os.path.join(ROOT, "tests", d, "*.strokes")))]
    sys.exit(main(args))
