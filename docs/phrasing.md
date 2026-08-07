# Phrasing

`FST-DICT-DESIGN.md` §7 names the dictionary the format exists for, and it is the
one that cannot be a dictionary file: 32,768 clause settings times 668 right-bank
enders is 21.9M chords, and §7 measures a tenth of that at 150MB of baked JSON.
Here it is a grammar, in 698KB, and the whole single-stroke language is ported
from the user's `phrasing.py`.

What is left is the retroactive modifier layer, which is a different mechanism
rather than more of the same one. Its measurement is at the end of this file.

## Three tapes, and who owns each

```
^+T*   Chord    [FULL][SUBJ]it[/SUBJ][C][3sg][NOMOD][NEG][SIMPLE][PRES][V0]
       Chain    [FULL][SUBJ]it[/SUBJ][C][3sg][NEG][PRES][F:be]
       Arrange  it is not
```

`Chord` reads keys and writes a feature bundle. `Chain` reads a bundle and builds
the auxiliary chain: which auxiliary is finite, which are not, and what form the
main verb takes. `Arrange` reads a chain and writes English: agreement,
contraction, and question inversion.

The split is what makes each layer small enough to read. Nothing in `Chain`
mentions a key and nothing in `Arrange` mentions an aspect, so `Arrange` states
the contraction rules once over six finite auxiliaries instead of once per corner
of the aspect-by-modality space. Both middle tapes are internal; the compiler
prunes their symbols, so a host sees keys to text.

`Chord` needs no hoist at all, which the prototype did. Its bundle is in key
order, and the three mood keys `# @ ^` are the first keys, so they are the first
features.

## The paradigm is a rule, not a table

The prototype generated the finite words as one table per surface form, keyed by
the whole selector, and the extension of that was nine tables of up to 96 rows
each. Those tables are the paradigm written out rather than stated, so the
paradigm moved into `phrasing.xfst` instead, and the generator kept only what is a
finite map by nature: the subject sublanguage (126 rows), the right bank (666
chords), and the conjugation (108 verbs times five forms).

The finite verb is now 47 hand-written rows -- `am`, `is`, `are`, `was`, `were`
and their negatives and clitics, in tables keyed by only what each word depends
on -- plus seven lines that say which auxiliary is finite:

```
  [ AnyMod(KSim) Cls(0, VBase) ]                                  she would go
| [ AnyMod(KPf)  Cls(Say("[M:have]"), VPastpart) ]                she would have gone
| [ NoMod(Kill("[PERF]"), Say("[F:have]")) Cls(0, VPastpart) ]    she has gone
| ...
```

Each line is one corner of modality by aspect. The two arguments travel together
because they are one fact: the element that takes the finite form also fixes the
form of everything after it, so the corner that picks `have` is the corner that
makes the main verb a past participle.

Three tables became rules on the way, and each was storing something the bundle
already determined:

- **The clitic** comes from the finite auxiliary, so it is `(Fid, Agr, Tense)`
  and not a column. That deleted the `is_launch` special case: an adverb
  launchpad takes no clitic because `do` has none, and the guard was asserting
  what the table already said.
- **Which negatives are two words** is `am` and present `shall`, so the negative
  table is simply partial there and the separate `not` is what falls out. No set
  of selectors to keep in step.
- **The mids** are polarity-independent, because only the finite head takes a
  negative form. One table of three, not two.

## Contraction is a priority union

Reduction is a preference, not a case: take the clitic where the tables allow
one, else the contracted negative, else the full form. `PU` says that in the
order it happens, and the two modes need no marker to tell them apart.

```
| Red(PU(Clit([ IsAff .o. SufW ], Tail),
         PU(Decl(Merge, Tail),
            Decl([ IsAff .o. AffW ], Tail))))
```

Three preferences deep, and the middle one is the modal perfect: "could've" when
nothing else claimed the auxiliary, which is what `this could've` needs and
`I'd have` does not.

## Question inversion, on a tape where the selector comes after the subject

The finite word leads a question and the selector that picks it is four symbols
*after* the subject. That is a hoist, the same one the record language uses for
its own flags: guess the symbol before the subject, read it after, and let
nondeterminism make the guess free.

```
define Pull(X, P)  [ [ 0 .x. X ] P [ X .x. 0 ] ?* ] ;
define Invert  [ PullFid .o. PullTns .o. PullPol .o. PullAgr .o. PullAnd ] ;
```

