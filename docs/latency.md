# Lookup latency

M0 asks for per-lookup latency against Plover's in-memory dictionary.
`docs/M0.md` could not answer it, because the only lookup available then was
`hfst-optimized-lookup` as a subprocess, and 8.5 us per probe was mostly process
startup. `stfst-ol` makes the measurement real.

`stfst bench`, release build, this machine (aarch64-darwin), 20,000 random
outlines from the dictionary, five passes. Re-measured after the parity work, which
is why two columns of numbers appear below:

| | typey-type | emily-symbols |
|---|---|---|
| open | 0.23 ms | 0.03 ms |
| median | 12 us | 0.67 us |
| p95 | 34 us | 1.4 us |
| p99 | 48 us | 1.9 us |
| throughput | 70,000/s | 1,374,000/s |

The hand-written dictionary got faster and the imported one got slower, both by
about a third, and neither move is explained. The alphabet grew by forty symbols
over the same period, which was the obvious suspect and is not the cause: declaring
only the symbols an import writes changed the artifacts not at all, because
`hfst-lexc` already prunes what it does not see. Recorded rather than resolved.

Against 0.34 us for the Python dictionary lookup. So a hand-written dictionary is
at parity and an imported one is 25 times slower, which is the honest comparison
and also not the interesting one: a stroke needs a handful of lookups, a frame is
16 ms, and the artifact is 15 times smaller.

Open is a `mmap` and a header read. Nothing is decoded, so a cold dictionary
costs its first few lookups and no startup.

## A stroke costs a lookup per live candidate

The number a host cares about is per *stroke*, not per lookup, and it follows from
the two above: every stroke opens one candidate per dictionary and advances every
candidate that is still live, so the cost is the width of the candidate window times
the cost of a step.

`stfst translate --quiet`, 9,349 single strokes drawn from typey-type outlines,
whole stack of 13 dictionaries, formatting and folding included:

| | |
|---|---|
| per stroke | 61 us |
| candidates | 7 at the widest |
| typey-type and emily-symbols alone | 56 us |
| one orthographic join | 16 us |

A join is the one operation that reads several artifacts at once. It asks the rules
for candidates and then asks the 338,882-word list about each, so up to forty
lookups answer one `{^ing}`. That costs 16 microseconds and does not move the
per-stroke number, because a stroke does at most one join: a stream where every
third stroke folds a suffix and inflects it measures 59 microseconds a stroke,
which is the ordinary number. The fold is a replay of the strokes it spans and
the inflection is one lookup.

Against a 16 ms frame, so a stroke uses four tenths of one per cent of the time
available. The stack size is nearly free: 13 dictionaries cost 9 microseconds more
than 2, because the small ones die in a key or two.

§4's "12.5 us a stroke for the whole stack" is not comparable. It predates the
formatter, the window and folding, and it counted a narrower stream.

## Two things that were quadratic

**The output buffer.** A unit's snapshot copied the whole buffer, and the buffer
grew without bound, so 9,349 strokes cost 320 microseconds each and most of that was
copying words nothing could reach. The buffer and the undo depth are now bounded, at
512 words and 64 units, which is more than any window or undo run needs: 65
microseconds. The document a writer is filling lives in their editor, not in the
engine.

**The fold.** Building a folded candidate as each stroke arrived cost a cursor per
foldable key per candidate on every stroke, and a fold can only matter on a stroke
that matched nothing. Built on demand instead, from the same replay undo uses, it
costs nothing until it is needed.

## What the cost is

A lookup costs about what the frontier is wide:

| | paths per step | widest |
|---|---|---|
| typey-type | 33.5 | 720 |
| emily-symbols | 1.7 | 18 |

A frontier is wide when the automaton emits output that the input has not yet
decided, because then it must guess and carry every guess. The two dictionaries
used to do this for different reasons. One is fixed and one is not.

**Fixed: a record used to lead with its flags.** `emily-symbols` reads `TR-TZ` and
had to have emitted `[ATTACH_L]` or not, though `-R` and `-B` had not arrived, so
one key in there were 96 live paths, one per flag combination. Flag keys are last
in steno order, so moving the flags to the end of a record lets a dictionary emit
them where it reads them:

| | before | after |
|---|---|---|
| artifact | 207,924 bytes | 12,214 bytes |
| median lookup | 5.8 us | 0.83 us |
| frontier | 62 paths per step | 1.7 |

A seventeenth of the size and seven times faster, for a change of serialisation
order. Nothing preferred flags first: `Record` parses tags wherever they fall, so
the leading position was a convention with a cost and no reader.

