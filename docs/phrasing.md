# The phrasing prototype

`FST-DICT-DESIGN.md` §7 names the dictionary that the format exists for, and sets
one question for a prototype: is the grammar pleasant to write, or do the mode
dimension and the juncture rules fight? The slice it asks for is subject plus
copula plus negation plus modality plus the contraction modes, both directions.

That slice is now `dictionaries/phrasing.xfst`, ported from the user's
`phrasing.py`, and the answer is that it did not fight. What follows is what the
port settled, and what it left.

## The layering held

Two layers, composed, exactly as §7 sets out:

```
^+T*   ->   [SUBJ]it[/SUBJ][C][3sg][BE][NEG][FULL]   ->   it is not
       Chord                                    Realize
```

`Chord` reads keys and writes features. `Realize` reads features and writes
English. Nothing in `Realize` mentions a key, and nothing in `Chord` mentions a
word, so agreement, contraction and the finite forms are all decided over
features. Composition leaves the feature symbols in the alphabet and the compiler
prunes them, so a host sees keys to text and never learns the middle tape
existed.

The two halves also split by who owns them. `phrasing-tables.xfst` is generated
from `phrasing.py`: the subject map, which is 126 rows because §4.1's growth,
coordination, politeness order and type shift are all finite maps over subsets of
five keys, and the closed-class conjugation, which is data because English's
finite forms are irregular. The rules that read those tables are hand-written and
fit on one screen.

## Contraction is a priority union, not a mode flag

The one thing §7 worried about is the mode dimension, and it turned out to be the
cheapest part. Reduction is a preference: take the clitic where the tables allow
one, else say the full form. That is `PU`, so the reduced and full modes need no
marker to tell them apart and no rule fires twice:

```
define Realize  [ Infinitive | FullAff | FullNeg
                | PU(RedClit, RedAff) | PU(RedClitNot, RedNeg) ] ;
```

Two shapes carry every mode. A clause says its finite word after the subject; a
clitic hangs the word on the subject and leaves any `not` behind. The awkward
cases stay small because of that: `am not` has no aux contraction, so the
reduction falls back to the subject and gives "I'm not", which is one composition
against the table of selectors where the negative is two words. `there're` does
not surface, which is one subtraction, and it is a rule rather than a column in
the table because the exception is about the pair `there` + `are` and not about
`there`: "there'll" is fine.

`PU` moved into the generated prelude for this, since Shrimple wants it too.

## The tape order is the feature order, with two hoists

The feature record is in the order the keys arrive in, which is what keeps the
lookup from guessing. Two features arrive in the wrong place: `^` is the first key
but `[FULL]` is the last feature, and `*` precedes the right bank while `[NEG]`
must follow `[PAST]`. Both are the hoist the record language already uses: the
branch that reads the key emits its feature after the rest of the body.

```
define Star(B)  [ [ Kill("*") B Say("[NEG]") ] | B ] ;
```

## Measured

The slice is 4,032 entries over 1,361 distinct translations.

| | |
|---|---|
| Artifact | 15,927 bytes forward, 13,835 reverse |
| The same slice as baked JSON | 122,533 bytes |
| Lookup | 2.5 µs median, 3.8 µs p95 |
| Frontier | 7.9 paths per step, 33 at the widest |

7.7× on a prototype is not the interesting number. The interesting one is that
the ratio grows: the full dictionary multiplies this slice by 668 right-bank verb
chords, and a table multiplies out where a grammar factors. §7 measures that end
state at roughly 150MB of baked JSON, which is why the dictionary cannot be a
dictionary file.

## Both directions

Every one of §7's five examples reverses, which is the acceptance criterion:

```
$ stfst reverse build/dicts/phrasing.stfst --system systems/stenotype-extended.toml
it's         +T
it is not    ^+T*
I'm not      +K*
it was       +T-D     ^+T-D
we weren't   +SK*D
not to be    +HR*     +HRO*   +HR*D   +HRA*   ^+HR*   ...
```

Two of those say something. "it was" has two outlines because full mode is inert
for `was`, so both strokes are entries and both are offered, correctly. And
"not to be" shows §7 item 4 already satisfied for this slice: item 4 asks for a
host-side sort putting the least-marked stroke first, and the cheapest-first
search offers `+HR*` before `+HRA*` because it weighs a key at one. Fewest keys is
fewest marked features here, so the sort may turn out to be free.

## Checked exhaustively

The domain is 128 left-key sets times four modality corners times negation times
tense times the two modes, which is 4,096 strokes, so `tests/golden/phrasing.tsv`
is every one of them against `phrasing.py`. No sampling, and no judgement about
which cases matter. A stroke with no subject is a modifier and not a clause, and
the Python raises for it; those lines carry no expectation, which asserts the
stronger thing: this dictionary has no entry, so the stack falls through.

The port cost one bug, and the exhaustive file is what caught it: a clausal "and"
plus an infinitive (`+THR`) lost its conjunction, because an infinitive has no
subject word to fold the "and" into and needed a feature of its own.

## Left out

