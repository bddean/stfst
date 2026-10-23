#!/usr/bin/env python3
"""Compare what this host writes against what Plover writes, translation by translation.

The golden files check that a compiled artifact still says what the importer said.
They cannot catch the importer and the formatter agreeing on the wrong thing, and
they did not: `{^}` was read as attaching on both sides, so 516 typey-type entries
joined leftward when Plover joins rightward, and the golden file had been
regenerated from the same mistake.

So this checks the only thing that settles it. Each translation is written between
"run" and "y", in Plover and here, and the two strings must match. Both boundaries
are visible that way, which is what an attachment bug shows up in.

  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/check-translate.py [dictionary.json ...]
"""
import json
import os
import subprocess
import sys

BINARY = "./target/release/stfst"
DEFAULT = ["commands.json", "user.json", "phrases.json", "semantic-suffixes.json",
           "typey-type-full.json"]


def plover_output(translations):
    import plover.formatting as f
    from plover.steno import Stroke
    from plover.translation import Translation

    out = []
    for text in translations:
        # A real document, because Plover rewrites by deleting: `{*<}` sends four
        # backspaces and then "RUN". Ignoring the backspaces would show both the old
        # word and the new one and read as a difference that is not there.
        buf = [""]

        def erase(n, buf=buf):
            buf[0] = buf[0][:len(buf[0]) - n]

        fm = f.Formatter()
        fm.set_output(type("O", (), {
            "send_backspaces": lambda s, n: erase(n),
            "send_string": lambda s, x: buf.__setitem__(0, buf[0] + x),
            # A keystroke and an engine command are not text, so they are not part
            # of the comparison: what is compared is what lands in the document.
            "send_key_combination": lambda s, x: None,
            "send_engine_command": lambda s, x: None,
        })())
        seen = []
        for t in ["run", text, "y"]:
            tr = Translation([Stroke.from_steno("TEFT")], t)
            fm.format([], [tr], seen)
            seen.append(tr)
        out.append(buf[0])
    return out


def main(paths):
    import plover.system as system
    from plover.registry import registry
    registry.update()
    system.setup("English Stenotype")
    # A join that makes no word completes to one in both hosts, and Plover's half
    # of that is the plugin's.
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "plover-plugin"))
    from plover_stfst import morph
    morph.install()

    translations = []
    for path in paths:
        raw = json.load(open(os.path.join(os.path.dirname(__file__), "..", "dictionaries", path)))
        translations.extend(raw.values())
    # One line each, so a translation holding a newline cannot be compared this way.
    # Those are exactly the entries the escape convention added, and they are checked
    # by the golden files instead.
    translations = [t for t in translations if "\n" not in t and "\r" not in t]

    run = subprocess.run([BINARY, "xlate", "--translations", "/dev/stdin"],
                         input="\n".join(translations) + "\n",
                         capture_output=True, text=True)
    if run.returncode != 0:
        print(run.stderr.strip(), file=sys.stderr)
        return 1
    ours = run.stdout.splitlines()
    theirs = plover_output(translations)

    # A translation this format has no meaning for is reported, not compared: the
    # importer already refuses it by name and `docs/parity.md` says which.
    pairs = [(t, a, b) for t, a, b in zip(translations, ours, theirs)
             if not a.startswith("!")]
    refused = len(translations) - len(pairs)
    bad = [(t, a, b) for t, a, b in pairs if a.strip() != b.strip()]
    print("%d translations, %d refused, %d of %d differ"
          % (len(translations), refused, len(bad), len(pairs)))
    for t, a, b in bad[:20]:
        print("  %r: plover %r, ours %r" % (t, b, a))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or DEFAULT))
