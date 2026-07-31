# Lookup latency

M0 asks for per-lookup latency against Plover's in-memory dictionary.
`docs/M0.md` could not answer it, because the only lookup available then was
`hfst-optimized-lookup` as a subprocess, and 8.5 us per probe was mostly process
startup. `stfst-ol` makes the measurement real.

`stfst bench`, release build, this machine (aarch64-darwin), 20,000 random
outlines from the dictionary, five passes:

| | typey-type | emily-symbols |
|---|---|---|
| open | 0.02 ms | 0.02 ms |
| median | 8.3 us | 5.8 us |
| p95 | 25 us | 13 us |
| p99 | 35 us | 18 us |
| throughput | 97,000/s | 152,000/s |

Against 0.34 us for the Python dictionary lookup, so 25 times slower per
lookup. That is the honest comparison and it is also not the interesting one: a
stroke needs a handful of lookups, a frame is 16 ms, and the artifact is 15
times smaller. Nothing here is near the budget.

Open is a `mmap` and a header read. Nothing is decoded, so a cold dictionary
costs its first few lookups and no startup.

## What the cost is

A lookup costs about what the frontier is wide, and the frontier is wide:

| | paths per step | widest |
|---|---|---|
| typey-type | 34 | 732 |
| emily-symbols | 62 | 576 |

Both dictionaries emit output before the input that decides it has been read, so
the reader carries every guess forward and most of the guesses die. The two
causes are different, and both are ours rather than HFST's.

**A record puts its flags first, and the keys that decide them come last.**
`emily-symbols` reads `#TRU` and must already have emitted `[ATTACH_L]` or not,
though `-R` and `-B` have not arrived. So after the first key there are 96 live
paths, one per flag combination. This is what the canonical order in
`FST-DICT-DESIGN.md` §3 costs, and it is worth asking whether flags belong at the
end of a record instead: the flag keys are last in steno order, so a record that
ends with its flags could be emitted as it is read. Nothing else prefers flags
first, because `Record` parses tags wherever they fall.

**Pairwise alignment emits a translation from the first key.** `lexc` pairs the
two sides symbol by symbol, so `T:the quick` puts `t` on the `T` arc and the
whole rest of the translation on epsilon arcs that follow it immediately. Every
shorter entry sharing a prefix therefore hangs a dead output tail off our path,
and after one key `typey-type` has 68 live paths.

Delayed alignment predicts the opposite shape: nothing is emitted until the
outline is complete, so the frontier stays at one path through the input and
opens once at the end. That would be the fix, and `docs/encoding.md` prices it at
1.8 times the artifact. Not worth it at 8 us against a 16 ms frame, so the
frontier stays wide and this is the reason why.

## Reading the format

`hfstol` has no specification we can cite; the layout comes from HFST's
`transducer.h` and the traversal rules from its reader. So the evidence that we
read it correctly is agreement with `hfst-optimized-lookup`:

```
scripts/crosscheck-ol.sh build/dicts/typey-type.stfst systems/stenotype-extended.toml \
    ~/.config/plover/typey-type-full.json
```

179,002 distinct tapes for `typey-type` and 36,375 for `emily-symbols`, both in
full agreement. The tapes are every outline in the dictionary, every tape in the
golden files, and 40,000 random key sequences. The random ones carry the weight:
a reader goes wrong on dead ends and partial paths, not on entries that exist.

Two things the reader refuses, both checked when the file is opened: weights, and
epsilon cycles that emit output. The second has no finite set of outputs, so the
closure would not terminate.
