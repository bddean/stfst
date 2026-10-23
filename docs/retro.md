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
| `semantic-retro` | 19 entries, read as its file rather than imported, since the record language has no meaning for a tagger. Plover's `{:retro:…}` meta runs them; phrasing's own fold is the alternative. |

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

## The other direction: text that is not written yet

`retro-words` reads text the host has written. Two mechanisms belong to the text
it writes *next*, and both make the host hold something: the answer belongs to a
unit that does not exist when the keys arrive.

|  | reads the text | what waits |
|---|---|---|
| **a tail** | no | the record that follows the text: `STPR*T` then "here" is "from here to" |
| **a window read forwards** | yes, as a window | the whole answer, because a lookup over text nobody has written has none |

### A tail: a record that only follows

```
STPR*T   from to
HAOER    here
         from here to
```

`[AFTER]` delimits the tail, and the tail is a record of its own with its own
flags, because the join is the whole point: `by[AFTER][META=infl]ing[END][END]`
writes "by" now and asks the host for the gerund of whatever comes next. One
record waits for one unit, so a tail holds no tail.

The body writes with the keys, which is the reason both `frames` and `ahead` are
tails: a writer sees "from" as they stroke it, and only its close waits. Nothing
else could write it that early. A record that read the word would be a function
of text nobody has written, so its whole answer would have to wait, and the head
would arrive with the word rather than with the keys.

### What a tail shows while it waits

The close, before the word that goes inside it. `Record::preview` is that form
of a tail and `Text::preview` writes it, so a writer sees the frame closed from
the keys and the word lands in it.

| the tail | shows | with the word |
|---|---|---|
| `from[AFTER]to[END]` | from to | from here to |
| `"[ATTACH_R][CARRY_CAP][AFTER]"[ATTACH_L][END]` | "" | "Here" |
| `and[AFTER]s[ORTHO_L][END]` | and -s | and cats |
| `by[AFTER][META=infl]ing[END][END]` | by -ing | by running |

A join wants a word nobody has written, so an orthographic tail and an
inflection each show as a hyphen and a suffix rather than joining the body:
"ands" would be a lie about what is there. Everything else shows as itself, and
its own flags place it, which is what closes `""` around nothing.

The preview is written nowhere. It is a function of the text and the record
waiting, so `Board::text` recomputes it and the buffer a window reads never
holds it: a frame is shown the words a unit wrote, and a preview is not one of
them. A pending capital belongs to the word that lands rather than to the close,
so the preview does not spend it.

A frame previews nothing. It reads the text it waits for, so until that text
exists it has no answer, and a guess would be the one thing this whole mechanism
is avoiding.

### A window read forwards

The other mechanism reads the words the next unit writes, as `retro-words` reads
the words already there. The grammar is the same shape: keys, then a window, then
a record that copies the window and writes around it.

```
[ Kill(K) Kill(WinOpen) Say([ {Dear} " " ]) Cap Say({,}) Kill(WinEnd) ]
```

Nothing in the grammar says which direction it is. `next = 2` in the recipe says
it, where a retro dictionary says `words = 16`, and a tape carries one window so
the two exclude each other.

What this can do and a tail cannot is *read* the word: the rule above capitalises
whatever it is shown. What it cannot do is inflect, because the window hides the
letters: a rule shown `[LETTER][LETTER][LETTER]` cannot double the `n` in "run".
Nor can it write anything before the word arrives. No dictionary in `recipe.toml`
asks for one, so `tests/dicts/held.xfst` is where it is exercised.

### Why the host has to hold the stroke

The lookup is a function of text that does not exist when the keys arrive, so
there is no answer to give yet. Nothing already in the record language closes
that gap:

| | |
|---|---|
| A carried state moves a fact to the *next stroke of the same dictionary* | and the word comes from another dictionary, which is a stroke this one is asked nothing about |
| `[COPY1]` splices a word the host has bound | and the host binds words it has written, not words it is about to |
| `[REPLACE1]` supersedes the unit before | which is backwards: both of these belong to the unit after |

So the host holds the stroke. For a frame it keeps the cursor that read the keys
and harvests again once a unit has written a word, which costs one cursor clone
per stroke while a frame waits and nothing at all when none does. For a tail it
keeps the record, and the body writes at once: a tail's own stroke is an ordinary
match, and only a frame's stroke reports as held.

Both are host 3. A host that shows written text and holds nothing would run a
frame against the wrong words and write a suffix before its word.

### What a frame is shown

The words written since its keys, and nothing older. A frame cannot reach back
over text that was already there, and a unit that wrote more words than the
frame declared is text the frame cannot be shown, so the frame does not fire:
showing the last few would frame the tail of a phrase.

