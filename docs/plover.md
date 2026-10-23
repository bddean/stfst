# The Plover plugin

M1 of `FST-DICT-DESIGN.md`: a Plover dictionary class over a compiled artifact.
The milestone calls it "the cheapest validation that the host contract is
honest", and it earned that description. This is what it found, what it costs,
and where it stops.

## What a second host is for

The artifact is meant to be portable, and nothing proves portability like a
second reader. The plugin re-implements two things this repo already has in Rust:
the `hfstol` reader and the record language. Neither shares a line with the
original, which is what makes agreement evidence rather than tautology.

Both are held to the originals by measurement and not by review:

| | |
|---|---|
| `scripts/crosscheck-py.sh` | The Python reader against the Rust one, over 42,807 tapes: every tape in the golden files, 40,000 random key sequences, and every outline of a dictionary when one is named. The random ones matter most, because a reader goes wrong on dead ends rather than on entries that exist. |
| `scripts/check-plugin.py` | Every entry of every imported dictionary, both directions, with Plover as the judge. |
| a stack check | The whole configured stack against the dictionaries it was compiled from. What a per-entry check cannot see is which dictionary wins an outline. Retired with the migration: see below. |

The chain is Python to Rust to HFST: `crosscheck-ol.sh` already holds the Rust
reader to `hfst-optimized-lookup`, so the format has one reading and three
implementations of it.

## No difference in any entry of the stack

`check-plugin.py` renders each entry twice through Plover's own formatter, once
from the original JSON translation and once from the artifact's record spelled
back, and requires the two to land in the same place. Every entry of every
imported dictionary in the stack does. 147,911 of them at the last count, which
moves as `user.json` does.

Getting there took three of Plover's answers over ours, and each is below: where
an orthographic join ends, what a keystroke leaves behind, and what `{*!}` edits.
Two of the three are changes to `crates/stfst-text` rather than to the plugin,
which is what a second host is for.

## An entry check cannot see a stack

`check-plugin.py` asks one dictionary about one entry. The stack check asked the
*stack* what an outline writes: it loaded the list in `plover.cfg` twice, once as
configured and once with every artifact replaced by the file it was compiled
from, and asked both every outline any of them defines. A dictionary that claims
one outline too many changes what the dictionaries below it get to say, and no
per-entry check can see that.

It reads 156,496 outlines and it found six things a golden file did not. Five are
one shape: a generator that sampled its oracle instead of asking it.

**emily-symbols had its attachment inverted, on every symbol.** That fork sets
`attachmentMethod = "space"`, which swaps the two keys: a symbol attaches on both
sides by default and `-R`/`-B` open a space. The xfst had the upstream reading
and called the difference deliberate, claiming "same visible output in ordinary
use". `#KU` wrote "run+y" in Plover and "run + y" here, and so did 162 others.
The generator could not catch it, because it read the glyph table and never asked
`lookup`, so it was an oracle for a third of the dictionary. Asking it instead
gave all 4,994 strokes, which is every combination of the six fields, and that
is what `tests/golden/emily-symbols.tsv` now holds.

**Shrimple's entry stroke typed the word "Shrimple".** The Python answers
`{Shrimple}` for a bare `^`, and Plover knows no meta of that name, so it writes
nothing. Reading it as literal text made the stroke type it.

**`{}` closes a join, and is not nothing.** `{^} {#Tab} {}` leaves a space and
`{^} {#Tab}` does not. It is Plover's empty text atom, so it ends the join where a
keystroke or a case meta beside it leaves the join alone. The reader took the
last *speaking* atom and skipped `{}` entirely, which made 540 emily strokes
attach where the Python detached.

**`{&^}` is glue on a caret.** Plover reads a directive's opening characters
before it reads the carets, so `&`, `#`, `:` and `=` at the front are that meta
and not text joined rightward. The record `&[ATTACH_R]` was spelled `{&^}`, which
Plover glued.

