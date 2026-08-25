# Parity with Plover's translation language

What Plover 5.3 can say in a dictionary, and whether this format can say it. The
list comes from Plover's own tables rather than from its documentation: the
`plover.meta`, `plover.macro` and `plover.command` entry points, plus the
installed system's 38 orthography rules.

## The operators

| Plover | Here |
|---|---|
| `{^}` `{^^}` `{^ ^}` `{^a^}` | `[ATTACH_L]` `[ATTACH_R]`: close the gap |
| `{^ing}` | `[ORTHO_L]`: close the gap *and* apply the 38 rules |
| `{&a}` | `[GLUE]` |
| `{-\|}` `{>}` `{<}` | `[CAP_NEXT]` `[LOWER_NEXT]` `[UPPER_NEXT]` |
| `{~\|'}` | `[CARRY_CAP]` |
| `{*!}` `{*?}` | `[CANCEL_SPACE]` `[RETRO_SPACE]`: the gap between the last two units, taken away and put back |
| `{$}` | `[WORD_END]` |
| `{.}` `{,}` `{?}` `{!}` `{:}` `{;}` | a record: `.[ATTACH_L][CAP_NEXT]` |
| `{#control(c)}` | `[KEY]c[CTRL][END]` |
| `{*-\|}` `{*<}` `{*>}` | `[META=retro.cap]` `[META=retro.upper]` `[META=retro.lower]` |
| `{*($c)}` | `[META=retro.currency]$c[END]` |
| `{mode:caps}` … `{mode:set_space:_}` | `[CMD=mode.caps]` … `[CMD=mode.set_space]_[END]` |
| `=undo` `{*+}` `{*}` | `[CMD=engine.undo]` `[CMD=engine.repeat_stroke]` `[CMD=engine.toggle_star]` |
| `{plover:…}` | `[CMD=plover.…]` |
| `{:infl:ed}` | `[META=infl]ed[END]`: a lexicon, no part of speech |
| `{:retro:past}` | **not carried.** A clause rewrite over a tagger |
| `{=regex/yes/no}` | **not expressible.** It reads the *next* translation |
| `{}` and any unknown brace atom | **refused.** Plover writes nothing for one |
| a control character in text | `[U+]0A[END]`: the code point, two hex digits |

Every name is a symbol from a table in `stfst-core::host`, not text. §3 had put
commands and metas in the "open-ended, so delimit" column; a host implements a
fixed set, so the set is closed, and enumerating it makes the load-time check the
same one-pass alphabet check as every other tag. A dictionary that asks for an
operation this host cannot do is refused when it loads.

That refusal has already earned itself: while the table still held a clause-rewrite
operation nobody had written, `semantic-retro.json` was refused at load rather than
loading and quietly doing nothing. The operation is now out of the table too, so
those entries do not import at all and `stfst import --report` names each of the
nineteen. That is the stronger statement: not "this host cannot", but "this format
has no meaning for it".

## Measured against Plover's formatter, not against our own last answer

*Exact.* Every translation in the stack, written between "run" and "y" in Plover
and here, and the two strings compared:

```
146571 translations, 31 refused, 0 of 146540 differ
```

This check exists because the golden files could not do its job, and one bug shows
why. A golden file records what the importer said; regenerate it after changing the
importer and it records the new answer, right or wrong. `{^}` was read as attaching
on *both* sides for two commits, so 516 typey-type entries joined leftward where
Plover joins rightward, and nothing noticed: the artifact on disk was stale too, so
it agreed with the golden file that had been generated from the same mistake.
`scripts/check.sh` now compiles before it checks, for that reason.

What the comparison found, once it existed, was five separate bugs. Each is a case
where Plover's model is per *atom* and a record's is per record, and they only line
up at the edges:

- **Attachment is positional.** The left edge is the first atom's caret and the
  right edge is the last atom's. `co{^}` joins to what follows, not to what
  precedes, so `x co{^} y` writes "x coy". An atom that writes no word, a keystroke
  or a command, passes both along instead of deciding either.
- **A word gap between atoms has to be spelled.** A record concatenates its items,
  so `{,}and` needs the space written in: "run, and", not "run,and". 113 entries.
- **A gap at the record's edge is not text.** Plover's word splitting drops it, so
  `" gradients"` and `"world? "` lose theirs and the record's own flags decide.
- **A pending capital can be spent inside the record.** `{.}so` is ". So". The
  record language has one case flag for the whole record, so where the word is in
  the same record the capital is applied there and the flag is not set.