Out of the slice, and so out of the language: aspect (`E`/`U`), question (`#`),
emphasis (`@`), the verb and adverb enders, satellites, and the retroactive
modifier layer. A stroke holding any of them has no entry here.

Nothing is blocked any more. The retro layer needed host contract item 6, and M4
built it, so what is left is work rather than a question. The rest of this file is
the design for that work, written down because it was worked out and is worth more
on paper than in a commit message.

## The rest of the language, designed

### The record grows five features

Key order in this system is `# @ ^ + STKPWHR A O * E U` then the right bank, and
each feature is emitted where the record wants it rather than where its key sits.
A hoist does that: `Kill(key) Body Say(feature)` reads the key early and writes the
feature late, which is how `^` and `*` already work.

```
[SUBJ]w[/SUBJ] (C) Agr Head Asp (PAST) (NEG) (FULL) (EMPH) (Q)
```

`Asp` is one of `[SIMPLE]`, `[PERF]`, `[PROG]`, `[PERFPROG]` from `E`/`U`. `[EMPH]`
comes from `@` and `[Q]` from `#`. The wrappers nest in key order, `#` outermost,
so the emission order is the reverse and the three new flags land at the end:

```
Chord = Q(Emph(Full([ Kill("+") Subjects Mid Star(Asp Past) ])))
```

`Sel = Agr Head Asp ("[PAST]")` stays contiguous, which is what lets a table be
composed onto it. `[EMPH]` deliberately does not join the selector: it sits past
the flags, so a rule requires or forbids it in the tail and there are two table
sets rather than one twice as wide.

### What is data and what is rule

The auxiliary chain is `MODAL? (HAVE..en)? (BE..ing)? MAIN`, and the finite forms
of `be`, `have`, `do` and the modals are irregular, so the chain's *words* are
data. Its *arrangement* is not. Nine tables over `Sel`, each at most 96 rows,
generated from `_render_complex` exactly as the present two are:

```
FinAff  FinNeg  Rest  Suf  NegSep  Merged  RestVe        the copula paradigm
FinAffDo  FinNegDo  RestDo  SufDo  NegSepDo  ...          the `do` paradigm
InfText                                                   Asp to "to have been"
```

`Rest` is the words after the finite one, and it is polarity-independent: in
`_render_complex` only the head takes `negform`, so negation never reaches the
mids. That is what keeps the table count down.

Five arrangements read them, and every mode is one of the five:

```
full, aff      subj " " FinAff Rest
full, neg      subj " " FinAff " not" Rest
question       Fin " " subj Rest, and Fin " " subj " not" Rest where NegSep
clitic         subj Suf Rest, and subj Suf " not" Rest where NegSep
clause         subj " " Fin Rest, when no clitic exists
```

The question inverts the finite word past the subject, and that is not a problem
for a transducer even though the selector sits *after* the subject on the tape. The
finite words are a closed set of about twenty, so the branch is
`Say(word) SubjCopy Kill(Sel)` once per word, and the selector consumed at the end
is what picks which branch survives. Same trick as the hoists.

Reduction stays a priority union over the arrangements, as it is now: reduce where
the tables allow it, else say the full form. That is why the modes need no marker.

### One place to disagree with the Python

`@` on an empty predicate names the verb `do`, because the unmarked verb is `be`
and the key already says the word. Under a question the Python then applies
do-support to that `do` and writes **"do I do"**: 440 of the 32,256 clauses in this
slice come out that way, and none of them means anything. Do-support exists to give
tense and negation a word to sit on when the lexical verb cannot carry them, and a
`do` that is already the verb needs no second one.

So the port writes `do I`, `don't I`, `does it not`. It is a divergence, it is
deliberate, and the exhaustive check has to carry a list of the cases where the two
disagree rather than assert equality everywhere.

### The retro modifier layer is an iterated composition

`lookup` takes a base clause stroke plus up to seven modifier strokes, and each
modifier is a delta on the feature bundle. That is exactly composition. The base
stroke maps to a record and the rest of the outline rides through unchanged; then
one relation per modifier reads `record Sep stroke` and writes the record it
becomes:

```
Chords = Base .o. ModStep .o. ModStep .o. ...     seven times, LONGEST_KEY - 1
```

`ModStep` is total, so an outline with fewer modifiers passes through the remaining
steps untouched. Seven compositions of a small relation over a small record, and
`_apply` is a dozen assignments, so the step is a table too. `+` bare selects the
third contraction mode, which is the `subj` value the single-stroke language never
reaches.

### What the volume looks like

658 right-bank chords name a verb, a satellite slot and a tense; 8 name an adverb.
Both are generated tables over the right bank, and 107 verbs times five forms is
the conjugation. The `Realize` side gains one concatenation, the main verb's word,
whose *form* the chain already decided. That is the "mechanical volume" §7
predicted, and it is mechanical only because the chain above it is written first.

Checking cannot stay exhaustive over the whole language: 128 subject sets times the
clause dimensions times 668 enders is tens of millions of strokes. It stays
exhaustive over the clause dimensions with the enders fixed, which is the part with
rules in it, and becomes a large sample over the enders, which are a table.