**The last case a translation names is the one Plover spends.** `{<}{-|}` writes
"The" and not "THE". The shared table gave the glyph's case priority over the
key's, with a reason that was a preference rather than a fact.

**The empty glyph takes no repeat key.** The Python refuses it so that the
`U9`-shaped strokes stay clear of `wm.py`'s focus-space keys, and the xfst
allowed 24 strokes the Python did not.

## What the stack check reported at the end, and why

| | |
|---|---|
| 55 | `emily-modifiers` combos that skhd binds. The Python runs the shell command from `lookup` and answers `{#}`; the artifact emits the keystroke and `plover-skhd-relay` runs the command at the output seam instead. The check compares dictionaries, so it cannot see the extension. |
| 31 | `typey-type` entries the importer refuses, which are that dictionary's own broken translations: `{oxan}`, `{mithracin}`, `{remember}`, four unbalanced braces. Plover writes nothing for any of them either, and three do lose a mark. |
| 16 | `-B` on the glyph that uppercases the next word. Plover's trailing `{}` spends the pending case, so the key does nothing there today and the artifact honours it. |
| 1 | `{#Alt_L(Tab Tab)}` becomes two combos, so the modifier is released between the taps. A record holds one key per keystroke, and one hold across two keys has no spelling. |

The check is gone with the migration it was written for. It compared the stack
against the dictionaries stfst replaced, and those are no longer the definition
of anything: what each artifact holds is now checked against a file of
expectations, and `check-plugin.py` still holds every imported entry to Plover.

## A tape merges what an atom boundary separated

`{^ed}{?}` is two Plover atoms: an orthographic join of "ed", and a question mark
after it. The record language spells a body as a run of items, and a *tape*
cannot hold the boundary between two adjacent text items, so the record reads
back as one item, `ed?[ORTHO_L]`. Spelling that as `{^ed?}` is not the same
translation: Plover writes "runned?" for it and "runed?" for the original.

What recovers the boundary is a fact about orthography rather than a guess. A
join applies to a *suffix*, and a suffix is what can appear inside a word, so the
plugin splits at the first character that cannot: `{^ed}{^?}`. The rules fire on
"ed" and the question mark is stuck on after, which is what the two atoms said.

Where to split is the whole question, and letters are the wrong answer. `run` and
`es` is "runes" in Plover and here, but `run` and `es'` is "runnes'" in both,
because an apostrophe is a letter as far as the rules are concerned. Splitting at
the first non-letter would ask about "es" and write "runes'", which neither host
would. Splitting at the first character no word holds asks about "es'" and gets
"runnes'" from both.

Sixty-one entries in typey-type are spelled with a split. Every one of them lands
where the original translation lands.

## Two differences that need two strokes to show

`check-plugin.py` compares one entry at a time, so a difference that needs two
strokes to show is out of its reach. Running `tests/translate/basic.strokes`
through Plover found both of these, and `crates/stfst-text` has adopted Plover's
answer to each.

**A keystroke leaves the attachment where it found it.**

```
@KR      c[ATTACH_L][ATTACH_R]     "...it is notc"
@KR-F    [KEY]c[CTRL][END]         "...it is notc"
PHOR     more                      "notcmore",  and "notc more" before this
```

A record that writes no word left nothing of its own, so what the unit before it
left passes on. `[CARRY_CAP]` already said that about the pending case, and this
is the same statement about the rest of the tail. The importer had taken that
view *inside* a record all along, where a keystroke "passes both along instead of
deciding either", so the two halves of the format now agree as well.

**`{*!}` edits the gap already written.**

Plover resolves `{*!}` as a macro before the formatter sees it: it joins the last
two translations, so "run more" becomes "runmore". `[CANCEL_SPACE]` used to mean
"open no gap before my own text", which is a no-op for the records that carry it,
because every one of them is the flag alone. It is a retrospective edit now, and
so is `[RETRO_SPACE]` beside it: one slot, two values, both about the gap between
the last two units. A unit that joined into the word before it has no gap there,
and then both are no-ops, which is Plover's answer too.

