# Replacing the `^` XOR rule

The full study is [caret-replacement.html](caret-replacement.html). It has
every design scored, the charts, and the sample words. This is the short
version.

## Problem

A two-syllable word was one stroke marked `^`, with its stressed vowel's chord
XOR the other vowel's first letter (`caret.xfst`, `contract.xfst`,
`scripts/gen/two_syllable.py`). The XOR maps the 16 vowel chords onto
themselves, so the vowel keys read as an unrelated vowel (today `^TKAOEU`,
always `^ULS`, city `^S-T`). The stroke does not say which side of the stress
the other vowel is on, and `^` has three meanings.

## Scales

Numbers are comparable only within one scale.

| Tag | Metric | Words |
|---|---|---|
| resolved · 12,208 | Lost frequency, resolved against the compiled stack as `gen-two-syllable.py` resolves it | two-syllable dictionary |
| resolved + fallback · 12,208 | The same, with a fallback outline per word | the same |
| hard · 12,208 · no stack | Hard collision of first outlines, no stack | the same |
| hard by sound · 11,344 | Hard collision, words by sound from `lexicon.lexc`, homophones apart (floor 0.51%) | words both sources share |
| hard · 78,853 · handoff | The foot-zone handoff's own metric | its CMUdict words |
| hard · 3,606 · approx. | Hard collision among these words only | marked words with 2 vowels |

The voicing runs used a later stack build, where the baselines are 1.820%
(XOR) and 1.571% (letter) instead of 1.824% and 1.574%.

## Drop-in rules (resolved · 12,208)

| Rule | Lost | Words lost | Readings | Stressed readings |
|---|---|---|---|---|
| `^` XOR, today | 1.824% | 719 | 10.36 | 5.45 |
| No rule (Typey Type) | 1.475% | 1, and 747 with no outline; 1.252 strokes/word | | |
| **Letter**: `^` e or none, `^+` i/y, `+` a/o/u | **1.574%** | 589 | 4.17 | 1.00 |
| Side and y | 2.397% | 839 | 4.12 | 1.00 |
| Side | 3.349% | 1094 | 5.82 | 1.00 |
| Mark only | 4.061% | 1293 | 11.63 | 1.00 |
| Searched table on `^ +` (ceiling) | 1.466% | 570 | 3.94 | 1.00 |

## OR'd marker chords (resolved · 12,208)

The marker chord may also carry vowel keys or `*`, OR'd into the stressed
chord. A single `^` with the letter OR'd (or-letter) loses 2.471% and reads
like XOR: happy `^HAEUP`, and 36% of strokes hide the weak key. The best
readable design is **or-plus**, the letter rule with `+O` and `+U`: 1.340%
alone, but the vowel keys are literal in 86.5% of strokes, not 100% (today
`+TKAOEU`). The searched ceiling is about 0.95%, with no pattern.

## The fallback is memorization (resolved + fallback · 12,208)

A Typey Type outline that is not the theory's own two strokes follows no rule,
so a learner must memorize it. The theory's two strokes (`theory.tsv`, before
`contract.xfst` joins them) are a fallback by rule.

| Letter rule, then | Lost | Strokes/word | Memorized |
|---|---|---|---|
| nothing | 1.574% | 1.000 | 0 |
| Typey Type | 0.026% | 1.009 | 295 (1.092%) |
| **theory split** | **0.093%** | **1.015** | **0** |
| theory split, then Typey Type | 0.003% | 1.015 | 29 (0.090%) |

With Typey Type behind it, today's XOR needs 368 memorized words and mark only
578. Under every rule about half the Typey Type fallback words are memorized.

## Other lines of research

- **De novo foot-zone layouts** (hard by sound · 11,344). Free layouts reach
  0.58 to 0.68%, near the 0.51% floor, but replace every chord and drop
  spelling. Kept to stfst's chords on `^ +`, by sound loses 4.32%, against
  2.80% for the letter rule on the same scale.
- **Plain de novo board** (hard by sound · 11,344). One stroke per syllable on
  stfst's board. Ireland's chart loses 0.60%, a searched chart 0.51% (the
  floor), at 3.56 keys per stroke against 4.12 and 99% of clusters in steno
  order. Left order s k p m t n r l; right r n k l m p s t d v. The theory
  matters far more than the board: one stroke to one per syllable removes 3.72
  points on Ireland's chart; changing the chart removes at most 1.62.
- **Left-hand voicing key** (resolved · 12,208). Not worth it as stated. No key
  is free; on `+` the best variant (1.710%) is worse than the letter rule. On a
  key of its own it takes the letter rule from 1.571% to 1.453%, at the cost of
  rewriting 29% of Typey Type. Two-thirds of the gain is v alone (`SR` reads as
  re- or s-), which is the follow-up worth doing.
- **Six vowel keys** (hard · 3,606 · approx.). `~/.config/plover/VOWELS-DESIGN.md`
  §10. 1.41% (1.18% with `*`) against 1.01% for the letter rule, and it needs
  a new board.

## The study's recommendation

