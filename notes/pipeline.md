❯ here's a last couple debriefing questions for the agent.
  1. What wasted tiem? What to improve Dev X wise in the stfst repo?
  2. I'm thinking about ways to represent dictionaries more as a pipeline. Like, for the 2-syllable thing... It's a transformation of "written out strokes". A more complete pipeline might be, notionally: A pronounced word in context -> { spelling, pronounciation, frequency rank... } -> apply phonetic contraction rules -> write it out accd to basic theory -> reorder syllables -> ^ contractions -> special case briefs for common words -> etc etc. Is this a good direction? To what extent does this require overhauling the basic technical choices of stfst?

....

2. Dictionaries as a pipeline

Yes, and it doesn't need an overhaul. Composing transducers is what an FST toolkit is built for.

Where each stage fits:

┌───────────────────────────────────┬──────────────────────────────────────────────────────────────┐
│               stage               │                         how it fits                          │
├───────────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ lexicon: word to spelling,        │ A lexc transducer. Frequency becomes a weight, which HFST    │
│ sounds, frequency                 │ supports.                                                    │
├───────────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ phonetic contraction, writing out │ Rewrite rules composed with .o.. Two-syllable, affixes and   │
│  by the theory, reordering        │ the vowel theory are all this.                               │
│ syllables, ^ contraction          │                                                              │
├───────────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ collisions: which word keeps an   │ Global. It depends on every word that competes for an        │
│ outline, and where the loser goes │ outline, and a loser falls back to its next-best outline.    │
│                                   │ That is an assignment problem, not a single FST operation.   │
├───────────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ briefs                            │ A priority layer, which the stack already is.                │
├───────────────────────────────────┼──────────────────────────────────────────────────────────────┤
│ "in context"                      │ A runtime decision (phrasing, retro-words), not dictionary   │
│                                   │ data.                                                        │
└───────────────────────────────────┴──────────────────────────────────────────────────────────────┘

What is missing is a split, not a new engine. Each JSON generator today mixes two things:

- The rule. It should be FST stages. Right now it is Python that repeats the theory.
- Resolution. It is generic, but written again in each script.

The smallest change has three parts:

1. A shared lexicon artifact. CMU pronunciations aligned to spelling, plus frequency. The letter-to-sound alignment is the one hard piece. The vowel theory's "cells" already do it in Python.
2. Stages named in the recipe and composed by the compiler. For example pipeline = ["lexicon", "theory", "caret"], with a golden file per stage so each step can be inspected. Composition inside one xfst file already works; the recipe just can't express stages yet.
3. One generic resolver. It enumerates the finite relation (about 150k words), picks a winner for each outline by weight and by what the stack already writes, and writes the losers and exceptions as a small table in a higher layer. Each dictionary then becomes FST stages plus that one resolver, not a script of its own.

The runtime (stfst-ol, the priority stack, the host) doesn't change: resolution removes the ambiguity before the artifact is written, so the artifact needs no weights. The size risk is composing the whole lexicon with every rule. Phrasing already compiles 21.9M chords, so that is probably fine.

Where I'd start: two-syllable, since its rule is already pure. Port its rule to xfst stages over the shared lexicon. Keep its resolution in Python, but pull that out into the generic resolver. Then affixes can reuse both the lexicon and the resolver.