Plover's `{*?}` is not copied exactly, and that is deliberate. It re-looks-up the
strokes of the last translation and spaces its pieces, which is a translator
operation and not a formatting one, and this format keeps the two apart. What
`[RETRO_SPACE]` does here is the text-level half: put the gap back.

## Plover needs a longest key, and a cursor does not

`docs/translation.md` argues that no dictionary here declares a longest key: a
cursor says whether a stroke run starts an entry, so the window of candidates
finds its own size. Plover's dictionary API takes a whole outline, so Plover has
to know the longest before the first stroke.

So the compiler answers it once. `Automaton::max_occurrences` counts how many
times the separator can appear on an accepted input tape, by layers: the states
reachable without reading one, then the states one separator away, and so on
until a layer repeats an earlier one. The manifest carries the answer as
`[input] strokes`, and no host in this repo reads it.

Every answer matches the source it was imported from, which is what
`check-plugin.py` compares:

| | |
|---|---|
| typey-type | 13 |
| user, repeat-idioms | 3 |
| everything else imported | 1 |
| shrimple | no longest |

Shrimple's language is `^ / syllable / syllable / …`, so its outlines have no
longest and the layers never stop growing. That is a real property and not a
failure to measure, so the recipe declares the cap instead: 40, which is what
`Shrimple-WSI-English.py` declares to Plover today. A dictionary with neither a
longest nor a cap is refused, because Plover would silently stop looking.

## What it costs

Against the same seven dictionaries, the same 200 strokes, and Plover's own
translator:

| | resident | per stroke |
|---|---|---|
| the artifacts, through this plugin | 8 MB | 8 ms |
| the JSON they were imported from | 82 MB | 0.09 ms |

Ten times smaller and ninety times slower, which is the trade a Python frontier
walk makes. A stroke arrives about every 250 ms at 200 words a minute, so 8 ms is
comfortable rather than fast.

That number was measured one lookup at a time, and a host does not ask one lookup
at a time. Measured instead on the live stack, twenty strokes of prose through
Plover's own translator and formatter, and the trailing four phrases through
reverse lookup the way `plover-spacebar-suggestions` asks for them:

| | the sources | the artifacts | after |
|---|---|---|---|
| forward, per stroke | 0.57 ms | 6.0 ms | 1.2 ms |
| reverse, per phrase | nothing measurable | 16.1 ms | 2.9 ms |

Sixty-four milliseconds a stroke went on suggestions alone, on the thread that
takes the next stroke. `docs/latency.md` has the three findings and what each one
cost. The short form is that a lookup is now one step from the cursor the stroke
before left, that a dictionary whose outlines have no longest carries no reverse
automaton, and that the start state is closed once rather than per lookup.

The frontier is still what the time goes on, and it is still the interpreter that
makes it cost: the Rust reader does the same walk in 8.8 us. Closing that gap
would mean inlining the table reads into the cursor, and that trades the one
thing this plugin exists to demonstrate, which is that the format is cheap to
read again.

## A dictionary lists what it holds

Plover reads a dictionary as a set of entries in two places, its dictionary
editor and `casereverse_lookup`, and `stenodrill` reads the stack the same way to
build a deck. An artifact answered none of them, because a `StenoDictionary`
lists `self._dict` and nothing fills that here.

An automaton has the keyspace though: it is the language, and walking it is a
walk of the transition table. So the plugin lists what it holds, on the first ask
and then held, which for `typey-type` is 145,314 entries in four seconds. A
dictionary that writes a syllable at a time holds infinitely many and lists none,
and a cycle on the walk is how that is discovered rather than declared.

`check-plugin.py` holds the listing to the lookup: what a dictionary lists is
what it answers for, and neither has an entry the other lacks.

## Two things Plover resolves before a dictionary is a dictionary

Both were invisible to `check-translate.py`, because that script compares
formatter output and neither of these reaches the formatter.

