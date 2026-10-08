#!/usr/bin/env python3
"""Write `dictionaries/typey-composed.json`: the Typey Type entries whose text
the rest of the stack writes stroke by stroke without them.

`STAR/-D` is starred and `RAOE/HROEBGTS` relocates, but so are star then `-D` and
{re^} then locates, and the folded `HOFTS` is host then `-S`. Such an entry says
nothing the rules do not, so `gen-typey-vowels.py` leaves it out of
`typey-type-full.json`, and the entries it left out are this file.

An entry is composed when, with every composed entry gone, each Plover system
writes the same text for its strokes after "run", "I", "the" and nothing, and
before "y". Plover is the oracle, the real translator and formatter over the real
stacks of `plover.cfg`, because a fold's meaning is `plover-semantic-suffixes`'s
and not a rule here. The Rust host is asked too, over the compiled stack with
Typey Type as an editable map, because its inflection is a lexicon and not that
plugin, and the two disagree on a few words (still lifes, snobs).

One more condition, because an entry also decides where a translation ends: no
later part of its outline may begin a longer outline in either stack. Without
`A/B`, `A` then `B/C` would be read where `A/B` then `C` was.

Run under Plover's own python, after `gen-typey-vowels.py`:

  /Applications/Plover.app/.../python3.13 scripts/find-typey-composed.py
  /Applications/Plover.app/.../python3.13 scripts/find-typey-composed.py --check

then run `gen-typey-vowels.py` again, which drops what this file lists.
"""
import argparse
import configparser
import contextlib
import glob
import json
import os
import re
import subprocess
import sys
import tempfile
import tomllib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, os.path.join(ROOT, "plover-plugin"))
PLOVER = os.path.expanduser("~/.config/plover")
# Where Plover installs a user plugin: the Python dictionary loader is one.
sys.path.append(os.path.join(PLOVER, "plugins/mac/lib/python/site-packages"))
FULL = os.path.join(ROOT, "dictionaries", "typey-type-full.json")
COMPOSED = os.path.join(ROOT, "dictionaries", "typey-composed.json")
SYSTEMS = ("English Stenotype", "Stenotype Extended")

# What each stroke is written after. Enough to show an attachment, a pending
# capital, and a fold that reads the word before it.
CONTEXTS = ("run", "I", "the", "")

# Keys Plover folds in both systems. An entry of one stroke without one of these
# cannot be composed, so it is not asked about.
SUFFIX_KEYS = "SDZG"

# The suffix keys of `dictionaries/folds.xfst`, which is where Stenotype
# Extended's folds are: that system declares no `SUFFIX_KEYS` of its own.
EXTENDED_SUFFIXES = ("-Z", "-D", "-S", "-G", "-T")

# A number stroke Plover writes as its digits when nothing defines it, and the
# Rust host writes nothing, so an entry holding one is not composed there.
NUMBER = re.compile(r"[0-9#]")


def typey():
    """Typey Type as the vowel generator wrote it, the composed entries put back."""
    with open(FULL) as f:
        full = json.load(f)
    if os.path.exists(COMPOSED):
        with open(COMPOSED) as f:
            full.update(json.load(f))
    return full


def stack(system_name):
    """[(name, path)] for a system's enabled dictionaries, highest first, with
    typey-type as its JSON so an entry can be taken out."""
    cfg = configparser.RawConfigParser()
    cfg.read(os.path.join(PLOVER, "plover.cfg"))
    out = []
    for e in json.loads(cfg[f"System: {system_name}"]["dictionaries"]):
        if not e["enabled"]:
            continue
        path = os.path.join(PLOVER, os.path.expanduser(e["path"]))
        name = os.path.basename(path).rsplit(".", 1)[0]
        out.append((name, FULL if name in ("typey-type", "typey-type-full") else path))
    return out


def outlines_listed():
    """Every outline a source in either stack lists, as stroke tuples: the JSON
    dictionaries, the flat ones, and the affix grammar's golden file."""
    from plover.steno import normalize_steno
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import plover_tape
    system = plover_tape.System.load(os.path.join(ROOT, "systems/stenotype-extended.toml"))
    paths = {os.path.abspath(p) for s in SYSTEMS for _, p in stack(s) if p.endswith(".json")}
    with open(os.path.join(ROOT, "recipe.toml"), "rb") as f:
        recipe = tomllib.load(f)
    paths |= {os.path.abspath(os.path.join(ROOT, d["json"])) for d in recipe["dict"] if "json" in d}
    outlines = set(typey())
    for p in paths:
        with open(p) as f:
            outlines |= set(json.load(f))
    for d in recipe["dict"]:
        if "plain" in d:
            with open(os.path.join(ROOT, d["plain"])) as f:
                outlines |= {line.split("\t")[0] for line in f
                             if line.strip() and not line.startswith("!")}
    with open(os.path.join(ROOT, "tests/golden/affixes.tsv")) as f:
        outlines |= {system.outline(line.split("\t")[0]) for line in f if not line.startswith("!")}
    out = set()
    for o in outlines:
        try:
            out.add(normalize_steno(o))
        except ValueError:
            pass
    return out


