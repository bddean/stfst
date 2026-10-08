# The upper tape is keys, not atomic chords

`FST-DICT-DESIGN.md` §3 chose whole chords as multichar symbols on the upper
tape. This repo uses one symbol per key. The design doc has not been amended
yet, so it and the code disagree; this file is the record of why, and the
amendment is outstanding work.

## What §3 chose, and what it costs

A chord as one atomic symbol is opaque to rules. Nothing can look inside
`"STKPWHR"` to ask whether `-G` is present, any more than you can ask whether
the letter `q` contains a curve. §4a already hit this with folding and answered
it with `Explode`, a chord-to-keys relation entered and left by composition.

The trouble is that `Explode` has to be a literal list of pairs, because
relating an atomic symbol to its parts is exactly what rules cannot do. For an
imported dictionary the list is the chords it uses. For a *generative*
dictionary the chord set is whatever the rules produce, which you do not know
until you have produced it. So the list has to be derived by a compiler pass
that walks the finished automaton, and every hand-written dictionary depends on
that pass existing.

## What one symbol per key costs instead

Steno letters repeat across banks and position does not always disambiguate:
the key run `T P L` has two paths through the steno-order acceptor, `T- P- -L`
and `T- -P -L`, which is why written steno needs `TP-L` against `T-PL`. Putting
characters on the tape therefore drags the hyphen convention into every rule
context.

One symbol per key removes that. The right bank takes lowercase, so `TPl` and
`Tpl` are different symbol sequences and no hyphen is needed. Which symbol each
key writes lives in the system file, so a Michela board or a hacked keymap ships
its own and the whole toolchain follows.

## What this buys

**An entire mechanism disappears.** No `Explode`, no chord-alphabet derivation
pass, no per-dictionary key mask. Folding stops being a composition sandwich and
becomes one rule:

```xfst
define FoldG  {g} -> "[SUF=ing]" || _ .#. ;
```

**Hand-written generative dictionaries become possible.** This is the one that
decided it. `emily-symbols` is seven independent fields in steno order, and with
keys as symbols each field is a small relation and the cross-product is the
compiler's problem. 39 rows of table data and about 30 lines of rules replace
5,248 enumerated strokes. `emily-symbols.py` is not a build input.

**The error model becomes possible at all.** Over atomic chords, `"STKPW"` and
`"STKPR"` are two unrelated symbols, exactly as far apart as `"STKPW"` and
`"A"`, so an edit-distance transducer has nothing to measure. Over keys, one
substitution is one mis-hit key, which is what a radial touch button actually
gets wrong. Chord-atomic input would have quietly killed M5, which is the
feature the mobile target most needs.

**The host contract shrinks.** Emitting keys in steno order is less work than
producing canonical hyphenated chord text, because a host has a key set and
canonicalising is the harder direction. Number-bar normalisation also stops
being a separate concern: `#` is just a key, and digits never reach the tape.

## What it costs, measured

A stroke is four or five symbols instead of one. §3 measured a *trie* over
characters at 600,504 nodes against 183,527 over chords, and keys land between
the two. The artifact is a minimised automaton rather than a trie, so that
figure overstates it, but the direction is real.

Measured (`docs/M0.md`): the `typey-type-full` artifact is 6,256,925 bytes
against 93.6 MB resident for the Plover-style dictionary with its reverse index.
So the key tape passes the M0 criterion comfortably, and the chord-packing
question is an optimisation rather than a precondition.

## Chord packing is now a build flag, not a mechanism

If size ever demands it, the net-walking pass that derives a chord alphabet from
a finished grammar can run as `--pack-chords`, with the error model unavailable
on packed artifacts. Adding an optimisation later is easier than removing a
load-bearing mechanism, which is the real argument for this ordering.

Not yet implemented, and not yet needed.

## Three changes to §3's record language

**The flags come last, not first.** §3 puts the closed tags in front of the body.
The keys that decide them are the last keys in steno order, so a leading position
forces a forward lookup to guess the flags and carry every guess: 96 live paths
one key into `TR-TZ`. Moving them to the end made the `emily-symbols` artifact a
seventeenth of its size and its lookups seven times faster, measured in
`docs/latency.md`. Nothing wanted them first, because `Record` parses tags
wherever they fall.

**`[CARRY_CAP]` is added**, for Plover's `{~|}`. Twelve typey-type entries need
it, Plover already implements it, and it is one more closed flag.

**`[REPn]` and `[COPYn]` are enumerated rather than delimited.** §3's own rule
says closed and small should be enumerated, and a single symbol can be moved to
canonical position where a multi-symbol block cannot.
