# plover-stfst

A compiled dictionary from this repo, read by Plover.

Plover asks a dictionary two questions: what does this outline write, and what
writes this text. An artifact answers both, so the plugin translates vocabulary
and not machinery. A stroke becomes a key tape, a record becomes a Plover
translation, and the reverse automaton answers the second question that
`phrasing.py` answered with ten nested loops.

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

## What a dictionary answers

Three questions. What an outline writes, what writes some text, and what the
dictionary holds.

The third is the one a `StenoDictionary` answers out of `self._dict`, which an
artifact never fills. The automaton has the keyspace though, because the keyspace
is the language, so the plugin walks it on the first ask and holds the result:
145,314 entries for `typey-type`, in four seconds. Plover's dictionary editor and
`casereverse_lookup` read a dictionary this way, and so does `stenodrill`.

A dictionary that writes a syllable at a time holds infinitely many entries and
lists none. That is discovered on the walk, as a cycle, rather than declared.
The same fact takes its reverse automaton away: one word is written by
unboundedly many outlines there, so "which outlines write this" is a
cheapest-first search that costs the host 40 ms and answers with outlines longer
than the ones another dictionary already offers.

A lookup steps the cursor the last stroke left rather than walking from the
start. `docs/latency.md` has the measurements.

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
back. It also holds the listing to the lookup: what a dictionary lists is what it
answers for, and neither has an entry the other lacks. Every entry, none
differing. What it took is in `../docs/plover.md`: three of Plover's answers taken
over ours, two of them in `crates/stfst-text` rather than here.
