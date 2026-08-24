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

## The one difference in 147,188 entries

`check-plugin.py` renders each entry twice through Plover's own formatter, once
from the original JSON translation and once from the artifact's record spelled
back. One entry differs:

```
ES/AE   want "{^es'}" -> " runnes' y",  got "{^es}{^'}" -> " runes' y"
```

Both spellings are the same record. They differ because Plover's orthography
answers `run + es` and `run + es'` differently: the first consults a word list,
finds no "runnes", and concatenates; the second finds no candidate in the list at
all and takes the first rule's output. Our host answers `run + es'` the way the
plugin's spelling does, so the plugin agrees with this repo and disagrees with
Plover on one entry.

## A tape merges what an atom boundary separated

`{^ed}{?}` is two Plover atoms: an orthographic join of "ed", and a question mark
after it. The record language spells a body as a run of items, and a *tape*
cannot hold the boundary between two adjacent text items, so the record reads
back as one item, `ed?[ORTHO_L]`. Spelling that as `{^ed?}` is not the same
translation: Plover writes "runned?" for it and "runed?" for the original.

What recovers the boundary is a fact about orthography rather than a guess.
Orthography joins a *suffix* to a word, and a suffix is letters: measured over
Plover's own rules, a join whose suffix starts with anything else is plain
concatenation. So the plugin splits at the end of the leading letter run and
spells `{^ed}{^?}`, and the second half is an ortho join no rule can fire on.

Sixty-one entries in typey-type are spelled with the split, and three of them
write something different without it. The same reading is what `crates/stfst-text`
does, which applies the join to the first *word* of the body.

## One difference an entry check cannot see

`check-plugin.py` compares one entry at a time, so a difference that needs two
strokes to show is out of its reach. Running `tests/translate/basic.strokes`
through Plover with this stack finds one:

```
@KR      c[ATTACH_L][ATTACH_R]     "...it is notc"
@KR-F    [KEY]c[CTRL][END]         "...it is notc"
PHOR     more                      Plover: "notcmore",  here: "notc more"
```

A keystroke between two words does not clear the pending attachment in Plover,
and it does here: `crates/stfst-text` ends a unit with that record's own
`[ATTACH_R]`, and a record that writes no word has none. The importer already
takes the other view *inside* a record, where a keystroke "passes both along
instead of deciding either", so the two halves disagree. Nothing else in the
stack reaches it, and it is a question about `stfst-text` rather than about the
plugin.

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
Plover's real behaviour. A record holding `[CANCEL_SPACE]` *and* a body would not,
because `{*!}` beside other atoms is no longer a macro and Plover's formatter
ignores it, where this format reads it as a leftward attachment. Nothing in the
stack writes one, so the difference is still on paper.

**An empty translation is an untranslate.** Plover writes the raw steno for one.
So a record that writes nothing has to say so, and the plugin spells it `{}`.
`SPWO*L` in typey-type is `{#}`, a key combo with no keys in it, and it is the
entry that found this.

## What is not here

Reverse lookup answers with the eight shortest outlines, because a generative
dictionary writes one word with unboundedly many and the suggestions window wants
the few shortest. `casereverse_lookup` answers with nothing: the automaton relates
a record to its outlines and a case-folded record is a different record, so
answering would need a second net rather than a second query.