def broken_in(system_name, removed, alone):
    """Those of `removed` ({outline: translation}) whose strokes this system
    writes differently once all of `removed` is gone, or with `alone`, once that
    one entry is."""
    import plover.system as system
    from plover.dictionary.base import load_dictionary
    from plover.formatting import Formatter
    from plover.registry import registry
    from plover.steno import Stroke, normalize_steno
    from plover.steno_dictionary import StenoDictionaryCollection
    from plover.translation import Translation, Translator
    from plover_stfst import fold, host
    from plover_stfst.dictionary import StfstDictionary

    registry.update()
    registry.register_plugin("dictionary", "stfst", StfstDictionary)
    for meta in ("stfst_copy", "stfst_retro", "stfst_hold", "stfst_frame", "stfst_drop"):
        registry.register_plugin("meta", meta, getattr(host, meta))
    system.setup(system_name)

    dicts = []
    for name, path in stack(system_name):
        try:
            # A Python dictionary may print as it loads, and stdout is the answer.
            with contextlib.redirect_stdout(sys.stderr):
                d = load_dictionary(path)
        except Exception as e:
            print(f"{system_name}: {name} unavailable: {e}", file=sys.stderr)
            continue
        if path == FULL:
            entries = d
            for o, t in typey().items():
                entries[normalize_steno(o)] = t
        dicts.append(d)
    collection = StenoDictionaryCollection(dicts)

    def texts(strokes):
        out = []
        for before in CONTEXTS:
            buf = [""]
            sink = type("Output", (), {
                "send_backspaces": lambda s, n: buf.__setitem__(0, buf[0][:len(buf[0]) - n]),
                "send_string": lambda s, x: buf.__setitem__(0, buf[0] + x),
                "send_key_combination": lambda s, x: None,
                "send_engine_command": lambda s, x: None,
            })()
            formatter = Formatter()
            formatter.set_output(sink)
            translator = Translator()
            translator.set_dictionary(collection)
            translator.add_listener(formatter.format)
            fold.install(translator)
            if before:
                translator.translate_translation(Translation([Stroke.from_steno("TEFT")], before))
            for s in strokes:
                translator.translate_stroke(Stroke.from_steno(s))
            translator.translate_translation(Translation([Stroke.from_steno("TEFT")], "y"))
            translator.flush()
            out.append(buf[0])
        return out

    inflecting = [Stroke.from_steno("-" + k) for k in SUFFIX_KEYS]
    gerund, past = Stroke.from_steno("-G"), Stroke.from_steno("-D")

    extended = system_name == "Stenotype Extended"
    suffixes = [Stroke.from_steno(k)
                for k in (EXTENDED_SUFFIXES if extended else system.SUFFIX_KEYS)]

    def asked(key):
        """The entry's strokes, and each with a suffix key folded onto the last,
        since a fold reads the entry as its base. Onto a word that is already an
        inflection only a key that does not inflect is asked: sheared then -S is
        a double inflection nobody strokes, and sheared then -T is "sheared the".
        A gerund is a noun too, so -S and -Z are asked of it: buildings.
        Onto a stroke of suffix keys alone, `-G` in `STAR/-G`, none is."""
        last = Stroke.from_steno(key[-1])
        keys = [k for k in suffixes if k not in last]
        if all(any(k in s for s in suffixes) for k in last.keys()):
            keys = []
        elif any(k in last for k in inflecting if k != gerund):
            keys = [k for k in keys if k not in inflecting]
        elif gerund in last:
            keys = [k for k in keys if k not in (past, gerund)]
        return [key] + [key[:-1] + ((last + k).rtfcre,) for k in keys]

    def answers(key):
        try:
            return [texts(strokes) for strokes in asked(key)]
        except Exception:
            return None

    keys = {o: normalize_steno(o) for o in removed}
    rust = set()
    if system_name == "Stenotype Extended" and not alone:
        rust = rust_broken({o: asked(k) for o, k in keys.items()})
    want = {o: answers(k) for o, k in keys.items()}
    if not alone:
        for k in keys.values():
            del entries[k]
    broken = sorted(rust)
    for o, k in keys.items():
        if o in rust:
            continue
        if alone:
            del entries[k]
        if want[o] is None or answers(k) != want[o]:
            broken.append(o)
        if alone:
            entries[k] = removed[o]
    return broken


