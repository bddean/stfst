# stfst internals

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
scripts/check.sh affixes              # one dictionary's checks: its generator, goldens, fixture
scripts/fixture.sh tests/translate    # one stroke fixture; --update rewrites its .expected
stfst bench build/dicts/typey-type.stfst --system systems/stenotype-extended.toml
stfst translate build/dicts/*.stfst --system systems/stenotype-extended.toml
stfst reverse build/dicts/typey-type.stfst --system systems/stenotype-extended.toml because
stfst dump build/dicts/affixes.stfst --system systems/stenotype-extended.toml --records
```

HFST and foma come from the flake and are needed only to *build* a dictionary.
Nothing at runtime links them, which matters because HFST is GPLv3 and the
eventual consumer is an Android keyboard. `stfst-ol` reads the compiled
artifact, so `lookup`, `test` and `bench` need nothing on `PATH`;
`--engine hfst` switches them to HFST's reader, which is how
`scripts/crosscheck-ol.sh` checks ours.

## The stack

`recipe.toml` is `plover.cfg`'s enabled list, in its order, so priority here means
what file order means there, and `scripts/check-plover-cfg.py` fails where the
two disagree. `plover.cfg` names nothing else, so every dictionary Plover loads in
that system goes through here. The retro family sits below everything else so it
cannot shadow a working entry.

A suggestion offers the shortest outline, and among outlines of one length the
one from the dictionary with the higher `suggest`, which is its priority unless
the recipe says otherwise. Phrasing's is lowest, so a clause outline is offered
only where nothing as short writes the text. A word no dictionary writes may be
a fold's, so a suggestion also tries a base word's outline and a suffix key, and
offers it where the stack reads it back as the word: hypocrites is `^H-RPBGTS`.
Among outlines of one length, a dictionary's comes before a fold's.

| Dictionary | Source |
|---|---|
| emily-symbols | xfst over a shared glyph table |
| phrasing-tag | xfst, `^+`: the clause with a question tag |
| phrasing | xfst, a clause grammar over 20.7M chords, all on `#` |
| shrimple | xfst over a generated table |
| user | JSON, editable: read as its file, and Add Translation writes it |
| stfst-user | JSON, editable: what the Android keyboard defines |
| phrases | JSON, imported |
| commands | JSON, imported |
| emily-modifiers | xfst over generated tables |
| folds | xfst: what a stroke nothing matched folds to |
| semantic-retro | JSON, editable: clause rewrites over a tagger |
| two-syllable | JSON, generated |
| two-word | JSON, generated |
| affixes | xfst over a generated lexicon, and the orthography rules |
| typey-type | JSON, generated from Typey Type |
| vowel-briefs | JSON, generated beside typey-type |
| retro-words | xfst over a window of written text |
| repeat-idioms | JSON, imported |
| repeat-word | flat source in the record language |
| frames | flat source in the record language |
| ahead | flat source in the record language |

A fold is a stroke nothing matched, read as another stroke with something
written around it: `KAOEPT` is "keep" then "the", `SKPAD` is "and" then "add",
`^TPOU` is "a" then "few" (`TPAOU`).
`dictionaries/folds.xfst` says which keys come off and what they write, as a
record of ordered alternatives, and the host looks the base up across the whole
stack and takes the first that translates. So a fold is a rule in a dictionary
like any other, where Plover's is the system's `SUFFIX_KEYS` and the bare key's
entry. `stfst_core::fold` is the record and `docs/parity.md` the host's half.

`retro-words` has no reverse automaton, and that is not an omission: it rewrites
text rather than writing a word, so the question a suggestion window asks has no
answer for it. `shrimple` has none because its language has no longest outline.
See `docs/retro.md`.

`frames`, `ahead` and phrasing's hole ender belong to text that is not written
yet. Each writes its own word at once, and the host holds the rest until the
next unit writes. What waits is shown while it waits, so the frame is closed
from the keys and the word lands inside it: `STPR*T` writes "from to", and
"here" makes it "from here to". An inflection has no word to join yet, so it
shows as a suffix: `^PW*EU` writes "by -ing", and "run" makes it "by running".

Phrasing's `-RG` says the verb of a whole clause is the next word, so `#TPERG`
then "run" is "it's running", and every other key of the clause means what it
meant. A held clause waits the same way before its verb: `🦐#T-R` writes "I
work", and "often" makes it "I often work". A held subject waits for its noun:
`#WHE-B` writes "the is adding", and "dog" makes it "the dog is adding".

`semantic-retro` is read as its file, because its entries are clause rewrites
over a part-of-speech tagger, which this format deliberately cannot hold. Plover's
own meta runs them, and the Rust host skips them. See `docs/parity.md`.

A few dozen entries in the whole set do not import, and `stfst import --report`
names each. All are brace atoms naming no operation (`{pgh}`, `{--}`, `{...}`, a
few unbalanced ones). Plover types nothing for any of them, so they are dead
entries in typey-type. A control character imports: text names one by its code
point, `[U+]0A[END]`, so `commands.json`'s newline key is an ordinary
translation.

What the whole set writes is checked against Plover's own formatter, not
against our last answer. `scripts/check-translate.py` renders every translation
both ways and compares them, and none differ. `docs/parity.md` also lists the
five bugs that check found on its first run.

`stfst compile recipe.toml` prints each artifact's size and build time. Both
directions of the whole set are about 46 MB, 31 of them `phrasing-tag`, and a
full compile takes about two and a half, most of it `phrasing-tag`. The Plover-style dictionary and its
reverse index take 93.6 MB resident (`docs/M0.md`).

A dictionary whose artifact is newer than everything it is compiled from is left
alone, so a compile only pays for what changed. It is compiled from its source
files, the system, its own entry in the recipe and the compiler's source, and
the artifact's `stamp` file records them. Its own entry is tracked rather than
the whole recipe, so adding a dictionary compiles only that one. The compiler's
source is tracked rather than its binary, so a change to `lookup` compiles
nothing. `--dict NAME` builds one by name, and `--force` rebuilds what is
already built.

## A dictionary a host can write to

Every dictionary in the stack is compiled once and only read, except those the
recipe marks `editable = true`. Such a dictionary is a JSON file read when the
stack loads, and its artifact is a manifest naming the file and nothing else, so
an edit to the file needs no build. `Stack::open` reads the file, and the Plover
plugin serves it as Plover's own JSON dictionary and saves back to it, so the Add
Translation dialog has somewhere to put an entry. `user.json` is the one Plover
writes, being the first. Beside it sits `stfst-user.json`, which the Android
keyboard writes its definitions to.

On the phone `stfst-user.json` outranks `user.json`, because an outline
redefined there has to beat what it used to write. In the recipe it sits the
other way up, so `user.json` stays the dictionary Plover writes to.

`stfst_dict::Editable` is that dictionary: a map from outline to record, built
from a Plover JSON file and written back to it, with its own priority in the
stack like any other. It has no window, no carried state and no context slot, and
that is the deal. A compiled dictionary is a relation over tapes and can say
things a map cannot; a map can be edited on a phone with no HFST.

The keyboard cannot use the artifacts, which name files on this machine, so
`android/sync-assets.sh` leaves them out and ships the files instead. A directory
of them carries an `editable.toml` saying what each file is called and where it
sits, because a JSON dictionary says nothing about priority and a host that
guessed would be a second place that decides it. `android/editable.toml` is the
one the keyboard ships; `--editable DIR` gives `stfst translate` the same
thing, and `!define OUTLINE translation` in a stroke stream is what the keyboard's
panel does, which is how `tests/editable` checks it.

## The keyboard

`android/` is an Android input method over these artifacts. Each thumb walks
down a fixed honeycomb through its own half of the steno order, so the layout is
fixed by that order and not by the dictionary. `stfst-jni` links `stfst-ol` into
the app, so the whole 15 MB stack answers on the phone and nothing is
re-implemented in Kotlin. See `android/README.md` for the gesture and
`docs/radial.md` for what building it changed here.

The keyboard and `stfst translate` drive the same host loop, `stfst-board`.

A steno machine on USB writes through it too: Plover HID with
`plover-hid-timing`'s rules, Gemini PR, or Stentura, configured by the
desktop's `plover.cfg`. What the bytes mean is `stfst-machine`, which does no
I/O, so a desktop host could use it as well.

Defining an outline is a keyboard operation there too: `{PLOVER:ADD_OUTLINE}` and
`{PLOVER:ADD_TRANSLATION}` open a panel over the keys, and steno fills both of its
fields. `make install` syncs `stfst-user.json` with the desktop's copy over adb.

## pitman-board

`~/src/me/pitman-board` is a second consumer: an Android keyboard for Pitman's
Shorthand, where an outline is the chord and a sign is the key. It brings its own
`systems/pitman.toml` and dictionaries and changes nothing here, so the general
half does not need factoring out. `System` was already the general thing under a
steno name, and `Side`, `first_right`, `implicit_hyphen` and `numbers` never
leave `system.rs`. The compiler's generated prelude describes a Pitman segment
exactly.

It needed one change here. `key_letter` refused any name that did not strip to
a single character, so a sign could not be called `circle-s`. It now falls back
to the tape symbol. `letter` spells a written outline, so it belongs to the
outline parser rather than to the key.

That keyboard does not link `stfst-ol` yet. It answers the same question from
tables and holds itself to what `stfst lookup` says. The one change that would
need both consumers in one place is weights in the artifact, which is inside
`stfst-ol`.

## The Plover plugin

`plover-plugin/` is a Plover dictionary class over these artifacts, so a
dictionary compiled here works in Plover with no further work. Every dictionary
in `recipe.toml` loads there.

Four things an artifact can ask for have no way through Plover's dictionary API,
which passes a lookup one argument: a bound word, a window of written text, a
window of the text after it, and a tail. `plover_stfst/host.py` answers each with
a meta and an extension, so `retro-words` and `repeat-word` are artifacts in
`plover.cfg` rather than the Python plugins they were ported from, and `frames`,
`ahead` and phrasing's hole ender work on the desktop too. A dictionary is
refused only for an ability the host lacks, never by a version number, because
these are separate abilities and a version is one number.

`scripts/check-hold.py` checks that half. It drives a whole Plover
engine over nine stroke fixtures and compares the text at the end of every case
against what `stfst translate` wrote for the same strokes: 1,417 cases, no
difference, including the 1,316 `retro-words` operations over fourteen buffers.

Nothing is shared with the Rust: the plugin has its own `hfstol` reader and its
own reading of the record language, which is what makes it evidence that the
artifact is portable at all. `scripts/check-plugin.py` renders every entry of
every imported dictionary both ways through Plover's own formatter and compares
them. No entry differs, after three of ours were changed to Plover's answer. A
stack check found six more differences by asking the configured stack and the
stack it was compiled from what each of 156,496 outlines writes, which a
per-entry check cannot ask. `docs/plover.md` names all six.

Against the same seven dictionaries, the plugin costs 8 MB resident and the JSON
they came from costs 82 MB. A stroke through the whole live stack costs 1.2 ms
against the sources' 0.57 ms. It cost 6.0 ms until a lookup learned to step on
from the cursor the previous stroke left. `docs/latency.md` has these numbers
and how they were measured.

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

`{^s}` and `{:infl:ofs}` are different questions. The first is orthography, so `child` and `s` is `childs`, exactly as Plover says. The
second is inflection, so it is `children`. Only the second asks a lexicon, and a
host with no lexicon refuses `{:infl:…}` rather than answering it with rules.

A join that makes no word completes to one: `idea` and `{^cal}` is
`identical`, where Plover alone writes `ideacal`. The plugin does the same in
Plover, and `docs/parity.md` says how far each half reaches.

A ladder asks the same lexicon a different question: which words some suffixes
make of the previous word. `-L` climbs them one per press, so `cap` is `capable`
and then `capably`. `[META=ladder]` is the meta and `docs/parity.md` the rule.

The rules are also a layer a grammar composes as it compiles: `affixes` spells
the plurals it writes through `Ortho`, so each is the word `{^s}` would write.
There is no word list to rank candidates at compile time, so a rule's candidate
is taken over the plain join, and the golden file is what says a join had only
one.

## The vowel theory, which writes JSON

Four generators write dictionaries by rule. Each one's docstring states its rule
and what it decides besides the rule. `~/.config/plover/VOWELS-DESIGN.md` is the
design. They need cmudict and wordfreq, which the phrasing venv has.

| Generator | Writes |
|---|---|
| `gen-typey-vowels.py` | `typey-type-full.json`: Typey Type with its vowels rewritten by `dictionaries/typey-vowels.toml`. Also `vowel-briefs.json`, `typey-superseded.json`, and `tests/vowels.tsv`. |
| `gen-two-syllable.py` | `two-syllable.json`: a word of two syllables as one stroke marked `^` (report is `^PROERT`). |
| `gen-two-word.py` | `two-word.json`: two one-syllable words often written together, as one `^` stroke by the same rule (at all is `^TUL`). |
| `gen-affixes.py` | `dictionaries/affixes-lexicon.xfst`: the words of the `affixes` grammar, a stem's stroke then one stroke for the prefix and suffix (reservation is `SEFRB/R-GS`). |

The output is JSON where `English Stenotype` reads it too, because that system
cannot load an artifact. `scripts/find-typey-composed.py` writes
`typey-composed.json`, the Typey Type entries the rest of the stack already
writes. It runs under Plover's python, because only Plover's translator can say
what `{:infl:…}` writes. `tests/briefs/` holds the user's briefs the rules came
to write, and each generator's `--check` fails if the rule stops writing one.

The generators read each other's output and the compiled stack, so they run
together: `make regen` (`scripts/regen.sh`, in the dev shell) compiles, runs each
generator's `--check`, reruns the stale ones in dependency order, and repeats
until all pass. Then it writes the pipelines' stage golden files
(`scripts/stages.sh NAME --update`). `--composed` also runs
`find-typey-composed.py` first. `stfst compile` caches each stage's net and each
pipeline prefix under `build/work/cache` by what they are built from, so a change
to one stage rebuilds only it and what follows. `stfst compile recipe.toml --dict
NAME --ablate STAGE` writes the pipeline without one stage to
`build/work/NAME/without-STAGE`, to measure what the stage does.

## Measurements and decisions

| | |
|---|---|
| `docs/key-tape.md` | Why the upper tape is keys and not chords, and where this repo diverges from the design doc. |
| `docs/M0.md` | The M0 acceptance measurement: size against Plover's in-memory dictionary. |
| `docs/latency.md` | Per-lookup latency, why the search frontier is wide, and what the same width costs a host that reads the format in Python. |
| `docs/encoding.md` | What the artifact costs, why delayed alignment would cost more, and the exact size model. |
| `docs/modifiers.md` | The `@` keysym namespace, and why a keystroke's modifiers are tags inside the item. |
| `docs/motor.md` | What a thumb motion costs: one synthesised speed profile read three ways, for judging layouts before building them. |
| `docs/phrasing.md` | The §7 clause grammar: the keys, what is a rule and what is a table, and why `#` is phrasing's alone. |
| `docs/parity.md` | Plover's translation language operator by operator, what the record language says instead, and what it still cannot say. |
| `docs/plover.md` | The Plover plugin: what a second host found, what a second *stack* found after it, why Plover needs a longest key when a cursor does not, and what reading an artifact costs in Python. |
| `docs/retro.md` | Rewriting text already written: why the window comes after the keys, why the tape carries character classes and not characters, and what is left in the host. |
| `docs/translation.md` | The runtime: longest match without a declared longest key, what a dictionary is checked for at load, and why reverse lookup is a cheapest-first search. |

## Layout

| | |
|---|---|
| `systems/` | Steno systems. Which tape symbol each key writes is configuration, not a convention baked into the tools. |
| `dictionaries/` | Every dictionary source. Grammars in xfst and lexc: a `-tables.xfst` file is transcribed data with an upstream owner, and the grammar over it is authored. Flat `.dict` files in the record language. Plover's JSON and Python dictionaries, which `plover.cfg` names here and `dictionaries/README.md` describes. `typey-vowels.toml` is the vowel theory's table, which a script applies to JSON, and `affixes.toml` the affix stroke's. |
| `morphology/` | `ortho.xfst`, Plover's 38 orthography rules. Not a dictionary: nothing here is over the key tape, though a grammar may include it to spell a join, as `affixes` does. The two lexicons beside it are generated, not committed. |
| `recipe.toml` | The dictionary stack: what to compile, and at what priority. |
| `scripts/gen/` | The generators that share the vowel theory, as a package: `vowels`, `two_syllable`, `two_word`, `affixes`, `stack`, which reads what the rest of the stack writes in Plover's syntax, and `corpora`, which counts the phrasing corpora. `scripts/gen-*.py` run them. |
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
| `crates/stfst-machine` | Steno machines: Plover HID, Gemini PR and Stentura on the wire, `plover-hid-timing`'s chorder and sounds, Plover's keymaps, and `plover.cfg`. No I/O. |
| `crates/stfst-cli` | `stfst`. Testing only. |
| `plover-plugin/` | The Plover host: a dictionary class, an `hfstol` reader in Python, and the record language read both ways. |

## The upper tape is keys, one symbol each

The design doc chose whole chords as atomic symbols. This uses one symbol per
key instead, in steno order, with the right bank in lowercase so every key is a
distinct single character:

```
"#" "T"                  ->  #T
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
imported from Plover. A JSON dictionary a host has to write to, or whose entries
the record language cannot hold, is `editable = true` instead of imported: see
"A dictionary a host can write to".

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

and one about the stack: no input holds a key another dictionary `owns`. `owns =
"#"` on `phrasing` is what keeps the number bar out of every other dictionary. A
flat dictionary is held to it too, and a JSON import leaves such an entry out
and counts it, because its source is not ours to edit.

A failure prints a counterexample. Add the dictionary to `recipe.toml` with a
priority; higher shadows lower, as `plover.cfg` file order does. A dictionary
with no longest outline, such as `shrimple`, also needs `strokes`: how far a
host that cannot ask a cursor should look. Every other dictionary has that read
off its automaton. Such a dictionary also gets no `reverse.hfstol`, because one
word is written by unboundedly many outlines, so "which outlines write this"
has no finite answer to rank. See `docs/translation.md`.

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
`.expected` that `stfst translate` writes for it, which `scripts/fixture.sh
tests/<name> --update` records. `artifacts` there lists the stack when it is
more than the one dictionary, and `requires` a file the fixture cannot run
without.

A generated dictionary names its script as `generator` in the recipe, and
`check.sh` runs `--check` on it, which fails when what the script writes is
stale. Any golden file that is not `tests/golden/<name>.tsv` goes in `golden`.

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
