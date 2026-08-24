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

The chain is Python to Rust to HFST: `crosscheck-ol.sh` already holds the Rust
reader to `hfst-optimized-lookup`, so the format has one reading and three
implementations of it.

## No difference in 147,188 entries

`check-plugin.py` renders each entry twice through Plover's own formatter, once
from the original JSON translation and once from the artifact's record spelled
back, and requires the two to land in the same place. Every entry of every
imported dictionary in the stack does.

Getting there took three of Plover's answers over ours, and each is below: where
an orthographic join ends, what a keystroke leaves behind, and what `{*!}` edits.
Two of the three are changes to `crates/stfst-text` rather than to the plugin,
which is what a second host is for.

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

## Two differences an entry check cannot see

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
comfortable rather than fast. The whole loadable stack of eleven costs 9 ms and
1 MB more, because most lookups die on their first key.

The frontier is what the time goes on: 58 live paths a step on typey-type, and
about 810 arc reads a lookup. The Rust reader does the same walk in 8.8 us. The
gap is the interpreter, so closing it would mean inlining the table reads into
the cursor, and that trades the one thing this plugin exists to demonstrate,
which is that the format is cheap to read again.

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

## What is not here

Reverse lookup answers with the eight shortest outlines, because a generative
dictionary writes one word with unboundedly many and the suggestions window wants
the few shortest. `casereverse_lookup` answers with nothing: the automaton relates
a record to its outlines and a case-folded record is a different record, so
answering would need a second net rather than a second query.
