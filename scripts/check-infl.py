#!/usr/bin/env python3
"""Hold `{:infl:…}` to the lexicon it was compiled from.

The artifact holds only the pairs where lemminflect disagrees with Plover's
`add_suffix`, and the host falls back to the rules for the rest. That is a filter
across three moving parts: the generator's choice of form, lexc's escaping, and
the host's fallback. This checks the composition rather than any one of them, by
asking the generator what it meant and the binary what it does.

  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/check-infl.py
"""
import importlib.util
import subprocess
import sys

BINARY = "./target/release/stfst"
MODES = ["ed", "ing", "s"]


def generator():
    spec = importlib.util.spec_from_file_location("gen_morph", "scripts/gen-morph.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main(limit=20000):
    import plover.system as system
    from plover.registry import registry
    from plover.orthography import add_suffix
    import plover_semantic_suffixes as tables
    from lemminflect import getAllLemmas, getInflection

    registry.update()
    system.setup("English Stenotype")
    gen = generator()

    vocabulary = sorted({w.lower() for w in system.ORTHOGRAPHY_WORDS})[:limit]
    pairs, want = [], []
    for word in vocabulary:
        for mode in MODES:
            form = gen.target_form(word, mode, tables, getAllLemmas, getInflection)
            if form is None or " " in form:
                # No lexicon entry, so the rules answer, and the rules are already
                # measured against Plover exactly. Still worth sending: a spurious
                # entry would show up here as a difference.
                form = add_suffix(word, mode)
            pairs.append((word, mode))
            want.append(form)

    run = subprocess.run([BINARY, "infl", "--pairs", "/dev/stdin"],
                         input="\n".join("%s\t%s" % p for p in pairs) + "\n",
                         capture_output=True, text=True)
    if run.returncode != 0:
        print(run.stderr.strip(), file=sys.stderr)
        return 1
    got = run.stdout.splitlines()

    bad = [(p, a, b) for p, a, b in zip(pairs, got, want) if a != b]
    print("%d inflections, %d differ" % (len(pairs), len(bad)))
    for (word, mode), a, b in bad[:15]:
        print("  %s + %s: lexicon %r, ours %r" % (word, mode, b, a))

    # The cases this exists for, named rather than left to a sample.
    for word, mode, form in [("run", "ed", "ran"), ("running", "ed", "ran"),
                             ("child", "s", "children"), ("foot", "s", "feet"),
                             ("is", "ed", "was"), ("sheep", "s", "sheep"),
                             ("walk", "ed", "walked"), ("carry", "s", "carries"),
                             ("i", "z", "my"), ("boss", "z", "boss'")]:
        run = subprocess.run([BINARY, "infl", "--pairs", "/dev/stdin"],
                             input="%s\t%s\n" % (word, mode),
                             capture_output=True, text=True)
        got = run.stdout.strip()
        print("  %-9s %-4s %-10s %s" % (word, mode, got, "ok" if got == form else "WANT " + form))
        if got != form:
            bad.append(((word, mode), got, form))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(int(sys.argv[1]) if len(sys.argv) > 1 else 20000))
