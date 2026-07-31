# What the artifact costs, and what delayed alignment would cost

`docs/M0.md` left an open question: 71,993 distinct records sit behind 145,340
outlines, so a pairwise-aligned artifact repeats translations that a
delayed-aligned one could share. The estimate was that this is redundancy worth
recovering, and recovering it needs a builder emitting ATT instead of going
through `lexc`.

The question is answerable without building anything, because the minimal
acyclic automaton over a set of strings is unique. So `scripts/encoding-
experiment.py` builds one per encoding from `stfst import --pairs` and counts.

`typey-type-full`, 145,340 entries, 1,752,964 input symbols and 1,256,702 output
symbols:

| encoding | states | arcs |
|---|---|---|
| inputs alone | 131,840 | 231,413 |
| records alone | 61,983 | 109,795 |
| pairwise | 274,327 | 398,875 |
| delayed | 591,146 | 729,667 |

**Delayed alignment is 1.8 times bigger, not smaller.** Sharing the record tails
does work, and it costs more than it saves: an entry becomes `len(in) + len(out)`
arcs where pairwise puts two symbols on one arc, and worse, the input side stops
merging. Two outlines that share a suffix can share states only if what follows
is the same, and what follows now includes the whole record. So the input
automaton degenerates towards a trie: 591,146 states against 131,840 for the
inputs on their own.

**Pairwise is already close to the floor.** The two sides taken separately need
341,208 arcs, and no single automaton can beat that while also relating them.
Pairwise spends 398,875, which is 17 per cent above a bound it cannot reach. The
redundancy the earlier note pointed at is 17 per cent at the very most, and the
encoding that would chase it costs 80 per cent more.

So the builder is not worth writing for size, and `lexc` stays on the import
path. `docs/latency.md` wanted the same builder for a different reason, and that
argument survives this one but does not carry it: delayed alignment would keep
the lookup frontier at one path, but a lookup already costs 8 us against a 16 ms
frame, and 1.8 times the artifact is a real cost on a phone.

## The size model, which is exact

An `hfstol` file is its header, its symbol table, an index table of 6 bytes a
slot and a transition table of 8 bytes an entry. The transition table holds one
entry per arc plus one terminator per state:

```
398,875 arcs + 274,327 states = 673,202 transition entries
```

The artifact has 673,202. Not approximately: exactly. And

```
143,071 index slots x 6  +  673,202 entries x 8  =  6,244,042
```

against a file of 6,244,829 bytes, the difference being the header and the 181
symbol names. So the automaton the experiment builds is the automaton HFST
builds, and any future question about size is arithmetic on states and arcs
rather than a compile and a `stat`.

Index slots are fewer than states because HFST gives a slot only to a state with
more than one input symbol; the rest are reached by pointing straight at their
transitions. That is the Liang compaction, and it is why the index table is 14
per cent of the file rather than 40.
