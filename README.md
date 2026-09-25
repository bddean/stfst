# stfst

Steno dictionaries compiled to finite-state transducers, with a compiler, a
dictionary set, and a headless CLI for testing. Design in
`~/.config/plover/FST-DICT-DESIGN.md`. This repo diverges from §3 of it on the
upper tape and on the record language, recorded in `docs/key-tape.md`.

## Getting started

```sh
direnv allow          # or: nix develop
stfst compile recipe.toml
stfst compile tests/recipe.toml --out build/test-dicts --work build/test-work
scripts/build-morph.sh   # orthography and inflection; needs Plover installed
scripts/check.sh      # unit tests, then the golden files
stfst bench build/dicts/typey-type.stfst --system systems/stenotype-extended.toml
stfst translate build/dicts/*.stfst --system systems/stenotype-extended.toml
stfst reverse build/dicts/typey-type.stfst --system systems/stenotype-extended.toml because
```

HFST and foma come from the flake and are needed only to *build* a dictionary.
Nothing at runtime links them, which matters because HFST is GPLv3 and the
eventual consumer is an Android keyboard. `stfst-ol` reads the compiled
artifact, so `lookup`, `test` and `bench` need nothing on `PATH`;
`--engine hfst` switches them to HFST's reader, which is how
`scripts/crosscheck-ol.sh` checks ours.

## The stack

`recipe.toml` is `plover.cfg`'s enabled list, in its order, so priority here means
what file order means there. `main.json` is disabled there and absent here. The
retro family is disabled there too, and sits below everything enabled so it cannot
shadow a working entry.

| Dictionary | Source |
|---|---|
| emily-symbols | xfst over a shared glyph table |
| shrimple | xfst over a generated table |
| user | JSON, read as itself where a host can write |
| phrases | JSON, imported |
| commands | JSON, imported |
| emily-modifiers | xfst over generated tables |
| semantic-suffixes | JSON, imported |
| two-syllable | JSON, imported |
| typey-type | JSON, imported |
| and-fold | JSON, imported |
| phrasing-tag | xfst, `#^`: the clause with a question tag |
| phrasing | xfst, a clause grammar over 21.9M chords |
| frames | flat source in the record language |
| ahead | flat source in the record language |
| retro-words | xfst over a window of written text |
| repeat-idioms | JSON, imported |
| repeat-word | flat source in the record language |

`stfst compile recipe.toml` prints what each artifact weighs and what it took, so
this table does not: both directions of the whole set are about 46 MB, 31 of
them `phrasing-tag`, and it compiles in about three minutes, against 93.6 MB resident for the Plover-style
dictionary and its reverse index in `docs/M0.md`.

A dictionary whose artifact is newer than everything it is compiled from is left
alone, so that minute is only ever paid for what changed. The compiler counts as
a source, because a rule it changed is in none of the files. `--dict NAME` builds
one by name, and `--force` builds what is already built.

## A dictionary a host can write to

Every artifact in that table is built once and read. `user.json` is the exception
the table's second column names: on the desktop Plover reads the file itself, so
the Add Translation dialog has somewhere to put an entry, and the phone reads it
the same way rather than as `user.stfst`. Beside it sits `stfst-user.json`, which
is what the Android keyboard defines into.

On the phone `stfst-user.json` outranks `user.json`, because an outline
redefined there has to beat what it used to write. In `plover.cfg` it sits the
other way up, so `user.json` stays the dictionary Plover writes to.

`stfst_dict::Editable` is that dictionary: a map from outline to record, built
from a Plover JSON file and written back to it, with its own priority in the
stack like any other. It has no window, no carried state and no context slot, and
that is the deal. A compiled dictionary is a relation over tapes and can say
things a map cannot; a map can be edited on a phone with no HFST.

A directory of them carries an `editable.toml` saying what each file is called
and where it sits, because a JSON dictionary says nothing about priority and a
host that guessed would be a second place that decides it. `android/editable.toml`
is the one the keyboard ships; `--editable DIR` gives `stfst translate` the same
thing, and `!define OUTLINE translation` in a stroke stream is what the keyboard's
panel does, which is how `tests/editable` checks it.

