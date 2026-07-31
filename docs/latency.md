# Lookup latency

M0 asks for per-lookup latency against Plover's in-memory dictionary.
`docs/M0.md` could not answer it, because the only lookup available then was
`hfst-optimized-lookup` as a subprocess, and 8.5 us per probe was mostly process
startup. `stfst-ol` makes the measurement real.

`stfst bench`, release build, this machine (aarch64-darwin), 20,000 random
outlines from the dictionary, five passes:

| | typey-type | emily-symbols |
|---|---|---|
| open | 0.04 ms | 0.03 ms |
| median | 8.8 us | 0.83 us |
| p95 | 25 us | 1.8 us |
| p99 | 35 us | 2.3 us |
| throughput | 95,000/s | 1,048,000/s |

Against 0.34 us for the Python dictionary lookup. So a hand-written dictionary is
at parity and an imported one is 25 times slower, which is the honest comparison
and also not the interesting one: a stroke needs a handful of lookups, a frame is
16 ms, and the artifact is 15 times smaller.

Open is a `mmap` and a header read. Nothing is decoded, so a cold dictionary
costs its first few lookups and no startup.

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
