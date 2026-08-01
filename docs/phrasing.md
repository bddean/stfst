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

Only the last one is blocked rather than merely unwritten. The retro layer edits
the clause already on screen, which is host contract item 6, and a dictionary
declaring item 6 is refused at load until M4. The rest is the "mechanical volume"
§7 predicted: the verb lexicon is 668 more generated rows against the same
`Realize`, whose auxiliary chain `MODAL? (HAVE..en)? (BE..ing)? MAIN` is the part
this prototype did not need to write.
