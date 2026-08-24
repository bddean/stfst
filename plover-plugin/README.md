# plover-stfst

A compiled dictionary from this repo, read by Plover.

Plover asks a dictionary two questions: what does this outline write, and what
writes this text. An artifact answers both, so the plugin translates vocabulary
and not machinery. A stroke becomes a key tape, a record becomes a Plover
translation, and the reverse automaton answers the second question that
`phrasing.py` answers today with ten nested loops.

This is M1 of `FST-DICT-DESIGN.md`, and its point is the format rather than the
speed: one plugin per engine, and then every dictionary this repo ever compiles
works there. What it costs and what it cannot do are in `../docs/plover.md`.

## Install

Install into the interpreter Plover runs, which is Plover's own:

```sh
/Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \
    -m pip install -e plover-plugin
```

Then name an artifact in `plover.cfg`, under the system it was compiled for. The
extension picks the format, and an artifact is a directory:

```
dictionaries = [..., {"enabled": true, "path": "~/src/stfst/build/dicts/typey-type.stfst"}]
```

Order is priority, as it is for every other Plover dictionary, so the list in
`plover.cfg` and the priorities in `recipe.toml` say the same thing.

## What loads

Eleven of the thirteen dictionaries in `recipe.toml`. Two do not, and they are
refused by name at load rather than mistranslated:

```
retro-words   needs host 2: it reads 16 words of what is already written
repeat-word   needs host 2: it reads 1 bound words
```

A dictionary that reads a context slot, carries a state, splices a bound word or
rewrites a window needs the host to say something on every stroke, and Plover's
dictionary API has no way to say it. The manifest declares each of those, so the
refusal is one comparison and it happens before a stroke arrives.

## Layout

| | |
|---|---|
| `plover_stfst/ol.py` | The `hfstol` reader: mapped, no decode, no dependency. A second implementation of `crates/stfst-ol`. |
| `plover_stfst/record.py` | The record language and Plover's translation language, each read as the other. Needs no Plover, because the golden generators use it as an oracle. |
| `plover_stfst/dictionary.py` | The `plover.dictionary` entry point. |

## Checks

```sh
python3 -m unittest discover plover-plugin/tests   # the record language, the reader
scripts/crosscheck-py.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml
"$PLOVER_PYTHON" scripts/check-plugin.py           # every entry, judged by Plover
```

The last one is the one that matters. For every entry of every imported
dictionary in the stack, it renders what the plugin writes and what the original
JSON writes through Plover's own formatter and compares the two, and it asks the
reverse automaton for the outlines that write each record and requires the record
back. 147,188 entries, one difference, named in `../docs/plover.md`.