- **With carets peeled, the core is text.** Plover matches its whole-atom metas only
  on an atom that has no carets, so `{^*^}` is an asterisk rather than the star
  command and `{^-|^}` is two characters rather than a capital. The one exception is
  `~|`, which does compose: `{^~|'^}` writes an apostrophe inside a word.

And one that was ours rather than a misreading of Plover: `{^ia}{,}` merged into the
single word "ia," before the orthography ran, so no rule could match it and `run`
came out "runia" instead of "runnia". A record now keeps one item per atom, and an
orthographic join is spent on the first word.

The 31 refused are brace atoms naming no operation: `{pgh}`, `{--}`, `{...}` and
four unbalanced ones. Plover types nothing for any of them, so they are dead entries
in typey-type, and `stfst import --report` names each. Reading the core as text was
this module guessing, and reproducing Plover's silence would be a different guess.

## What a host must do when it cannot oblige

The distinction between a command and a meta decides what happens when a host
cannot oblige. A **command** is a request, so an unimplemented one is declined and
the dictionary still loads: `plover.quit` means nothing on a phone. A **meta**
edits text, so an unimplemented one would leave the text quietly wrong, and the
dictionary is refused.

## The table read backwards

The operator table above is a correspondence, so it has a second direction, and
the Plover plugin needs it: a record has to be spelled as a translation Plover
writes the same way. `plover-plugin/plover_stfst/record.py` reads it both ways,
and `scripts/check-plugin.py` measures the round trip over every entry in the
stack with Plover's own formatter as the judge. Every entry, no difference; the
one that was left is `{$}` before a suffix, and `docs/plover.md` says why it is.

Two corners the table does not show, because they only appear going back.

**Where the attachment goes.** Plover attaches per atom and a record attaches per
record, so the record's edges have to land on the first and last atom that write a
word. `{^^}` before the body says "join leftward and apply no rule", which is what
`[ATTACH_L]` alone means and what no single directive says.

**Where the boundary went.** A tape cannot hold the boundary between two adjacent
text items, so `{^ed}{?}` and `{^ed?}` arrive as the same record and Plover writes
them differently. What recovers the boundary is that orthography joins a suffix,
and a suffix is what can appear inside a word. See `docs/plover.md`.

## Orthography, which is the operator that mattered

*Done, and it is three artifacts rather than any host code.*

`{^ing}` is the most common directive in every real dictionary and it is not
"close the gap". Plover's English system carries 38 rewrite rules over the join
plus a 338,882-word list to choose between the candidates they generate, so `run`
and `ing` make `running` and `discover` and `ed` make `discovered` rather than
`discoverred`. Thirty-eight regexes in every host is the thing this format exists
to avoid, so all three parts are artifacts:

```
ortho.hfstol   122 KB   `word ^ suffix` to every candidate, tagged with its rule
words.hfstol   6.9 MB   a word to how common it is
infl.hfstol    788 KB   `word ^ mode` to an inflected form
```

The rules are 150 lines of xfst, one line per rule, and two of them are the reason
this belongs in an automaton rather than in a regex engine. Rule 8 is a lookbehind,
`(?<![gin]a)r`, which is a subtraction here: `[ L* - [ L* [g|i|n] a ] ] r`, so
`dispatches` and `monarchs` both come out right. Rule 38 doubles a consonant,
which is a copy, and a copy of *one* symbol is finite, so it stays regular.

What is left in the host is Plover's choosing, and it is fifteen lines with no
English in it: prefer a candidate the word list knows, among those prefer the
commonest, break a tie by which rule made it. The rule number is why the artifact
emits one.

§9 predicted one composed artifact instead, `PU(rule .o. InWordList, plain)`, and
three uncomposed ones are better for two reasons. Composing the rules against the
list means building the preimage of a 338,882-word list under 38 relations, which is
a large net to construct for a choice the host can make in fifteen lines. And the
choice is not a priority union: Plover ranks the surviving candidates by *how
common* each one is, which is a number per word rather than an order over rules, so
the composition would have to carry weights to say it. Leaving the three relations
separate says it with no weights at all, and `words.hfstol` becomes a thing a host
can ask other questions of. A suggestion window wants exactly that.

Two measurements, because the two cases differ and reporting one would hide the
other. Both use Plover's own `add_suffix` as the oracle.

```
300000 pairs, 300000 agree (100.0%)      lowercase words, ten commonest suffixes
 91090 pairs,  90869 agree (99.76%)      capitalised and accented words
```

The first is exact, and it is also the case that happens: a formatter capitalises
*after* it joins, so the word reaching a join is lowercase unless a dictionary
already wrote a capital.