`retro-words` has no reverse automaton, and that is not an omission: it rewrites
text rather than writing a word, so the question a suggestion window asks has no
answer for it. `shrimple` has none for the other reason, which is that its
language has no longest outline. See `docs/retro.md`.

`frames`, `ahead` and phrasing's hole ender belong to text that is not written
yet: each writes its own word at once and the host holds the rest until the next
unit writes. What waits is shown while it waits, so the frame is closed from the
keys and the word lands inside it: `STPR*T` writes "from to", and "here" makes it
"from here to". An inflection has no word to join yet, so it shows as a suffix
reads: `^PW*EU` writes "by -ing", and "run" makes it "by running".

Phrasing's `-RG` says the verb of a whole clause is the word coming next, so
`+TERG` then "run" is "it's running" with every other key of the clause still
meaning what it meant. A held clause waits the same way before its verb:
`🦐+K-R` writes "I work", and "often" makes it "I often work". A determiner
subject holds the noun: `+PR-B` writes "the is adding", and "dog" makes it "the
dog is adding".

`semantic-retro.json` is absent: its entries are clause rewrites over a part-of-speech
tagger, which is the one thing this format deliberately cannot hold. See
`docs/parity.md`.

A few dozen entries in the whole set do not import, and each is named by
`stfst import --report`. All are brace atoms naming no operation (`{pgh}`, `{--}`,
`{...}`, a few unbalanced ones), and Plover types nothing for any of them, so they
are dead entries in typey-type. A control character is no longer among them: text
names one by its code point, `[U+]0A[END]`, which is what makes `commands.json`'s
newline key an ordinary translation.

What the whole set writes is checked against Plover's own formatter rather than
against our last answer: `scripts/check-translate.py` renders every translation
both ways and compares, and none differ. See `docs/parity.md`, which also lists
the five bugs that check found on its first run.

## The keyboard

`android/` is an Android input method over these artifacts: two thumbs, each
walking down a fixed honeycomb through its own half of the steno order, with the
layout fixed by that order rather than by the dictionary. `stfst-jni` links
`stfst-ol` into the app, so
the whole 15 MB stack answers on the phone and nothing is re-implemented in
Kotlin. See `android/README.md` for the gesture and `docs/radial.md` for what
building it changed here.

The host loop both it and `stfst translate` drive is `stfst-board`.

Defining an outline is a keyboard operation there too: `{PLOVER:ADD_OUTLINE}` and
`{PLOVER:ADD_TRANSLATION}` open a panel over the keys, and steno fills both of its
fields. `make install` syncs `stfst-user.json` with the desktop's copy over adb.

## pitman-board

`~/src/pitman-board` is a second consumer: an Android keyboard for Pitman's
Shorthand, where an outline is the chord and a sign is the key. It brings its own
`systems/pitman.toml` and dictionaries and changes nothing here, which is the
answer to whether the general half wants factoring out. `System` was already the
general thing under a steno name, and `Side`, `first_right`, `implicit_hyphen`
and `numbers` never leave `system.rs`. The compiler's generated prelude turned
out to describe a Pitman segment exactly.

One change was needed, and it is in the working tree: `key_letter` refused any
name that did not strip to a single character, so a sign could not be called
`circle-s`. It now falls back to the tape symbol. `letter` spells a written
outline, so it belongs to the outline parser rather than to the key.

That keyboard does not link `stfst-ol` yet. It answers the same question from
tables and holds itself to what `stfst lookup` says, so the native path is still
to be wired. The one change that would want both consumers in one place is
weights in the artefact, which is inside `stfst-ol`.

## The Plover plugin

`plover-plugin/` is a Plover dictionary class over these artifacts, so the format
is portable backwards and a dictionary compiled here works in Plover with no
further work, and now every dictionary in `recipe.toml` loads there.

