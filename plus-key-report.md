# Extended layout: `+`, frames and the next pipeline

The numbers below come from my scripts, not from the compiled stack, except where a section says the stack.

- Scratchpad: `/private/tmp/claude-501/-Users-ben--config-plover/9374fb5e-4665-4064-8f05-4ec0f6656f09/scratchpad/`, abbreviated `SP` below. It may be gone after a reboot.
- Corpus: news, web and wiki from `phrasing/corpora`, not your own register.

## 1. Two-syllable markers (`^`)

The detail is in [xor-conflicts.md](file:///Users/ben/Desktop/xor-conflicts.md). The sample is 3,804 words. Each row gives two-syllable losers, the losers of zipf > 4, and the one-syllable losers.

| scheme | result |
|---|---|
| `^` unless free (now) | 227 / 37 / 0 |
| `^` only on short words | 220 / 33 / 28 |
| no marker | 422 / 75 / 127 |
| Ireland layout: `*` always, `#` also when short | 232 / 36 / 10 |

- Ruled out: a contextual marker, -er with `R` on the left, a medial-cluster table, and a searched code table (it fits only its own word list).
- The star is overloaded mainly by the vowel spelling bit (ai `AEU*`, oa `OE*`).
- You are satisfied with `^`, so `+` and `@` won't be used for this.

## 2. Jobs for `+`

### Constraints

- `+^S` can't be stroked without `@`.
- `@` belongs to Emily's modifiers.
- `semantic-retro` uses `+` with no left consonant.
- `#` with one bank could be freed from phrasing.

### What the stack already does

The folds in `~/src/me/stfst/dictionaries/folds.xfst` already write:

- `-T`: the word, then "the".
- `SKP-`: "and", then the word.
- the `^` toggles: "a X", "X of", "X is", "X to".

So my first count of words stroked alone was wrong: it read only the JSON files. The count in §3 runs through the stack. `stfst translate` is now in `CLAUDE.md` for this kind of question.

### Ideas you liked

- 6: phrasal-verb particles.
- 8: `+` attaches the word, for compounds.
- 13–16: frames around a word.
- Shrimple on `+`.

## 3. Frames, measured against the compiled stack

- The sample is 40k sentences, 274k words. The stack writes it in 277,820 strokes.
- One-stroke spans came from `stfst reverse` over every artifact. Fold strokes were checked with `stfst translate`. A DP took the cheapest segmentation.
- Scripts: `SP/frames/` (`build.py`, `ab.py`, `rate.py`, `var.py`, `los.py`).

**A** puts `+` on the word's own stroke, and a toggled vowel chooses the frame.

| scheme | strokes saved |
|---|---|
| `+` = "the _" | 1.63% |
| plus `O` = "the _ of" | 1.78% |
| plus `U`/`OU` = "_ up"/"_ out" | 1.85% |

- Toggles after the first lose 40–70% of the time, because the toggled vowel usually lands on another word.
- "the _" loses 18%, but only because two-syllable words with `^` were excluded.

**B3** gives part of the board to a frame chord, and the word keeps the rest. There are 22 corpus frames: "the _", "the _ of", "of the _"…

| frame keys | win | strokes saved |
|---|---|---|
| left bank (the word keeps vowels and right) | 0.76 | 2.71% |
| vowels | 0.89 | 2.68% |
| `-D -Z` + vowels | 0.85 | 2.96% |
| `W- H- R- -D -Z` | **0.94** | **3.24%** |
| `-D -Z` only (3 frames) | 0.99 | 1.85% |

- Each frame has 300–1,000 fillers, so B3 only works as a rule: drop the frame keys and add the frame chord. What you learn is the losers.
- With `W H R D Z`, the frames "the _ of" and "the _" have 124 and 190 frequent losers: rest/west, right/white, man/plan, size/side, kind/kinds.
- Dropping `-D` and `-Z` also loses plurals and past tenses, so the frame needs an inflection bit.
- The reduced stroke isn't the word's outline, so B3 needs a generated table, not a fold.

Open questions:
- B3 with only the glides `W- H- R-` (7 frames, with D/Z kept for inflections). Not measured.
- Personal register. Not measured.
- Attach (8). This method can't count it, since compounds are single tokens.

## 4. Ties to TODO.bs and the pipeline rethink

Several items in [TODO.bs](file:///Users/ben/.config/plover/TODO.bs) are the same problem as the frames: a function word beside a word, written by rule.

- **"S- for 'is …', ST- for 'it's …'"** and **automatic "a"/"an", "of", "of the" (Pitman)** are frames. B3 and the folds are two ways to write them. One frame grammar could own the `^` toggles, the `-T`/`SKP-` folds and `+` frames together, instead of three mechanisms.
- **"to going" → "going to" by frequency** is the same as choosing a frame's winner by frequency.
- **Two-syllable words exclusively as contractions of written-out forms**, and **less python, pure transducer**: §1's analysis ran in Python on CMU data, outside the stack. Rules derived from written-out outlines would let the stack generate and check them itself.
- **Syllabification**: merge everything after the first consonant cluster onto the right side. This matches what §1 found: placing the medial consonants by stress was best, and the left bank was worst.
- **Pipeline:** my analyses kept going wrong when they read the JSON files instead of the stack, because the folds and phrasing were invisible to them. If every dictionary goes through stfst, `translate`, `reverse` and `dump` become the one way to ask what a stroke writes. Measurement scripts like these could then live in stfst: a corpus DP over the stack, giving strokes per word and the losers for a proposed rule.