The 221 differences in the second are all one thing, Plover's word list being
case-sensitive where this lowercases to look up. 177 are ours preferring a word the
list knows (`Doctored`, against Plover's `Doctorred`, which is not a word). 37 are
the converse, Plover finding a capitalised entry we miss (`Latiner`). 7 are a
suffix that swallows a final capital in an acronym (`SASEd` against `SASed`). Fixing
the last group needs the rules to report where the word stops and the join begins,
which is a real mechanism for 7 pairs in 91,090, all of them a suffix on an
acronym. It is not built, and this is the number that says why.

An exact check sits under the rate, and it is the tighter one: for every pair,
Plover's rules give a set of `(rule number, expansion)` and so does the artifact,
and `scripts/check-ortho-rules.py` requires the two sets to be *equal*. A rule that
never fires and a rule that fires wrongly look the same in an agreement rate and
not in a set comparison. It found three transcription errors and one alphabet gap
the first time it ran, and it reports which of the 38 rules the sample never
reached rather than leaving coverage to a hopeful `limit`.

## Inflection, which is the other half of a join

*Done.* `plover-semantic-suffixes` uses `lemminflect` for `run`→`ran` and
`child`→`children`, and that is a lexicon, so `infl.hfstol` is one: `word ^ mode`
to a form, compiled from lemminflect's own tables.

Keeping it separate from orthography is the point, and conflating them was the bug
it replaced. `{^s}` is orthography, so `child` and `s` make `childs`, exactly as
Plover says. `{:infl:ofs}` is inflection, so they make `children`. Only the second
asks a lexicon, and a host with no lexicon now *refuses* `{:infl:…}` rather than
answering it with the rules, because a wrong word is worse than a missing one.

The artifact holds only the 69,806 pairs where the lexicon disagrees with
`add_suffix`, and the host falls back to the rules for the other 27,793. That is a
filter and not an approximation: the generator computes both sides and keeps the
entry whenever they differ, so `walk` and `ed` needs no entry and `run` and `ed`
does. `scripts/check-infl.py` checks the composition of all three parts, the
generator's choice, lexc's escaping and the host's fallback, over 60,000
inflections, exactly.

Re-inflection falls out of the lemma table and cost nothing: `running` and `ed` is
`ran`, because the lexicon inflects the lemma and not the form it was handed.

One thing needed deciding rather than porting. `-S` is genuinely ambiguous between a
plural and a third person, and the plugin resolved it with the tagger, by looking at
the word before. What resolves it here is the inflection itself: try the noun
reading, then the verb, and prefer whichever *changes the word*. The plural of the
noun "abandon" is "abandon", so `-S` there means "abandons". Where neither reading
changes it, that is the answer and not a failure, because an invariant plural is a
lexicon fact and only a word list knows it: `sheep` and `-S` is `sheep`.

## What the ecosystem needs that the format still cannot do

**Lookahead.** `{=regex/yes/no}` depends on the translation that has not arrived.
Every context mechanism here looks backwards. The state half of host contract 6 plus
`[REPLACE1]` is the shape of an answer: write the guess, correct it next stroke.

Left undone on purpose, and the reason is a count: no dictionary in
`~/.config/plover` uses it. Zero across typey-type's 145,345 entries, `main.json`,
and every other JSON file there. It would cost the formatter a pending unit, which
is a real change to what a host must do, and nothing is asking.

**Not a tagger, and that is a decision rather than a gap.** The same plugin also
chooses *words* by part of speech: `-S` after an "of" word writes "of", after a
preposition it writes "this", and where the last word cannot take a suffix it walks
left for a head that can. None of that is ported, and none of it should be. Deciding
whether "run" is a noun here is a statistical model, so an artifact holding one could
not be checked and would not be a function of its input, which are the two properties
the format exists to have.

What replaces it is a rule about direction. **What a folded key adds, it appends.**
`KAOEPT` is "keep the" and never "the keep", and the plugin's own README came to the
same place for the same reason: the insertion that landed *before* a phrase fought
with the ordinary reading too often. Appending needs no part of speech, which is why
dropping the tagger costs nothing here.

**The phrasing retro modifier layer.** *The rest of phrasing is done.* The whole
single-stroke language is ported and checked against `phrasing.py` over 238,138
strokes with nothing differing: aspect, the question, emphasis, the 666 verb and
adverb enders, satellites and the infinitive, on top of the slice that was there.
One divergence is deliberate and asserted rather than ignored, and it is a bug
fixed: an adverb launchpad with no auxiliary drops negation, the question and
emphasis in the Python, and do-support is what English does there.

The retro modifier layer is written and checked: 492,480 runs, 490,720 exact, and
the rest is the launchpad divergence above. What it needed was not more grammar but
the run in the other order. Chronologically the modifier is read after the clause it
changes and the text is written before it, so a transducer has to guess the delta,
and one stroke has 3,072 distinct effects: 1.4M states for one modifier, and a
window over the written text passed 6.8GB and lost 4.8% of its answers because text
does not determine a bundle. Newest-first the delta is known first, the deltas
compose so the run length stops mattering, and the whole fold is 362,491 states with
no longest key declared.

What is left is a host that hands a dictionary its own run newest-first, which is one
manifest bit and the harvest path the window already uses. Until then
`phrasing-retro.xfst` is out of `recipe.toml`. See `docs/phrasing.md`.

**A dynamic stack.** *Done for enable, disable and solo.* Which dictionaries are
answering is part of the frame, like the context and the carried state, so a stroke
can change it and undo puts it back. Plover's dictionary commands have no undo at
all; here it costs nothing, because the frame stack was already there.

A dictionary turned off stops answering at once, including a run it had already
started. That is what `stack.solo` is for, and `plover-dict-commands` says so: a
mode with no interference from a multi-stroke entry below it. A name this stack does
not have is ignored, because a user's dictionary set is not a promise about anyone
else's, and `commands.json`'s one solo entry names two dictionaries this stack does
not load.

`stack.priority` is declared and not implemented. Reordering needs an order per
frame rather than a flag per dictionary, and nothing here asks for it.

## Suffix folding, which was the last piece of engine parity

*Done.* Plover's translator strips a `SUFFIX_KEYS` key from the last stroke, looks
the base up in the whole stack, and appends what the bare key means on its own. So
a folding rule does not live in a dictionary at all, which is why §4a could not
find a home for it and why §9 kept the question open.

The engine detail worth writing down: a fold applies to the *last* stroke of a
match, and no candidate knows it is at the last stroke. So the fold is tried as the
stroke arrives, by cloning each live candidate and feeding it the stroke with the
key removed. The clone is cheap, the folded candidate is one shot and the next
stroke clears it, and a candidate nobody harvests costs nothing. A fold is a
fallback, as it is in Plover: `PWABGS` has an entry of its own, so it never folds.

```
KAOEPT   keep + what -T means      "keep the"
TAPGS    tapping + what -S means   "tappings"
```

The second one crosses three parties. `TAPG` is typey-type's, `-S` is
`semantic-suffixes.json`'s `{:infl:ofs}`, and the inflection is the host's
orthography. None of the three knows about the others.

`suffix_keys` is system configuration, like the keys themselves, and it carries the
local fork's `-T` so that `KAOEPT` reads as "keep the".

## What a rule writer pays for the regions

One tape, in regions, in a fixed order:

```
[STATE]…[END]   [CAP=off][W1=lower]   keys   [W] glyphs [/W]
   state              context         keys        window
```

A dictionary that declares nothing sees exactly the keys and writes no boilerplate,
which is every dictionary in the stack but two. A dictionary that declares a region
must consume it in *every* entry, because the host sends what the manifest declares
and the manifest is derived from the alphabet: one entry that reads `CAP` makes
`CAP` part of that dictionary's tape.

The boilerplate for not caring is one token, `Kill(CtxCAP)`, and the trap is
reaching for the catch-all. `Kill(Ctx)` consumes whatever arrived *and* mentions
every slot in the table, so the compiler declares them all, the host sends them all,
and every branch has to read them all. Name the slot you want. The same holds for
`Kill(Win)`.

Forgetting the token is not silent. The entry becomes unreachable, and the outline
check prints the tape that no longer parses, which is how the fixture caught a stale
`Slot` the moment it was renamed.

The practical rule is to keep a window dictionary separate rather than paying the
region tax in a general one, which is what `retro-words` is: every entry there
rewrites, so the region is the point rather than an overhead.

## Two things measured on the way

**The hoist chain was a hidden `2^n`.** `Canonical` composed one hoist per flag
*with each other* before touching a grammar. Three new flags took it from 65,536
states to 786,432, and `emily-symbols` from 29 seconds to 4 minutes 25. Composing
them onto the grammar instead, left to right, keeps each step the size of the
grammar: 0.83 seconds, and the whole stack builds in 36. The flag count no longer
costs anything.

**An undefined identifier in xfst is a symbol, not an error.** So a grammar that
names something that does not exist composes with nothing and the artifact is
empty, and every other check passes trivially over no paths. The compiler now
tests for it. It found the fixture dictionary the same day it was written.
