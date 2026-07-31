# Translation, and the longest key that is not declared

`stfst-dict` is the runtime half of the host contract, items 1 to 4 of
`FST-DICT-DESIGN.md` §5. It links neither HFST nor the compiler, so it is what an
embedded host imports.

## Longest match without a longest key

Plover stores `LONGEST_KEY` per dictionary and, on each stroke, looks up every
suffix of the history from that length down. It has to: a hash table can answer
"is this an entry" and not "is this the start of one", so there is no way to know
when to stop reaching back.

An automaton answers the second question directly. A cursor that has read strokes
`p..=t` is live if some entry begins that way and dead otherwise, and dead is
permanent. So a session keeps one cursor per start that is still live, advances
them all on each stroke, drops the dead, and opens one more at the current
stroke. The window of starts worth trying is discovered rather than declared.

Measured over 9,407 strokes of `typey-type` outlines with `emily-symbols` on top:

| | |
|---|---|
| wall clock | 0.118 s, or 12.5 us a stroke |
| candidates at the widest | 5 |
| longest match found | 7 strokes |

Five candidates, and that covers matches up to seven strokes long. The 12.5 us
includes parsing the outlines, both dictionaries, and printing.

Dead being permanent is also what makes undo cheap. A start that died at `t - 1`
cannot be live at `t`, so the oldest live start never moves backwards. Recording
it per stroke, one integer, is enough to rebuild the whole candidate set by
replaying from there. No cursor is cloned and no snapshot is kept.

## What a dictionary is checked for, and when

§5 asks that an unknown tag be a load-time error and not a runtime surprise.
That is cheap here, because every tag a dictionary can emit is a symbol in its
alphabet, and the compiler runs `hfst-prune-alphabet` so the alphabet is exactly
what the artifact can emit. Composition would otherwise leave the grammar's own
feature tags in it, and `[AL=0]` reaching a host as a tag it does not know is
precisely the surprise the contract is about.

So `Dict::open` refuses, before any stroke:

- a manifest wanting a host version above ours
- a dictionary declaring context tapes or translation-unit state, which are §5
  items 5 and 6 and not written yet, so it is unavailable rather than wrong
- a key the loaded system does not have, which is contract item 1
- a tag outside the record language
- a manifest naming a different system

## Not written yet

**Reverse lookup**, contract item 4. Inversion is a relabelling, but a lookup
over the inverted relation needs the inverted tables, because `hfstol` indexes by
input symbol. So it needs a `reverse.hfstol` beside the forward one, which the
compiler does not write yet.

**Context tapes and translation-unit state**, items 5 and 6. Refused at load, as
above.
