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
which is the ordinary number. The fold is a clone of a live candidate and the
inflection is one lookup.

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
suffix key per candidate on every stroke, and a fold can only matter on a stroke
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

**Fixed: a record used to lead with its flags.** `emily-symbols` reads `#TRU` and
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
us against a 16 ms frame, so this width stays and this is the reason why.

The two causes were independent, which the numbers show: reordering the record
did nothing at all for `typey-type`, whose records mostly carry no flags.

## Reading the format

`hfstol` has no specification we can cite; the layout comes from HFST's
`transducer.h` and the traversal rules from its reader. So the evidence that we
read it correctly is agreement with `hfst-optimized-lookup`:

```
scripts/crosscheck-ol.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml \
    ~/.config/plover/typey-type-full.json
```

179,004 distinct tapes for `typey-type` and 36,377 for `emily-symbols`, both in
full agreement. The tapes are every outline in the dictionary, every tape in the
golden files, and 40,000 random key sequences. The random ones carry the weight:
a reader goes wrong on dead ends and partial paths, not on entries that exist.

Two things the reader refuses, both checked when the file is opened: weights, and
epsilon cycles that emit output. The second has no finite set of outputs, so the
closure would not terminate.
