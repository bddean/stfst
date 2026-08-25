# Retro: rewriting text that is already written

Four dictionaries in the user's config edit the recent output rather than
appending to it: recase a word, join or split one, wrap one in brackets, echo the
last word after a connective. In Plover each is a *meta*, a Python callback the
formatter hands its output history to, because a dictionary lookup never sees
prior output.

Porting them is what host contract items 5 and 6 were for, and it moved a line
that the design doc had drawn in the wrong place.

## Where the line went

§5 item 5 said the host binds a literal word to an opaque register "so the
transducer can move the string without inspecting it". That is right for one of
the four and wrong for the other three, and the reason is three facts about what a
transducer can do to a string:

| | |
|---|---|
| It can **rewrite** a string it reads | join, split, wrap and recase are all regular rewrites |
| It cannot **duplicate** one | `{(w, w·w)}` is not a regular relation, so repeat-word's echo stays a host operation, and that is what `[COPY1]` is for |
| It cannot **buffer** one before knowing what to do with it | so the text has to arrive *after* the keys |

The third is the one that shapes the tape. A window before the keys would have to
be held while the keys were read and then written out again, which is the second
fact. So the keys come first, the operation is known, and then the words stream
through the rule that operation selected:

```
"^" "A" "p"   [W] [LETTER][LETTER][LETTER] [SP] [UPPER][LETTER][LETTER] [/W]
                  the                           Fox
```

The consequence for the runtime is small, which is why this is affordable. A live
candidate reads keys only; harvesting one clones its cursor and feeds the window.
So a candidate that never gets harvested pays nothing, and only a dictionary that
declares a window pays at all.

## The tape does not carry characters

It carries, per character, either the character itself when the grammar names it,
or the class it belongs to:

| symbol | |
|---|---|
| `[LETTER]` `[UPPER]` | a cased letter, lower or upper |
| `[CASELESS]` | a letter with no case: 東, א |
| `[DIGIT]` `[OTHER]` `[SP]` | |
| `[=-]` `[=(]` … | a character the grammar named |
| `[DROP]` | output only: the next character, not written |

So `retro.case` says "this letter is uppercase now" and never learns which letter
it was. The host applies its own casing when it substitutes the answer back, which
means full Unicode casing without a case table anywhere in the automaton:
`RÉSUMÉ` works, and so would a script this port has never seen.

Case is a class of its own rather than a mark after the letter. With a mark, "a
lowercase letter" would mean "a letter with no mark after it", so every rule would
carry a lookahead, and the two camelCase rules could not be written without one.

**Which characters are named is derived, not declared.** The rules mention the
menus' punctuation, because splitting on `-` means finding a `-`. The compiler
reads that set out of the finished automaton's alphabet and writes it into the
manifest, so a host and a dictionary cannot disagree about it, and adding a
separator to the menu cannot forget to make its character visible. `[=-]` and not
`-`, because `-` is how a key spells itself and the compiler has to be able to
tell the two apart.

## Substitution needs every character accounted for

A class glyph coming back means "the next character of the window", in order. So a
rule that deletes one has to say so, and `[DROP]` is how: it consumes a character
and writes nothing. Without it, closing a gap left the substitution one character
out of step for the whole rest of the window, which read as

```
the quick Brown FOX   ->   the quick Brown- FO
```

That was the bug this design was always going to have, and it found itself the
first time a join ran. A significant character never takes a turn, because it is
literal on both tapes, so a rule may insert and delete punctuation freely.

## What is left in the host

One capability, and it names no operation:

```rust
let (start, span) = text.span(words);      // show the last N words
text.rewrite(&record, &window, words);     // replace exactly those N words
```

The separator and bracket menus, the camelCase scanner, find-versus-last, and the
per-operation argument parsing are all gone from Rust. What stays is the buffer
itself, the formatter, and the casing.

The dial is per dictionary. `retro-words` declares 16 words, which is
`retro-words.py`'s `WINDOW`; an orthography rule that reads the previous word
would declare 1 and cost proportionally less.

## The four dictionaries

| | |
|---|---|
| `retro-words` | The `^A` namespace: 114 strokes over four verbs and two menus. An xfst grammar over a generated dispatch. |
| `repeat-word` | 16 stems, `and [COPY1]`. A flat source in the record language, and the meta disappears: `[COPY1]` is the host's bound word. |
| `repeat-idioms` | 607 entries, imported. Overrides the trigrams whose third word is not the first, and wins by being a longer match. |
| `semantic-retro` | 19 entries, imported. `{:retro:past}` becomes `[META]retro[ARG]past[END]`; nothing implements that meta yet, and phrasing's own fold is the alternative to it. |

`repeat-word` is where the register half of item 5 earns its place. The Python
needs a meta because Plover cannot say "the last word"; the record language can,
and the whole plugin becomes one entry per stem.

## Checked

A golden file over strokes is not enough, because what can go wrong is the
rewrite, and a rewrite is wrong only for some text. So the fixture is a stroke
stream: `tests/retro-words/ops.strokes` runs every entry against fourteen buffers
chosen to hit the corners, and the expectation is the finished text. 1,316 cases.
`reach.strokes` asserts the reachable set: all 1,024 strokes in the layout, entry
or not.

The expectations were recorded from a run that `retro-words.py` had just agreed
with, over the same cases. Its ops are pure functions of a word window, so it ran
without Plover, and that is the last thing it did as an oracle.

Three divergences, each deliberate:

**Non-ASCII letters.** The Python splits a word on `[^0-9A-Za-z]+`, so it breaks
`naïve` at the `ï`. A class knows that a letter is a letter, so we do not. The
buffers in the property test are ASCII, and this is the better answer.

**A tab and a newline.** Separator menu rows 7 and 8 hold a control character,
which `lexc` cannot write and which crashes `hfst-xfst` inside a quoted symbol. So
those rows are absent and their strokes have no entry. This is the same limit
§9 already records, met a second time, and it is an argument for the declared
escape that entry proposes: it was harmless when the menu lived in the host and it
is not harmless now.

**Composition.** The Python packs a composed operation into one meta argument
joined with `+`, and applies each part, skipping the ones with nothing to do. Here
each part is total on its own and they compose: `Then(A, B)` is `Or(A) .o. Or(B)`.
Both halves have to be total, or `^AEP` on a single word would do nothing at all
rather than capitalising it.

## Measured

| | |
|---|---|
| Artifact | 405,783 bytes forward. No reverse: see below |
| Lookup | about 12 µs with a 15-word window, against under 1 µs for a plain stroke |
| Frontier | 1 live path, because the keys emit nothing and only select |

The 12 µs includes the harness printing an 80-symbol record per stroke, so it is a
ceiling. The shape of the cost is one automaton step per character of window,
which is the dial doing what a dial does.

A window dictionary is not usefully reverse lookable, and the compiler does not
try: `Fresh` strips the window as it strips a carried state, so the reverse
automaton is empty. The suggestion window asks "what do I stroke to write this
word", and a retro entry does not write a word.
