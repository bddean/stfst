# Phrasing

`FST-DICT-DESIGN.md` §7 names the dictionary the format exists for, and it is the
one that cannot be a dictionary file: 32,768 clause settings times 668 right-bank
enders is 21.9M chords, and §7 measures a tenth of that at 150MB of baked JSON.
Here it is a grammar, in 698KB, and the whole single-stroke language is ported
from the user's `phrasing.py`.

What is left is the retroactive modifier layer, which is a different mechanism
rather than more of the same one. What it needs is not more grammar but a tape in
the other order, and the measurements are at the end of this file.

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

## The retro modifier layer, and the tape order that pays for it

`lookup` takes a base clause plus a run of modifier strokes, and each modifier is
a delta on the feature bundle. The whole difficulty is the order the run arrives
in.

Chronological order is the expensive one. A modifier is read *after* the clause it
changes and the text is written before it, so the transducer has to guess the
delta, and one stroke has 3,072 distinct effects: mode (3) times question (2) times
emphasis (3) times growth (2) times modality (4) times polarity (2) times aspect
(4) times tense (2). Every one of them stays live across the whole clause.

| route, one modifier | |
|---|---|
| Chronological outline, `+T/+*` | 1.4M states |
| A window over the written text | over 6.8GB, never finished |
| **Newest-first, `+*/+T`** | **362,491 states** |

Reversing it is not a trick. It is the same rule `docs/retro.md` already draws for
the window: **a transducer cannot buffer a string before it knows what to do with
it, so the string has to arrive after the operator.** The window applies that to
text. This applies it to the run.

### Two relations, and the run length does not matter

```
Net    a whole run to one net delta        15,839 states
Apply  a delta and a clause to a clause   13,882 states
```

`Net` is the load-bearing half. The deltas compose, so a run of any length
collapses into one of 3,072 effects *before* the clause is read, and `Run` is a
Kleene star: this dictionary declares no longest key, where `phrasing.py` needs
`LONGEST_KEY = 8`.

Getting `Net` small took two goes, and the failure is instructive. Absorbing one
stroke at a time costs 3,072 states per absorber, because there the stroke is read
before the delta it edits and the decision has to wait; composing seven of those
against the replay passed 6GB. Accumulating per *field* instead costs one or two
bits each: `^` anywhere is full mode, `*` an odd number of times is negation, and
the newest stroke carrying `A` or `O` sets the modality. Nine relations over a bit
apiece, and the run collapses in one pass.

The other half of that lesson is composition order. `[ clause ] .o. Net .o. Apply`
folds the 362,491-state replay in at step one and then composes *that* with each
absorber, which is the product every time. The small relations have to meet each
other first.

### Why the strokes and not the features

Translation-unit state would carry the same information, and it is worse. The
transducer would have to *write* the bundle out, and the emission order puts the
state after the record, so it would hold the whole bundle across emitting the text.
State makes the transducer pay to remember. The run makes the host pay, and the
host has already paid: it has those strokes because that is how longest match
works.

It also needs no new region in the end. The prior run *is* a multi-stroke outline,
so what a dictionary has to be able to say is "feed me mine newest-first", and that
drops into the harvest path the window already uses. The live cursor still reads one
stroke, so longest match is untouched.

### Checked

`scripts/check-phrasing-retro.py`, 492,480 runs, **490,720 exact**. Coverage is the
product of the dimensions a modifier can touch, which is where the rules are: every
field of the delta alone, every pair, and the three-deep runs that make the
order-sensitive fields disagree.

The remaining 1,760 are the clause grammar's own deliberate divergence reaching
here, the adverb launchpad with no auxiliary, and nothing else differs. In
particular the modes, the toggles, the counts and the last-writer fields all agree:
`+*/+*/^+/+A/+K` is "I can", `@+/@+/+K` is "I do do", `+E/+U/+K` is "I've been".

The port cost two bugs and both were only reachable from here. The last-writer
fields read the *oldest* stroke, because "last" on a newest-first tape is the
leftmost, not the rightmost. And the modal perfect would not merge across an adverb
-- "I could've often", not "I could often have" -- which one stroke cannot reach at
all, because the past tense and an adverb ender both want the right bank.

### What is left

`phrasing-retro.xfst` is not in `recipe.toml`, because a host that hands a
dictionary its own run newest-first does not exist yet. The grammar is checked
without one, since a reversed run is an ordinary outline and `stfst lookup` answers
it, but a writer strokes the clause first. That is one manifest bit and the harvest
path, and it is the only thing between this and a working retro layer.

