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


....

Done (after this note)

- The lexicon. `scripts/gen-lexicon.py` writes `dictionaries/lexicon.lexc`: each word Typey Type writes that CMU pronounces, one entry per pronunciation, aligned by syllable, sounds then letters: report is `1 R=r IY0=e P=p AO1=o R.T=rt`. The weight is the zipf negated. 31279 of 35004 words align; the rest are mostly syncope (every) and a silent e inside a word (used). `dictionaries/aligned.xfst` names the language's pieces.
- Stages in the recipe. A `[[stage]]` is lexc, or xfst that defines `Stage`. A dictionary's `pipeline` names stages, and `stfst compile` composes them and writes what each prefix relates to `build/work/NAME/pipeline/STAGE.tsv`. `scripts/stages.sh NAME [--update]` holds those to `tests/golden/NAME/`, and check.sh runs it. `--pipeline-only` compiles the pipelines and no artifact.
- Two-syllable's rule is stages: lexicon, contract (two syllables to one, with marks), theory (a syllable's stroke), caret (the marks). The Python rule is gone. two-syllable.json is byte for byte what it was.
- Affixes' stems go through the same stages: gen-affixes lists them in `affixes-stems.lexc`, and the pipeline affix-stems, stem, theory, caret writes them. Its lexicon and golden file are unchanged.
- One resolver, `gen.resolve.resolve`, for both.

What changed from the plan, and why

- The artifact is still the resolver's table, not the stages with a small overlay. Of the 9047 words the two-syllable rule writes, the dictionary keeps 3848. The frequency floor, the inflections the suffix keys fold, and the inflection scope drop more than they keep, so a table of exceptions over the relation would be bigger than the result. The runtime does not change either way.
- A word's pronunciations are ranked, not weighted: each entry starts with its place in CMU's order, and the resolver takes a word's first. Frequency is the lexicon's weight, but nothing reads the weight yet: the resolver asks wordfreq, because it also ranks translations of other dictionaries, which are not in the lexicon.
- The alignment puts sounds before letters. A stage then reads a syllable's sound before its spelling, and contracting two syllables needs no reordering, which a transducer cannot do.
- Affixes' parse (prefix, stem, suffix) is still Python. Only its stems are stages.

Found

- Two things the Python rule did by accident, now explicit in contract.xfst: it re-spelled the second syllable as a word of its own, so gu before a vowel is a hard g (guile, disguise) and a y there is a consonant (cayenne).
- The first-stressed rule counts a final e as a vowel after l or r even where CMU says it is silent, so 68 words the rule could write get no stroke: agile, feature, future, measure, nature, culture, capsule. Kept, so the output did not change. Dropping the `[ l | r ] "_" e` case in `First` would add them.

Remains

- Affixes' parse as a stage over the lexicon, so it needs no stems file and no second compile.
- Typey Type's vowel theory itself (gen-typey-vowels) is still Python over Typey Type's strokes, and theory-tables.xfst's coda table comes from its result. The theory as stages over the lexicon would be the next step, and would let the coda table be a rule and not counts.
- The coda table holds 9178 codas no word has, as two that some do. A split in xfst would need a max over counts, so they are listed; they are most of theory-tables.xfst's 500 KB.
- The retro-words gap from agent-exp.md stands: gen-two-syllable reserves all 114 strokes retro-words.py lists, and the artifact answers 94. Whether it should answer the 20 menu rows is retro-words' question, not the pipeline's, so it is untouched.
- Costs: the two-syllable pipeline compiles in about 50 s, the lexicon is 2.3 MB and the stage golden files 900 KB.