def rust_broken(asked):
    """Those of `asked` ({outline: [strokes]}) whose strokes `stfst translate`
    writes differently once all of them are gone from Typey Type."""
    full = typey()
    artifacts = sorted(glob.glob(os.path.join(ROOT, "build/dicts/*.stfst")))
    artifacts = [a for a in artifacts if os.path.basename(a) != "typey-type.stfst"]
    cases = [(o, strokes) for o, seqs in asked.items() for strokes in seqs]
    stream = "".join(f"!buffer {before}\n" + "".join(s + "\n" for s in strokes)
                     for _, strokes in cases for before in CONTEXTS)

    def texts(entries):
        with tempfile.TemporaryDirectory() as d:
            with open(os.path.join(d, "typey.json"), "w") as f:
                json.dump(entries, f)
            with open(os.path.join(d, "editable.toml"), "w") as f:
                f.write('[[dict]]\nname = "typey-type"\nfile = "typey.json"\npriority = 30\n')
            res = subprocess.run([os.path.join(ROOT, "target/release/stfst"), "translate", *artifacts,
                                  "--system", os.path.join(ROOT, "systems/stenotype-extended.toml"),
                                  "--morph", os.path.join(ROOT, "build/morph"), "--editable", d],
                                 input=stream, capture_output=True, text=True, check=True)
        # The text after each case is its last line's last column.
        out = []
        for line in res.stdout.splitlines():
            if line.startswith("!buffer"):
                out.append(None)
            elif out and not line.startswith("!"):
                out[-1] = line.split("\t")[-1]
        return out

    before = texts(full)
    after = texts({o: t for o, t in full.items() if o not in asked})
    n = len(CONTEXTS)
    return {cases[i // n][0] for i in range(len(before)) if before[i] != after[i]}


def candidates():
    """{outline: translation} of the entries that could be composed and whose
    later strokes begin no outline."""
    from plover.steno import normalize_steno
    starts = {o[:i] for o in outlines_listed() for i in range(1, len(o))}
    out = {}
    for o, t in typey().items():
        key = normalize_steno(o)
        if NUMBER.search(o) or len(key) == 1 and key[0][-1] not in SUFFIX_KEYS:
            continue
        if any(key[i:] in starts for i in range(1, len(key))):
            continue
        out[o] = t
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true",
                    help="fail if an entry typey-composed.json lists no longer composes")
    ap.add_argument("--system", help=argparse.SUPPRESS)
    ap.add_argument("--alone", action="store_true", help=argparse.SUPPRESS)
    args = ap.parse_args()

    if args.system:
        # One system per process: Plover's system is global.
        json.dump(broken_in(args.system, json.load(sys.stdin), args.alone), sys.stdout)
        return

    import plover.system as system
    from plover.registry import registry
    registry.update()
    system.setup("English Stenotype")

    def broken(removed, alone):
        out = set()
        for name in SYSTEMS:
            res = subprocess.run([sys.executable, __file__, "--system", name] + ["--alone"] * alone,
                                 input=json.dumps(removed), capture_output=True, text=True, check=True)
            out |= set(json.loads(res.stdout))
        print(f"{len(out)} of {len(removed)} write differently{' alone' * alone}", file=sys.stderr)
        return {o: t for o, t in removed.items() if o not in out}, sorted(out)

    if args.check:
        # Whether what left still writes what it did. Finding more is the slow
        # half, and a missed one costs an entry, not a word.
        with open(COMPOSED) as f:
            _, bad = broken(json.load(f), False)
        if bad:
            sys.exit(f"{len(bad)} entries of {COMPOSED} no longer compose, {bad[:5]}: "
                     f"run {sys.argv[0]}, then gen-typey-vowels.py")
        return

    # Each alone first, then the survivors together, since taking one out can
    # change what another's strokes write. What breaks is put back until nothing
    # does.
    removed, _ = broken(candidates(), True)
    bad = True
    while bad:
        removed, bad = broken(removed, False)
    with open(COMPOSED, "w") as f:
        f.write("{\n" + ",\n".join(f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}"
                                    for k, v in sorted(removed.items())) + "\n}\n")
    print(f"{len(removed)} composed in both systems")


if __name__ == "__main__":
    main()