Four symbols, thirteen branches, composed. A clausal "and" comes along, because
"and" leads the whole clause: `#+TK` is "and am I", not "am and I".

## One deliberate divergence, and it is a bug fixed

An adverb ender with no modal and no aspect leaves the clause with no auxiliary
at all, and `phrasing.py` then drops negation, the question and emphasis on the
floor. `+T*FBTS` is "it often", `#+TFBTS` is "it often", `@+TFBTS` is "it often".
Three keys with nothing to write themselves onto.

Do-support is exactly the rule for that case, and English applies it: the clause
has nothing for tense or negation to sit on, so `do` arrives to carry them. So
this port writes "it doesn't often", "does it often", "it does often", and the
last one is the emphatic `do` the key asks for. 1,878 strokes differ, and
`scripts/check-phrasing.py` asserts the list rather than ignoring it.

Nothing else differs. In particular `@#+K` is "do I do" in both, and that is
right: `@` names the verb `do`, and a question about it needs do-support like any
other lexical verb. `do I do` reads oddly alone and correctly as the launchpad it
is, and an earlier note in this file that called it a bug was wrong.

## Measured

| | |
|---|---|
| Domain | 32,768 clause settings times 668 enders |
| Artifact | 698KB forward, 589KB reverse |
| Lookup | 19 µs median, 33 µs p95 |
| Frontier | 52 paths per step on average, 665 at the widest |
| Grammar | 25,907 states, 39,605 arcs |

The frontier is the interesting number and it is eight times the prototype's.
Every branch that says a finite word before reading the ender that confirms it
stays live until the ender arrives, and there are 666 enders. A stroke is 100ms
of a writer's time, so 19 µs is not a problem, but it is the shape of the cost
and the retro layer below is the same shape a thousand times over.

## Both directions

```
$ stfst reverse build/dicts/phrasing.stfst --system systems/stenotype-extended.toml
it's             +T       +TU     +TE
it is not        ^+T*     ^+T*E
we weren't       +SK*D    +SK*ED
not to be        +HR*     +HR*D   +HRA*   +HRO*   ^+HR*   ...
does it not do   #@^+T*
I don't often    +K*FBTS  @+K*FBTS
```

Two of those say something. "it's" has three outlines because the progressive and
the perfect scaffold to the same word, and both are real entries. And the
cheapest-first search offers `+HR*` before `+HRA*`, so §7 item 4's host-side sort
may turn out to be free: fewest keys is fewest marked features here.

## Checked

`scripts/check-phrasing.py`, 238,138 strokes, 0 differing. Coverage is split the
way the grammar is:

- **Exhaustive over the clause dimensions**, 196,608 strokes: every left-key set,
  modality corner, polarity, aspect, tense, contraction mode, question and
  emphasis, against one ender per shape the grammar distinguishes -- the empty
  slot, the lone tense key, an adverb, and verbs with a pre-satellite, a
  post-satellite and neither. That is the part with rules in it.
- **Exhaustive over the enders**, all 666 of them against nine clause settings.
  That is the part that is a table.

The full cross product is 21.9M strokes, and there is no reason to walk it: the
enders are a finite map, and a map is checked by reading every row of it once.

The port cost four bugs and this file's coverage caught all four. The clausal
"and" landed after an inverted finite word. The infinitive branch never consumed
the mood flags, so `+HR*` had no entry. Questions had no entry at all until the
selector hoist went in. And `define Record` in the grammar shadowed the prelude's
own `Record`, which silently emptied the reverse direction -- the compiler now
refuses a grammar that redefines a name a prelude macro reads, because `Fresh` is
expanded after the grammar and read a name that no longer named a record.

## The retro modifier layer goes through the window

`lookup` takes a base clause plus up to seven modifier strokes, and each modifier
is a delta on the feature bundle. That reads as composition, and it is, so the
obvious shape is a base relation and one small relation per modifier:

```
Records = [ Bundle Rest ] .o. Step .o. Step .o. ...     seven times
```

That does not fit, and the reason is a property of the mechanism rather than of
this grammar. A modifier stroke is read *after* the bundle it changes, and the
text is written before it, so the transducer has to guess the delta and let the
keys confirm it. The delta is wide: mode (3) times question (2) times emphasis (2)
times growth (2) times modality (4) times polarity (2) times aspect (4) times
tense (2) is 3,072 distinct effects one stroke can have, and every one of them
stays live across the whole bundle.