A unit that wrote no word of its own leaves the frame waiting. A record that
joined into the word before it wrote no word, and a command wrote nothing at all,
so a frame waits through both.

A tail waits for *text* and not for a word, because it follows the text rather
than reading it: `(` joined to the word after it wrote no word of its own, and
the `)` still belongs after that word. A command still leaves it waiting.

A held candidate answers only where nothing else does, and before a fold.
Whether it matches at all is unknown until the text arrives, so it cannot take
part in the longest-match comparison; but its keys *did* match as they were
written, and a fold is a last resort, so the fold comes after it. A tail needs
none of that ordering: its stroke matched as written, which is what makes
`SAO*EURT` "either _ or" rather than "either" plus a `-T` fold.

Where a later stroke turns the held stroke into part of a longer match, the hold
never happened: the host puts back what that stroke found, which is what makes
undo a pop here too.

### The three dictionaries

| | |
|---|---|
| `frames` | 13 templates on `S-` plus a brief plus `*`. `STPR*T` is "from _ to", because `TPR` writes "from" and `-T` writes "to"; the head writes with the keys and the tail waits. Ten of the fifteen briefs collide with no prefix and three under `S-`; two of those three are gone for it and the third survives because what it collided with was a fold. |
| `ahead` | 19 words that govern a gerund: `^PW*EU` is "by _ing". An alternative to folding, and the point is that it never surprises: `RUPBG` is "rung", so "run" plus a folded `-G` never happens and the writer has to know which words those are. |
| `phrasing`'s hole | `-RG` says the verb of the clause is the word written next, and `🦐` holds the clause open before its verb for the next stroke. See `docs/phrasing.md`. |

### Plover

Plover asks a dictionary one question and passes one argument: the outline. So
neither a tail nor a frame can be answered by a lookup, and `plover_stfst/host.py`
answers with a meta instead. `{:stfst_hold:…}` carries what the wait needs and
writes the preview; `{:stfst_drop:}` takes that preview back off the screen;
`{:stfst_frame:…}` writes a frame once the word is there, taking the words back
with `prev_replace` the way Plover's own retro metas do; and the extension moves
the one to the other when a translation writes.

The preview is one action, built in the meta rather than spelled as a
translation, so its own text is exactly what went on screen and the drop is the
same string back. `stfst_hold` marks the action it wrote and `stfst_drop` finds
it, which is why a unit that wrote nothing in between cannot be mistaken for
it.

Two more metas came out of the same corner and retired the two Python plugins
this family was ported from. `{:stfst_retro:…}` is a window of text already
written, so `retro-words` is an artifact in `plover.cfg` now, and
`{:stfst_copy:1}` is a bound word, so `repeat-word` is one too. Every dictionary
in `recipe.toml` loads there.

A windowed entry's own answer is unknown until the window arrives, so a lookup
can only say that the keys *begin* one, and the dictionary that says so claims
the stroke. A windowed dictionary therefore belongs at the bottom of
`plover.cfg`, where it claims only what nothing above it wanted, and that is the
one place the two hosts differ: `stfst-board` asks for a live candidate after
everything else and lets the stroke fall through where the rule does not apply.

Two discoveries paid for the extension. `Translator._listeners` is a **set**, so
its hook may run before the formatter or after it and nothing may depend on
which: the hook reads the translation's own text for the marker rather than
letting the meta arm the wait, and it reads the text again to decide whether a
word was written rather than asking `Translation.formatting`, which may not exist
yet. And what lands has to become part of the word's own translation, either by
joining it onto that translation's `english` before it is formatted or by taking
the word back and writing it again after, so that one `*` undoes both as it does
here.

### What is checked

`tests/frames` is a stroke stream over four dictionaries, because the word a
frame goes around comes from another one, and that is the whole reason the host
holds anything. Every case there writes the head with the keys and shows the
close beside it, and two cases end on the keys, where the preview is the whole
answer. `tests/held` is the mechanism itself over three fixture
dictionaries: both kinds of hold, a two-word unit through a one-word frame, a
hold that waits through a join and a command, a longer match over the held
stroke, and an undo that leaves it waiting again.

`tests/ahead` and `tests/hole` are the inflected tails, and both are skipped
where `build/morph` is absent, because what they prove is an inflection.
`scripts/check-hold.py` replays nine fixtures through a real Plover engine and
compares: 1,417 cases, no difference, which is the two hosts agreeing on
everything a lookup cannot be told, the preview included. `tests/repeat-word` and
`tests/retro-words/ops.strokes` are two of the nine, so the 1,316 retro
operations over fourteen buffers are answered the same way by both. Those fixtures list their artifacts in priority order,
highest first, because that is the order Plover reads a collection in and the
order `Stack::open` sorts a recipe into; a fixture that listed them any other
way would be asking the two hosts different questions, and one that did was
caught doing it.