Four things an artifact can ask for have no way through Plover's dictionary API,
which passes a lookup one argument: a bound word, a window of written text, a
window of the text after it, and a tail. `plover_stfst/host.py` answers each with
a meta and an extension, so `retro-words` and `repeat-word` are artifacts in
`plover.cfg` rather than the Python plugins they were ported from, and `frames`,
`ahead` and phrasing's hole ender work on the desktop too. A dictionary is
refused only by ability, never by a version number: the version is one number
and these are separate abilities.

`scripts/check-hold.py` is what holds that half honest. It drives a whole Plover
engine over five stroke fixtures and compares the text at the end of every case
against what `stfst translate` wrote for the same strokes: 1,372 cases, no
difference, including the 1,316 `retro-words` operations over fourteen buffers.

Nothing is shared with the Rust: the plugin has its own `hfstol` reader and its
own reading of the record language, which is what makes it evidence that the
artifact is portable at all. `scripts/check-plugin.py` renders every entry of
every imported dictionary both ways through Plover's own formatter and compares
them. Every entry, none differing, and three of Plover's answers taken over ours
to get there. A stack check found six more by asking the configured stack
and the stack it was compiled from what each of 156,496 outlines writes, which is
the question a per-entry check cannot ask; it has done its job and `docs/plover.md`
names all six. Against the same seven dictionaries the plugin costs 8 MB resident
where the JSON they came from costs 82 MB. A stroke through the whole live stack
costs 1.2 ms against the sources' 0.57, and it cost 6.0 until a lookup learned to
step the cursor the stroke before left. `docs/latency.md` has that and the two
beside it.

## Morphology, which is not a dictionary

`{^ing}` is not "close the gap": Plover's English system carries 38 rewrite rules
over the join plus a 338,882-word list to choose between the candidates they
generate. Three artifacts hold all of it, and `scripts/build-morph.sh` builds them:

| | |
|---|---|
| `ortho.hfstol` | `word ^ suffix` to every candidate, tagged with the rule that made it |
| `words.hfstol` | a word to how common it is, which is what ranks the candidates |
| `infl.hfstol` | `word ^ mode` to an inflected form, for `{:infl:…}` |

None of them is over the key tape, so none is a dictionary and `stfst morph` builds
them rather than `stfst compile`. The rules are hand written and committed. The two
lexicons come from data that ships with Plover and lemminflect, so
`scripts/gen-morph.py` runs under Plover's python and its output stays out of git.

`{^s}` and `{:infl:ofs}` are different questions, and mixing them was a bug. The
first is orthography, so `child` and `s` is `childs`, exactly as Plover says. The
second is inflection, so it is `children`. Only the second asks a lexicon, and a
host with no lexicon refuses `{:infl:…}` rather than answering it with rules.

## The vowel theory, which writes JSON

`typey-type` is imported from `~/.config/plover/typey-type-full.json`, and that
file is itself generated: `scripts/gen-typey-vowels.py` rewrites Typey Type's
vowels by the rules in `dictionaries/typey-vowels.toml`, reading
`typey-type-upstream.json` beside it. The output is JSON because Plover's
`English Stenotype` system reads the same file and cannot load an artifact.
`~/.config/plover/VOWELS-DESIGN.md` is the design.

The same run writes `vowel-briefs.json`, imported as `vowel-briefs` below
typey-type: the shorter upstream strokes of the frequent words the theory
lengthened, kept only where no entry in the stack writes them.

The generator needs cmudict and wordfreq, which the phrasing venv has. It prints
what moved, what collided and what was displaced, and writes `tests/vowels.tsv`:
each rule's examples at the stroke the theory gave them. `check.sh` tests that
file against the typey-type artifact and fails if the JSON is stale.

`scripts/gen-two-syllable.py` writes `two-syllable.json` the same way, from the
same tagger and table: a word of two syllables as one stroke marked with `^`,
the chord the stressed vowel's XOR the other vowel's letter (report is `^PROERT`,
happy `^HAEUP`), or a prefix chord and no `^` (display is `STKPHRAEU`).
`gen-typey-vowels.py` then moves the Typey Type outlines of the words it writes
into `typey-superseded.json`, which Stenotype Extended does not load. Run the two
in turn until both pass `--check`; one round is usually enough. It reads the compiled stack to leave
a stroke another dictionary writes to it, so run it after a compile. `check.sh`
fails if its JSON is stale.