**Not fixed: pairwise alignment emits a translation from the first key.** `lexc`
pairs the two tapes symbol by symbol, so `T:the quick` puts `t` on the `T` arc
and the rest of the translation on epsilon arcs that follow it at once. Every
shorter entry sharing a prefix therefore hangs a dead output tail off our path,
and after one key `typey-type` has 68 live paths. Delayed alignment would fix it,
and `docs/encoding.md` prices that at 1.8 times the artifact. Not worth it at 8.8
us against a 16 ms frame, so this width stays and this is the reason why. That
price is the Rust reader's. The section below measures what the same width costs
Plover, which is a different number and a different answer.

The two causes were independent, which the numbers show: reordering the record
did nothing at all for `typey-type`, whose records mostly carry no flags.

## The same width costs a hundred times more in Plover

Everything above is measured against `stfst-ol`. Plover runs the Python reader,
where the frontier that costs 8.8 us costs 206, and 24 lookups a stroke go to
`typey-type` alone.

The live stack, twenty strokes of prose through Plover's own translator and
formatter. The reverse column is one phrase, and
`plover-spacebar-suggestions` asks for the trailing four on every translation:

| | the sources | the artifacts | after |
|---|---|---|---|
| forward, per stroke | 0.57 ms | 6.0 ms | 1.2 ms |
| reverse, per phrase | nothing measurable | 16.1 ms | 2.9 ms |

The sources cost nothing backwards because a JSON dictionary builds its reverse
index at load and a Python one has no reverse lookup at all. So the migration put
about 70 milliseconds a stroke on the thread that takes the next stroke, and 64
of them were suggestions.

**A lookup is one step, not a walk.** Plover's dictionary API takes a whole
outline, so a lookup walked from the start every time. The outlines it asks about
are the suffixes of the recent strokes though, and each of those is what it asked
one stroke shorter on the stroke before. The plugin now keeps a cursor per
outline in two generations, and a lookup steps a cursor already taken:
`typey-type` goes from 206 us to 19. This is the candidate window the Rust engine
keeps, recovered from the one question Plover knows how to ask.

**A word with no shortest outline has no suggestion.** `shrimple` writes a
syllable at a time, so one word is written by unboundedly many outlines and
"which outlines write this" is a cheapest-first search rather than a lookup. It
cost 40 ms a word, 85 per cent of the reverse total, and it never won: the
cheapest outline it offers for `because` is four strokes where `typey-type` has
one. An artifact whose outlines have no longest now carries no `reverse.hfstol`.
That is the fact the recipe already had to declare, because a host that needs a
longest key had to be told one.

**The start state is closed once.** A cursor followed the epsilon transitions out
of state zero on every lookup. For `phrasing` that closure is 186 paths, and 125
of the 175 microseconds a lookup cost. It is the same closure every time, so the
automaton reads it once and a cursor clones it.

This one is the Rust reader's too, which is the only one of the three that is.
`stfst translate --quiet`, 9,000 single strokes, whole stack: `phrasing` goes
from 10 microseconds a stroke to 1.1, and the stack from 87 to 76. The other two
do not port. The engine already advances a candidate per dictionary rather than
walking from the start, and the reverse automaton `shrimple` no longer carries
was never in the Rust host's hot path.

## The remaining cost is one dictionary's frontier

Where a stroke goes in the Rust engine, 9,000 single strokes, each dictionary
alone against the whole stack's 0.68 seconds:

| | |
|---|---|
| `typey-type` | 0.63 s |
| `phrasing` | 0.01 s |
| the other twelve | 0.01 s each or less |

Eighty-one per cent of a stroke is one dictionary, and it is the frontier again:
46.6 paths a step, 719 at the widest. Nothing in the reader answers that. The
artifact would have to be delayed-aligned, which `hfst-lexc` cannot build at this
size, so the builder would have to be written here. At 76 microseconds against a
16 ms frame there is no reason to.

Pairwise alignment is still what makes the frontier wide, and it still stays:
`docs/M0.md` measured `hfst-lexc` at tens of gigabytes for a delayed-aligned
`typey-type`, so the builder that would fix it cannot be written with the tool
that builds the rest.

## Reading the format

`hfstol` has no specification we can cite; the layout comes from HFST's
`transducer.h` and the traversal rules from its reader. So the evidence that we
read it correctly is agreement with `hfst-optimized-lookup`:

```
scripts/crosscheck-ol.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml \
    dictionaries/typey-type-full.json
```

179,004 distinct tapes for `typey-type` and 36,377 for `emily-symbols`, both in
full agreement. The tapes are every outline in the dictionary, every tape in the
golden files, and 40,000 random key sequences. The random ones carry the weight:
a reader goes wrong on dead ends and partial paths, not on entries that exist.

Two things the reader refuses, both checked when the file is opened: weights, and
epsilon cycles that emit output. The second has no finite set of outputs, so the
closure would not terminate.