| | |
|---|---|
| One `Step` | 112,805 states, 5.3M arcs |
| One modifier, end to end | 1.4M states |
| Seven of them | did not finish in eight minutes, 3.8GB resident |

Minimising between compositions changes nothing, because that size is minimal.

The window is the way round it, and it is the same insight `docs/retro.md` already
draws: **a transducer cannot buffer a string before it knows what to do with it,
so the string has to arrive after the keys.** A dictionary that declares
`words = N` is shown the text the host already wrote, *after* the keys. So the
delta is known before the clause is read, and nothing is guessed.

Re-realising means running the clause grammar backwards, and it is a relation, so
it inverts. `Spell` is `Chain .o. Arrange`; its inverse reads a clause into a
bundle, the deltas edit the bundle, and `Spell` writes it out again:

| | |
|---|---|
| `Spell`, bundle to text | 3,261 states |
| The whole fold, every delta at once | 16,640 states |

Three orders of magnitude under the outline route, for the same nine deltas. The
fold was never the expensive part; getting the bundle to it was.

### What the window costs

**The reading.** Text does not determine the bundle. `+T`, `+TU` and `+TE` all
write "it's", and the same `+*` after them gives "it isn't", "it hasn't" and "it
isn't". A clause with no contractible spot also hides its contraction mode: `+G`
and `^+G` both write "go". So the parse takes the least-marked reading -- the one
a writer who never struck `^` or `@` would have meant. Measured against every
reading of every clause text, that is right for 95.2% of folds, and three quarters
of the rest is the contraction mode.

**The span.** The fold takes the longest suffix of the window that reads as a
clause. It has to be the longest: two thirds of clause texts have a shorter suffix
that also reads as a clause, so "I can add" ends in "can add" and in "add", and
folding a fragment would strand the rest. Where that suffix is more than the last
stroke wrote, the window and the Python part company, and the window is usually
right: "I can " typed separately, then `+B` and `+*`, gives "I can't add" here and
"I can don't add" there.

**A third mode left out.** `phrasing.py` gives a bare `+` a subject-clitic mode,
"it's not" where the reduced form says "it isn't". Carrying a third mode through
`Arrange` cost more in the composition than the mode is worth, so a bare `+` folds
nothing.

Both divergences are asserted rather than ignored, by
`scripts/check-phrasing-retro.py`, which seeds the buffer with a clause, strokes a
modifier at it, and compares the finished buffer.

### Where it stands, and what the last obstacle is

Every stage works, checked on concrete input:

```
Parse    +*[W][=i][=t][='][=s][/W]  ->  +*[RED][SUBJ]it[/SUBJ][C][3sg][NOMOD][AFF][SIMPLE][PRES][V0]
Deltas                              ->  ...[NEG]...
WriteCl                             ->  [=i][=t][= ][=i][=s][=n][='][=t]
```

Composing the three into one artifact does not fit. `Parse` is 196,174 states and
3.8M arcs, `Parse .o. Deltas` is 254,866 and 5.0M, and the last composition plus
`Canon` passed 6.8GB resident before it was stopped. So `phrasing-retro.xfst` is
here and is *not* in `recipe.toml`.

The cause is identifiable and it is not the fold. Three character-level machines
end up in one chain: the split that finds the clause in the window, the inverse
grammar that reads it, and the forward grammar that writes it. Each is over the
59-symbol window alphabet, and composing them multiplies.

Two of the three come from the split, and the split is there only because `words`
is fixed. The host already shows the last N words; what it cannot do is let a
dictionary say how many of them its answer consumed. Given that -- the same shape
as `[REPLACE1]`, and a smaller ask -- the window would be exactly the clause, the
head and the marking would both go, and the chain would be `ReadCl .o. Deltas .o.
WriteCl`: the 16,640-state fold and nothing else.

That is the next move, and it is a host contract item rather than more grammar.

### One thing the window made visible

The window carries a character as its *class* unless the grammar names it, which
is what lets `retro.case` recase `RÉSUMÉ` without a case table. Reading a clause
back needs the letters themselves, so this is the first dictionary to name the
alphabet, and the set is derived from the clause vocabulary rather than declared: a
verb with a new letter in it cannot forget to make that letter visible.

Naming both cases is what handles a clause the formatter capitalised. `[CAP]`
carries that fact across the fold, so "It's" reads as "it's", folds, and comes back
"It isn't" -- one symbol rather than two of everything.