## Measurements and decisions

| | |
|---|---|
| `docs/key-tape.md` | Why the upper tape is keys and not chords, and where this repo diverges from the design doc. |
| `docs/M0.md` | The M0 acceptance measurement: size against Plover's in-memory dictionary. |
| `docs/latency.md` | Per-lookup latency, why the search frontier is wide, and what the same width costs a host that reads the format in Python. |
| `docs/encoding.md` | What the artifact costs, why delayed alignment would cost more, and the exact size model. |
| `docs/modifiers.md` | The `@` keysym namespace, and why a keystroke's modifiers are tags inside the item. |
| `docs/motor.md` | What a thumb motion costs: one synthesised speed profile read three ways, for judging layouts before building them. |
| `docs/phrasing.md` | The §7 clause grammar: what is a rule and what is a table, and why the retro modifier layer needs a different mechanism. |
| `docs/parity.md` | Plover's translation language operator by operator, what the record language says instead, and what it still cannot say. |
| `docs/plover.md` | The Plover plugin: what a second host found, what a second *stack* found after it, why Plover needs a longest key when a cursor does not, and what reading an artifact costs in Python. |
| `docs/retro.md` | Rewriting text already written: why the window comes after the keys, why the tape carries character classes and not characters, and what is left in the host. |
| `docs/translation.md` | The runtime: longest match without a declared longest key, what a dictionary is checked for at load, and why reverse lookup is a cheapest-first search. |

## Layout

| | |
|---|---|
| `systems/` | Steno systems. Which tape symbol each key writes is configuration, not a convention baked into the tools. |
| `dictionaries/` | Hand-written dictionary source, in xfst. A `-tables.xfst` file is transcribed data with an upstream owner; the grammar over it is authored. `typey-vowels.toml` is the vowel theory's table, which a script applies to JSON. |
| `morphology/` | `ortho.xfst`, Plover's 38 orthography rules. Not a dictionary: nothing here is over the key tape. The two lexicons beside it are generated, not committed. |
| `recipe.toml` | The dictionary stack: what to compile, and at what priority. |
| `tests/golden/` | Host-independent golden tests for the grammars: a tape, a tab, the expected records. An imported source's is written into `build/golden/` by `check.sh`. |
| `tests/vowels.tsv` | The same, for what the vowel table writes, checked against typey-type. |
| `tests/translate/` | Stroke streams and what the stack makes of them, where the answer depends on the history. |
| `tests/retro-words/` | The same, for one dictionary whose answer reads a window of written text, which a golden file has nowhere to put. |
| `tests/dicts/`, `tests/probe/` | Fixtures for the parts of the host contract no shipped dictionary uses yet: a context slot, and a carried state. Compiled by `tests/recipe.toml`. |
| `crates/stfst-core` | Systems, the key tape, the record language, the golden format. No FST dependency, so a host can link it. |
| `crates/stfst-compile` | The compiler: prelude generation, JSON import, the HFST pipeline. |
| `crates/stfst-ol` | Reader for the `hfstol` runtime format: mapped, no decode, no HFST. |
| `crates/stfst-dict` | The runtime: the dictionary stack, longest match, record interpretation. |
| `crates/stfst-text` | The host side of the record language: the word buffer, the formatter, the one general rewrite, and the morphology reader. Names no operation. |
| `crates/stfst-cli` | `stfst`. Testing only. |
| `plover-plugin/` | The Plover host: a dictionary class, an `hfstol` reader in Python, and the record language read both ways. |

## The upper tape is keys, one symbol each

The design doc chose whole chords as atomic symbols. This uses one symbol per
key instead, in steno order, with the right bank in lowercase so every key is a
distinct single character:

```
"+" "T"                  ->  +T
"^" "S" "E" "b" "l"      ->  ^SEbl        (assemble)
"T" "K" "E" "U" "/" "s"  ->  TKEU/s       (two strokes)
```

