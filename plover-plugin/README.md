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

Every dictionary in `recipe.toml`. What is left to refuse is a carried state and
the `W1` context slot, and no dictionary in the stack asks for either; the
fixture that does is refused by what it asked for rather than by a version:

```
probe refused: reads a carried state, which Plover has no way to tell a dictionary
```

By ability and not by a host version. The manifest still carries a version,
because that is what a host with no plugin of its own compares, but a version is
one number and these are separate abilities.

## What a lookup cannot be told

Plover asks one question and passes one argument: the outline. So four things an
artifact can ask for have no way in, and each is a meta the dictionary answers
with instead. `plover_stfst/host.py` is all four and the extension they need:

| a dictionary asks for | the meta | when it writes |
|---|---|---|
| a bound word, `[COPY1]` | `stfst_copy` | at once: the word is behind the cursor |
| a window of written text, `words = 16` | `stfst_retro` | at once, over the words it rewrote |
| a window of the text after it, `next = 2` | `stfst_hold` then `stfst_frame` | when a unit writes a word |
| a tail, `[AFTER]` | `stfst_hold` then the tail itself | when a unit writes a word |

A window record is not text: its class glyphs stand for the characters of the
span, so `window.py` encodes the span the way the tape carries it and
`Record.rewrite` substitutes them back with Python's own casing. Both windowed
kinds take the words back with `prev_replace`, which is how Plover's own retro
metas rewrite what is written.

The last two wait, and that is what the extension is for. A tail and a frame
belong to the unit *after* their keys, and the answer depends on what the next
stroke means, which another dictionary owns:

```
^*G      ->  {:stfst_hold:tail:\{:infl:ing\}}   nothing, "ing" waiting
RUPB     ->  run                                a word, so the tail lands
             running

STPR*T   ->  {:stfst_hold:frame:0:STPR*T}       nothing, a frame waiting
HAOER    ->  here                               a word, so the frame reads it
             from here to
```

**The extension has to be enabled in Plover's configuration**, like any other,
and `pip install -e` again after a change to the entry points.

A windowed entry's own answer is unknown until the window arrives, so a lookup
can only say that the keys *begin* one. A dictionary that says so claims the
stroke, so **a windowed dictionary belongs at the bottom of `plover.cfg`**, where
it claims only what nothing above it wanted. That is the one place the two hosts
differ: `stfst-board` asks for a live candidate after everything else and lets
the stroke fall through where the rule turns out not to apply.

Two things about Plover shape the extension. `Translator._listeners` is a **set**,
so the hook may run before the formatter or after it: nothing may depend on
which, so the hook reads the translation's own text both for the marker and for
whether a word was written, rather than letting a meta arm the wait or asking
`Translation.formatting`, which may not exist yet. And what lands becomes part of
the word's own translation, by growing its `english` where the formatter has not
run and by taking the word back and writing it again where it has, so that one
`*` undoes both as it does in the Rust host.

`scripts/check-hold.py` is the check, and it drives a real translator: every case
of five fixtures replayed through Plover and compared against what `stfst
translate` wrote for the same strokes, 1,363 of them. Those fixtures list their
artifacts in priority order, highest first, which is the order Plover reads a
collection in: a fixture that listed them any other way would be asking the two
hosts different questions, and one that did caught it.

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
| `plover_stfst/host.py` | The `plover.meta` and `plover.extension` entry points: what a lookup cannot be told. |
| `plover_stfst/window.py` | Text as the tape carries it, for a frame: a class per character, and the characters beside them. |

## Checks

```sh
python3 -m unittest discover plover-plugin/tests   # the record language, the reader
scripts/crosscheck-py.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml
"$PLOVER_PYTHON" scripts/check-plugin.py           # every entry, judged by Plover
"$PLOVER_PYTHON" scripts/check-hold.py             # five fixtures, through an engine
```

The last one is the one that matters. For every entry of every imported
dictionary in the stack, it renders what the plugin writes and what the original
JSON writes through Plover's own formatter and compares the two, and it asks the
reverse automaton for the outlines that write each record and requires the record
back. It also holds the listing to the lookup: what a dictionary lists is what it
answers for, and neither has an entry the other lacks. Every entry, none
differing. What it took is in `../docs/plover.md`: three of Plover's answers taken
over ours, two of them in `crates/stfst-text` rather than here.
