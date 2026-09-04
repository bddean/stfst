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

Every dictionary in `recipe.toml` except two, and each of those is refused by
name and by what it asked for rather than mistranslated:

```
retro-words  reads 16 words of what is already written
repeat-word  reads 1 bound words
```

Both need the host to say something *before* the answer, and Plover's dictionary
API has one argument: the outline. The manifest declares what each dictionary
asks for, so the refusal is one comparison and it happens before a stroke
arrives.

By ability and not by a version number. The manifest still carries a host
version, because that is what a host with no plugin of its own compares, but a
version is one number and these are separate abilities: this plugin holds a
stroke and shows a window of text nobody has written yet, and cannot show one of
text already written, which no single number says.

## Holding a stroke

Two kinds of record belong to the unit *after* their keys, so neither can be
written when those keys arrive. A **tail** is `[AFTER]`: text that follows the
next word, joined by the host's orthography. A **frame** reads that word as a
window and writes over it.

Plover asks one question, "what does this outline mean", and both answers depend
on what the next stroke means, which another dictionary owns. So a dictionary
here answers with `{:stfst_hold:…}`, a meta that writes nothing and carries what
the wait needs, and the extension writes the answer when a translation puts a
word on screen:

```
^*G      ->  {:stfst_hold:tail:\{:infl:ing\}}   nothing, "ing" waiting
RUPB     ->  run                                a word, so the tail lands
             running

STPR*T   ->  {:stfst_hold:frame:0:STPR*T}       nothing, a frame waiting
HAOER    ->  here                               a word, so the frame reads it
             from here to
```

A frame's own answer is not known until the window arrives, so its marker stands
for a *live* candidate rather than a match. That makes a dictionary of frames a
fallback, and a fallback in Plover means the **bottom** of the dictionary list: a
lower dictionary answers only where nothing above it did, which is what
`stfst-board` gets by asking for a held candidate after everything else.

`window.py` is what a frame needs beyond that: the span it is shown does not
reach the tape as characters but as the class each one belongs to, so the rule
never learns which letter it was and `Record.rewrite` applies Python's own casing
when it puts the answer back. `frame()` walks the context slots, then the keys,
then the window, which is one cursor from the start rather than the memoised walk
an ordinary lookup does: a frame is asked once per hold.

Two things about Plover shape the extension. `Translator._listeners` is a **set**,
so the hook may run before the formatter or after it: nothing may depend on
which, so the hook reads the translation's own text both for the marker and for
whether a word was written, rather than letting a meta arm the wait or asking
`Translation.formatting`, which may not exist yet. And what lands becomes part of
the word's own translation, by growing its `english` where the formatter has not
run and by taking the word back and writing it again where it has, so that one
`*` undoes both as it does in the Rust host.

**The extension has to be enabled in Plover's configuration**, like any other,
and `pip install -e` again after a change to the entry points.

`scripts/check-hold.py` is the check, and it drives a real translator: every case
of `tests/ahead`, `tests/hole` and `tests/frames` replayed through Plover and
compared against what `stfst translate` wrote for the same strokes. Those
fixtures list their artifacts in priority order, highest first, which is the
order Plover reads a collection in: a fixture that listed them any other way
would be asking the two hosts different questions, and one that did caught it.

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
| `plover_stfst/hold.py` | The `plover.meta` and `plover.extension` entry points: a stroke whose answer waits for the unit after it. |
| `plover_stfst/window.py` | Text as the tape carries it, for a frame: a class per character, and the characters beside them. |

## Checks

```sh
python3 -m unittest discover plover-plugin/tests   # the record language, the reader
scripts/crosscheck-py.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml
"$PLOVER_PYTHON" scripts/check-plugin.py           # every entry, judged by Plover
"$PLOVER_PYTHON" scripts/check-hold.py             # a held tail, through an engine
```

The last one is the one that matters. For every entry of every imported
dictionary in the stack, it renders what the plugin writes and what the original
JSON writes through Plover's own formatter and compares the two, and it asks the
reverse automaton for the outlines that write each record and requires the record
back. It also holds the listing to the lookup: what a dictionary lists is what it
answers for, and neither has an entry the other lacks. Every entry, none
differing. What it took is in `../docs/plover.md`: three of Plover's answers taken
over ours, two of them in `crates/stfst-text` rather than here.