`TPl` is `T- P- -L` and `Tpl` is `T- -P -L`, so no hyphen convention reaches the
tape. The reason this matters is that a rule can then *name a key*, which an
atomic chord symbol makes impossible:

```xfst
define FoldS  {s} -> "[SUF=s]" || _ .#. ;
```

That is what lets a generative dictionary be written by hand instead of
generated by a script, and it is what makes an error model possible at all: over
atomic chords, `STKPW` and `STKPR` are two unrelated symbols, so edit distance
has nothing to measure. See `docs/key-tape.md`.

A key need not be a key on a board. `🦐` leads the order and marks the stroke as a
Shrimple syllable, which is what replaces that dictionary's entry stroke:
`🦐TAp/🦐KAt` is "tapkat" where `^/TAp/KAt` was. Nothing strikes it, because
`plover-hid-timing` adds it to a chord held past its threshold. So the system
says what the key means and the host says how it is sent, and neither needs to
know the other.

## Adding a dictionary

Three source forms. Hand-written xfst for anything with rules in it, a flat
`.dict` file for a list of entries in the record language, and JSON for anything
imported from Plover.

```
! dictionaries/repeat-word.dict: an outline, a tab, a record
^SKP    and [COPY1]
```

`!` opens a comment and not `#`, because `#` is a key. A duplicate outline is an
error here, where a JSON object would silently keep the last one.

The xfst form defines `Grammar`:

```xfst
define Grammar [ Parse .o. Glyph .o. DoAttach .o. Canonical ] ;
```

The prelude is generated from the system file and prepended, so `Stroke`,
`Outline`, `Record`, `Canonical` and the per-flag hoists are in scope. The
compiler then proves three things over every reachable path, not over sampled
cases:

- no output escapes the record language, and tags are in canonical order
- no input escapes steno order
- no internal feature tag reaches the artifact

A failure prints a counterexample. Add the dictionary to `recipe.toml` with a
priority; higher shadows lower, as `plover.cfg` file order does today. A
dictionary whose outlines have no longest, as `shrimple`'s do not, also needs
`strokes`: how far a host that cannot ask a cursor should look. Every other
dictionary gets that read off its automaton. That one also gets no
`reverse.hfstol`: one word is written by unboundedly many outlines there, so
"which outlines write this" has no finite answer to rank. See
`docs/translation.md`.

Imported JSON dictionaries skip those checks, and that is not an omission: the
import builds records through `stfst_core::Record`, which can only serialise
canonical order, and tapes through the outline parser, which can only emit steno
order. The checks exist to catch hand-written rules.

Then give it expectations, because `check.sh` fails a dictionary that has none.
A JSON or plain `.dict` source needs nothing: it is outline to record already,
so `check.sh` writes its golden file from the source into `build/golden/` on
each run. A grammar needs a `tests/golden/<name>.tsv` of tape, tab, record. For
one whose language is too big to hold, take a subset that cycles each dimension
and record what the artifact answers:

```sh
stfst lookup build/dicts/<name>.stfst --system systems/... < outlines \
    | awk -F'\t' '{ print $2 (NF < 3 || $3 == "-" ? "" : "\t" $3) }'
```

A dictionary whose answer depends on more than the tape gets a stroke fixture
under `tests/<name>/` instead, as `retro-words` does: a `.strokes` file and the
`.expected` that `stfst translate` writes for it.

## xfst notes

Each of these cost a debugging round, so they are written down:

- `define Name` needs its regex to start on the same line
- identifiers are alphanumeric; an underscore fails several lines later
- a bare symbol is the identity pair, so a tag meant to be consumed needs `.x. 0`
- `"\"` escapes its own closing quote; a literal backslash is `%\`
- parameterised defines work, which is what lets a table read as data
- in `lexc`, escape every non-alphanumeric ASCII character, and `0`, which is
  epsilon and otherwise vanishes silently
- `%` is *not* an escape inside `{...}`: xfst reads `{A%*B}` as `A % * B`. Put
  alphanumeric runs in braces and quote every other character on its own. This
  one hides: both sides of a rule can share the corruption and agree