The letter rule, with the theory's two strokes as the fallback: money
`^PHOPB`, happy `^+HAP`, city `^+SEUT`, about `+PWOUT`, today `+TKAEU`; a
losing word such as animal falls back to `APB/PHAL`. 0.093% lost at 1.015
strokes per word, with nothing to memorize. The vowel keys stay literal.
Runners-up: or-plus, side and y, and mark only with the theory fallback.

## Decision: or-plus, then the theory, then Typey Type

stfst writes or-plus. The stroke is the stressed syllable as before, and the
other vowel's first letter is a mark whose keys are OR'd in
(`dictionaries/caret.xfst`):

| The other vowel | Mark | Example |
|---|---|---|
| a | `+` | about `+PWOUT` |
| e, r-coloured, or dropped by a rewrite | `^` | money `^PHOPB`, report `^PRORT`, attempt `^TEPLT` |
| i or y | `^+` | happy `^+HAP`, city `^+SEUT` |
| o | `+O` | today `+TKAOEU`, also `+AOLS` |
| u | `+U` | focus `+TPOEUBGS` |
| none: one syllable, its plain stroke taken | `^` | matter `^PHART`, since mart is `PHART` |

A word whose stroke is taken falls back to the two strokes `theory.tsv` writes
for it (animal `APB/PHAL`), and then to its Typey Type outlines, the superseded
ones among them. Two-word pairs and affix stems take the same mark, since they
run the same `caret.xfst`. The function-word folds read the mark back: `+` is
"a" before a word, `+` and S- "so", `+O` and -F "of", `+O` and -T "to", and
`^+` and -Z "is" (`folds.xfst`).

As built (`scripts/gen-two-syllable.py`): 491 words (1.280%) on the theory's
two strokes, 39 (0.117%) on Typey Type, 0.008% lost (8 inflections), 1.014
strokes per word. The study scored or-plus with the same fallback at 0.003%,
29 Typey Type words and 1.013. The gap is inflections: the study left an -s
or -ed form's outline as the XOR stroke with its suffix key, which no design
rewrote, while the pipeline writes it as the base's or-plus stroke and the
key. So an inflection collides as its base does (constituents and
constituency are both `^STEUPBTS`), and 75 of the 538 words without a stroke
of their own are inflections. `stfst-layout layouts/stfst-or-plus-theory.toml`
on the current stack agrees with the generator.

Later, the left bank stopped being a plain union, which split chords: `frontier`
was `+TPHRAOER`, where fr `TPR` holds the H of n `TPH`. Now the word's onset
stays, then the stressed onset where it splits nothing, then each sound that
crossed where it splits neither (`contract.xfst`), so `frontier` is `+TPRAOER`.
Strict steno order was tried first and lost more (report would be `^RORT`).

| Left bank | On the theory's two strokes | Strokes/word |
|---|---|---|
| Union | 491 (1.280%) | 1.014 |
| Strict steno order, word order wins | 591 (1.604%) | 1.017 |
| Chords whole, stressed onset wins | 555 (1.725%) | 1.018 |
| **Chords whole, word's onset wins** | **513 (1.326%)** | **1.014** |

These are from a simulation of the rule through the resolver.

Other dictionaries give `^` and `+` meanings of their own. Each holds its own
outlines, and the resolver leaves an outline another dictionary writes to it,
so where a mark lands on one the word falls back:

| Dictionary | `^` and `+` |
|---|---|
| affixes | the prefix a- is `^` on the affix stroke |
| phrasing, phrasing-tag | `#^` the full form, `#^+` the question tag, behind `#` |
| emily-modifiers | `@` with `^` or `+` is `S` |
| semantic-retro | lone `+` and `^+` strokes with no left bank (`+U`, `^+`) |
| retro-words | `^A` and its menus (`^AEP`) |
| repeat-word, repeat-idioms | a connective folded onto `^` (`^SKP`, `^O`) |
| ahead | `^` with `*` (`^PW*EU` "by _ing") |
| Shrimple | `^` alone enters fingerspelling |

## Open decisions

- Two-stroke fallbacks carry the usual word-boundary risk (`HAP/PEU`).
- An inflection with no stroke of its own has no fallback of its own either
  (outages, constituents); its base's fallback and the suffix key would be one.
- A chord for the bare mark (about +0.1 points); spelling variants as losses.
- A v chord; a skeleton theory for 3+ syllable words (3.1% of tokens).

## Running

```
cargo build --release -p stfst-layout
./target/release/stfst-layout layouts/stfst-letter.toml          # alone
./target/release/stfst-layout layouts/stfst-letter-split.toml    # theory fallback
./target/release/stfst-layout layouts/stfst-letter-theory.toml   # theory, then Typey Type
./target/release/stfst-layout layouts/stfst-or-plus.toml
./target/release/stfst-layout layouts/stfst-letter.toml --hard --eval
./target/release/stfst-layout layouts/novo-plain.toml
FOOT_STENO=<handoff dir> ./target/release/stfst-layout layouts/novo-a.toml
```

One resolve of the 12,208 words takes about 1 ms, so descent over a marker
table finishes in under 0.1 s. Hard-collision annealing is 3.5 to 4.5 times
faster per move than the handoff's numba solver; twelve seeds of 300k moves on
78,853 words run in parallel in 13 minutes. A first run of a design encodes its
words, which takes 40 s to 2 minutes.