**Macros.** A translation of `=undo`, `{*+}`, `{*}`, `{*!}` or `{*?}` is a macro:
Plover's translator acts on the whole mapping and the formatter never sees it. A
record holding one alone spells back to exactly that mapping, so the plugin gets
Plover's real behaviour. Beside other atoms the same text is not a macro and
Plover's formatter ignores it, where the record language has one flag for the
whole record and no way to say "here and not there". Nothing in the stack writes
one, so that half stays on paper.

**An empty translation is an untranslate.** Plover writes the raw steno for one.
So a record that writes nothing has to say so, and the plugin spells it `{}`.
`SPWO*L` in typey-type is `{#}`, a key combo with no keys in it, and it is the
entry that found this.

## One difference left, and why it is left

`{$}` marks a word finished. Plover joins a suffix to it anyway: "run", `{$}`,
`{^ing}` is "running" there and "runing" here, because `crates/stfst-text` reads
`[WORD_END]` as "no rule reaches back past this". Plover's own `{$}` sets
`word_is_finished`, and what that stops is the *translator* folding a suffix key
onto the word, which is a different place.

Adopting Plover's answer here is a one-line change to the formatter and a
question about where `[WORD_END]` belongs, because the flag would then do nothing
in this host at all: `stfst-dict`'s fold does not consult it either. Nothing in
the stack strokes `{$}` and a suffix in sequence, so this one is written down
rather than answered.

## What a lookup cannot be told

Plover asks a dictionary one question and passes it one argument: the outline. So
four things an artifact can ask for have no way in, and each of them was a
refusal at load until the metas in `plover_stfst/host.py` answered it instead.

| a dictionary asks for | the meta | when it writes |
|---|---|---|
| a bound word, `[COPY1]` | `stfst_copy` | at once: the word is behind the cursor |
| a window of written text, `words = 16` | `stfst_retro` | at once, over the words it rewrote |
| a window of the text after it, `next = 2` | `stfst_hold` then `stfst_frame` | when a unit writes a word |
| a tail, `[AFTER]` | `stfst_hold` then the tail itself | the close at once, the rest when a unit writes anything |
| a preview off the screen | `stfst_drop` | with the word the tail waited for |

Both windowed kinds take the words back with `prev_replace`, which is how
Plover's own retro metas rewrite what is already written, and the class glyphs of
a window record are substituted with `Record.rewrite`. So `retro-words` and
`repeat-word` are artifacts in `plover.cfg` now rather than the Python plugins
they were ported from, and `frames`, `ahead` and phrasing's hole ender work here
too. Every dictionary in `recipe.toml` loads.

What it costs is one divergence, and it is about *which* dictionary answers
rather than what it writes. A windowed entry's own answer is unknown until the
window arrives, so a lookup can only say that the keys begin one, and a
dictionary that says so claims the stroke: `^A` writes nothing here, where
`stfst-board` lets it fall through to a lower dictionary. A windowed dictionary
therefore belongs at the bottom of `plover.cfg`, and there it claims only what
nothing above it wanted.

`scripts/check-hold.py` is what holds this half to the Rust one. It drives a real
translator over the same fixtures `stfst translate` answers, and compares the
text at the end of every case: 1,417 of them, no difference, including the 1,316
retro-words operations over fourteen buffers. `reach.strokes` is the one fixture
it cannot ask, because that one asserts which strokes have *no* entry and Plover
writes the raw steno where `stfst-board` writes nothing.

## What is not here

Reverse lookup answers with the eight shortest outlines, because a generative
dictionary writes one word with unboundedly many and the suggestions window wants
the few shortest. A dictionary whose outlines have no longest answers with nothing
at all: see `docs/latency.md`.

`casereverse_lookup` answers with nothing. The automaton relates a record to its
outlines and a case-folded record is a different record, so asking it would need a
second net rather than a second query. The listing could answer instead, by
folding every entry once the way Plover's own dictionaries do at load, and that is
left undone on purpose: the walk costs four seconds for `typey-type` and the first
thing that asks would be a suggestion, on the thread taking the next stroke.
