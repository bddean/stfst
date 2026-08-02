# Parity with Plover's translation language

What Plover 5.3 can say in a dictionary, and whether this format can say it. The
list comes from Plover's own tables rather than from its documentation: the
`plover.meta`, `plover.macro` and `plover.command` entry points, plus the
installed system's 38 orthography rules.

## The operators

| Plover | Here |
|---|---|
| `{^}` `{^^}` `{^ ^}` `{^a^}` | `[ATTACH_L]` `[ATTACH_R]`: close the gap |
| `{^ing}` | `[ORTHO_L]`: close the gap *and* apply the rules |
| `{&a}` | `[GLUE]` |
| `{-\|}` `{>}` `{<}` | `[CAP_NEXT]` `[LOWER_NEXT]` `[UPPER_NEXT]` |
| `{~\|'}` | `[CARRY_CAP]` |
| `{*!}` `{*?}` | `[CANCEL_SPACE]` `[RETRO_SPACE]` |
| `{$}` | `[WORD_END]` |
| `{.}` `{,}` `{?}` `{!}` `{:}` `{;}` | a record: `.[ATTACH_L][CAP_NEXT]` |
| `{#control(c)}` | `[KEY]c[CTRL][END]` |
| `{*-\|}` `{*<}` `{*>}` | `[META=retro.cap]` `[META=retro.upper]` `[META=retro.lower]` |
| `{*($c)}` | `[META=retro.currency]$c[END]` |
| `{mode:caps}` … `{mode:set_space:_}` | `[CMD=mode.caps]` … `[CMD=mode.set_space]_[END]` |
| `=undo` `{*+}` `{*}` | `[CMD=engine.undo]` `[CMD=engine.repeat_stroke]` `[CMD=engine.toggle_star]` |
| `{plover:…}` | `[CMD=plover.…]` |
| `{:infl:ed}`, `{:retro:past}` | `[META=infl]ed[END]`, `[META=clause]past[END]` |
| `{=regex/yes/no}` | **not expressible.** It reads the *next* translation |
| `{}` cancel formatting | not implemented |

Every name is a symbol from a table in `stfst-core::host`, not text. §3 had put
commands and metas in the "open-ended, so delimit" column; a host implements a
fixed set, so the set is closed, and enumerating it makes the load-time check the
same one-pass alphabet check as every other tag. A dictionary that asks for an
operation this host cannot do is refused when it loads.

That refusal has already earned itself: `semantic-retro.json` asks for
`[META=clause]`, the clause rewrites `plover-semantic-suffixes` does with a
morphology lexicon, and the reference host has no lexicon. So it is out of the
recipe instead of loading and quietly doing nothing.

The distinction between a command and a meta decides what happens when a host
cannot oblige. A **command** is a request, so an unimplemented one is declined and
the dictionary still loads: `plover.quit` means nothing on a phone. A **meta**
edits text, so an unimplemented one would leave the text quietly wrong, and the
dictionary is refused.

## Orthography, which is the operator that mattered

`{^ing}` is the most common directive in every real dictionary and it is not
"close the gap". Plover's English system carries 38 rewrite rules over the join
plus a word list to choose between candidates, so `run` and `ing` make `running`
and `box` and `s` make `boxes`.

The reference host implements 13 of the 38, hand written, because the rules cannot
be compiled by a Rust regex crate: rule 8 needs a lookbehind and neither `regex`
nor `regex-lite` has one. Coverage is measured rather than claimed. Over 40,000
pairs, every lowercase single-word translation in `typey-type-full.json` against
the ten commonest suffixes, with Plover's own `add_suffix` as the oracle:

```
40000 pairs, 39612 agree (99.0%)
  seduce + able: plover 'seducible', ours 'seducable'
  discover + ed: plover 'discovered', ours 'discoverred'
  signal + ed: plover 'signaled', ours 'signalled'
```

The residual is not the missing rules. It is the **word list**: Plover generates
candidates and prefers one that is a known word, which is how `discover` avoids
doubling and how `signal` picks the American spelling. That mechanism is a lexicon,
and a lexicon is an automaton, so the honest place for it is a compiled
orthography transducer that a host reads like any other artifact:
`PU(rule .o. InWordList, plain)`. Thirty-eight regexes in every host is the thing
this format exists to avoid.

## What the ecosystem needs that the format still cannot do

**Lookahead.** `{=regex/yes/no}` depends on the translation that has not arrived.
Every context mechanism here looks backwards. The state half of host contract 6
plus `[REPLACE1]` is the shape of an answer: write the guess, correct it next
stroke.

**A morphology lexicon.** `plover-semantic-suffixes` uses `lemminflect` for
`run`→`ran`, `child`→`children`, and a POS tag per word. That is a lexicon, which
is what HFST is for, and compiling one is a build job rather than a design
question. Until then `[META=infl]` does the regular cases and `[META=clause]` is
declared and unimplemented.

**A dynamic stack.** `plover-dict-commands` turns dictionaries on and off mid-run
and `plover_modal_dictionary` hides one until an activation stroke. The names are
in the table (`stack.toggle`, `stack.solo`, `stack.end_solo`, `stack.priority`);
`Stack` is immutable after `open`, and what a stack change means for a candidate
that spans it is undecided.

**Suffix folding across the stack.** Plover's translator strips a `SUFFIX_KEYS`
key from the last stroke, looks the base up in the whole stack, and appends the
suffix's own translation. That is the answer to §9's "where does a folding rule
live when the base word is in a lower-priority dictionary", and it is not in a
dictionary at all. `Session` opens one candidate per dictionary per start already,
so it is a small addition and it is not written yet.

## Two things measured on the way

**The hoist chain was a hidden `2^n`.** `Canonical` composed one hoist per flag
*with each other* before touching a grammar. Three new flags took it from 65,536
states to 786,432, and `emily-symbols` from 29 seconds to 4 minutes 25. Composing
them onto the grammar instead, left to right, keeps each step the size of the
grammar: 0.83 seconds, and the whole stack builds in 36. The flag count no longer
costs anything.

**An undefined identifier in xfst is a symbol, not an error.** So a grammar that
names something that does not exist composes with nothing and the artifact is
empty, and every other check passes trivially over no paths. The compiler now
tests for it. It found the fixture dictionary the same day it was written.
