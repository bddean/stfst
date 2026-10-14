# Systematizing the theory

The morning report for the overnight run: a summary, then each phase's detail,
then the dev-experience notes.

## Summary

### What changed

- `^` contracts the word's written-out form, not its CMU pronunciation. Each
  syllable is written as the theory writes a one-syllable word. Where Typey
  Type has a two-stroke outline whose chords are the word's vowels, that
  outline is the written-out form; CMU supplies only the stress.
- Rewrites before `^`, each its own xfst stage: prefix clusters (also over
  outline strokes), suffix chords (-tion `-GS`, -ment, -ness, -ful, -less, -ly
  as L), lead (drop a leading unstressed or secondary-stressed syllable),
  glide, syncope, middle t/d.
- One stroke left is a plain stroke, or `^` on the same keys where a
  one-syllable word has it (matter `^PHART`, mare `^PHAEUR`). Two strokes are
  contracted. A word keeps every outline no other word takes.
- Two-word pairs go through the same stages (`join.xfst`). A pair stroke with
  a suffix key reads in the more common order (`^TKPW-G` "going to"). Folds:
  S- is "is", ST- is "it's".
- Fixed regressions: WR and other silent onset letters (the rule writes
  written as WREUPBT; your `^WRUPBT` stays in user.json), nightmare and mare, PW about and KWE
  question, DUPT's target dictionary, AULS always.
- Tooling: `make regen`, a per-stage compile cache, `--ablate STAGE`.

two-syllable.json: 4662 entries before the run, 19436 now (11347 words,
coverage 192.7, from 128.3 at phase 2's first measurement).

### Decisions, with their numbers

| decision | chosen | alternative | numbers (words, coverage) |
|---|---|---|---|
| XOR operand of a digraph | first letter | its chord | chord: +9 words, -0.1 coverage |
| -tion, -sion | affix chord `-GS` | its sounds | sounds: -125 words, -2.6 |
| -ly | its L | XOR y | L better on every count |
| syncope | before l, m, n, r | l, r only | l, r only: -282 words, -3 |
| prefix cluster merged with onset | merged (company TKPHEU) | apart | apart: -252 words |
| outlines per word | every free outline | first only | +79% entries, same words; costs two-word strokes |
| outline filter | chords must be the word's vowels | none | none: +214 words, mostly junk |
| -ment, -ness, -ful, -less chords | kept | none | +43 words, +0.6 |
| nasal crossing in contract | only to an empty onset | any | removed junk (control `^TKPHRAOEL`) |
| lead drop of a secondary-stressed prefix | yes | no | +201 words, +3.4 coverage (phase 5) |
| S-/ST- folds | "it's" before "is", before suffix folds | other orders | 93% of the use read right |

### What is left

- Rules still in Python: the affix parse and the vowel theory's rewrite of
  Typey Type's outlines. Phase 4 has the plan for both.
- user.json `^` briefs the rule writes differently: 26, listed under phase 3b.
- Stale, not updated: `~/.config/plover/vowels-cheatsheet.html`,
  `affixes-cheatsheet.html`, `cheatsheets.pdf` and `vowels-anki.csv`.
- Older measurements in VOWELS-DESIGN.md §8 and §13 are the first version's.
  The new "pipeline as it is now" list says where they no longer hold.
- Restart Plover to load the new artifacts in build/dicts.

## Phase 1: regressions that are not new rules

### DUPT wrote to stfst-user.json

DUPT is `TKUPT`, `{PLOVER:ADD_OUTLINE}`. Between Sep 29 and Oct 7, Plover
saved 59 of the desktop's entries into `stfst-user.json`, PW and KWE among them.
I could not reproduce the cause: that plover.cfg listed `user.json` first, and
Plover's dialog picks the first writable dictionary. Since 55075f4, Plover loads
`user.stfst` and `stfst-user.stfst`. I loaded both under Plover's python, and
the first writable is `user.stfst`, whose file is `user.json`. Your Oct 11
briefs went there. `check-plover-cfg.py` holds the order.

- The 51 entries `user.json` did not have moved there. `stfst-user.json` is `{}`
  again, so it holds only what the phone defines.
- `^WEU`: `user.json` said "in which" and `stfst-user.json` said "away". I kept
  "in which".
- If the phone still has these entries, `make sync-user` brings them back into
  `stfst-user.json`. That does no harm.
- Plover kept writing to `user.json` during the night (`SKRET`, `SW-RG`). I left
  those lines uncommitted.

### Short briefs dropped (PW about, KWE question)

Cause: `vowels.supersede` moved a Typey Type outline to `typey-superseded.json`
when its stroke count equalled the rule's. A one-stroke brief and a one-stroke
`^` contraction have the same count, so PW lost to `^PWAOUT`. Now an outline is
superseded only when it is as long as the rule's shortest outline in strokes
and in keys, with `^` not counted. A fold counts one key more.

- 2018 outlines of 1515 words come back from `typey-superseded.json`
  (5630 to 3621). 751 of the words have zipf >= 4, and 138 have zipf >= 5:
  also HR-S, other OER, very SRE, really R-L, after AF/AFT, because PWAUZ,
  money PHUPB.
- REUFR river still goes, because it has the same 5 keys as `^REUFR`. That is
  phase 2's syncope rule.

### Missing words (nightmare, mare)

There are two causes.

- nightmare: upstream `TPHAOEUT/PHAEUR` was rewritten to `TPHAO*EUT/PHAEUR`,
  which is knight plus mayor. The long-outline rewrite gave the night stroke the
  igh rule's chord, but "night" by itself kept TPHAOEUT. Now a stroke of a
  longer outline stays the same when it writes a word that the longer word
  contains, on its own (night in nightmare, show in showman, new in newborn).
- mare: Typey Type does not have the word, and the lexicon took only Typey
  Type's words. The lexicon now also takes words that lemminflect knows, that
  CMU pronounces and that have zipf >= 3. lemminflect lists no names. It does
  not take a word that Typey Type has with a capital letter (Welsh). The
  lexicon grows from 31279 to 32643 aligned words.
- The two-syllable pipeline is now `lexicon, stem, theory, caret`. `stem` is
  one.xfst: one syllable as it is, or two contracted. So `two-syllable.json`
  also writes a common one-syllable word that Typey Type does not have, with
  no `^`. A Typey Type entry never gives way to such a word. The old `contract`
  stage had no other user and is gone.
- Result: few one-syllable words get a stroke (sheikh SHA*EBG, tor, hove),
  because most of these words collide. mare is PHAEUR, which is mayor's stroke,
  so mare still has no generated entry. Phase 2's fallback rule (the plain
  stroke takes `^` when it collides) should give it one. Your `PHAER` brief
  writes it until then. two-syllable.json goes from 4662 to 4854 entries.
  Most of the new entries are two-syllable words from the wider lexicon.

### Silent onset letters (^RUPBT for written)

Cause: theory.xfst wrote the onset from its sounds alone. A new table,
`OnsetSpelled`, is derived like the coda tables. It covers an onset that the
vowel theory's one-syllable words write with more keys than its sounds have.
At least 3 words must do so, and they must be the majority. The table has 5
rows:

| sound | letters | keys | words |
|---|---|---|---|
| R | wr | WR | write, wrist |
| W | wh | WH | what, when |
| HH | wh | WH | who |
| (none) | h | H | hour, honest |
| K | ch | KH | chord |

kn and gn stay TPH, as the one-syllable words write them. written is now
`^WRUPBT`, whisper `^WHEUFRP`, wrestle `^WR-LS`, honest `^HOEFPBT`, chorus
`^KHOURS`. This change is in the per-syllable theory, so phase 2's written-out
strokes get it for free.

### For later phases

- user.json `^` entries the rule writes differently (14): access, anoint,
  around, await, empty, epic, frustrate, modern, public, reduced, rely, rematch,
  very (`^SRU`/`^SRUR`), water. These are in the gen-two-syllable report.
- Lower case plurals of names still get through, because lemminflect knows
  "smith": smiths. A frequency floor or a case check on the stem would stop it.
- affixes still uses Typey Type's words as its scope, not the lexicon.

## Phase 2: the ^ contraction reads the written-out strokes

### What the pipeline is now

`lexicon, written, prefix, suffix, lead, glide, syncope, middle, contract,
theory, caret`. `written.xfst` gives each syllable its chord, with CMU's
stress. Each rewrite is its own stage and file, so a rule can be dropped from
the recipe and measured:

| stage | rule | first or second choice |
|---|---|---|
| prefix | dis/des/de+c STK, int SPW, ex KP, comb/em/im KPW, com KPH | only |
| suffix | -tion and kin as -GS, -ly as its L | only |
| lead | a leading unstressed syllable dropped for `^`; a closing nasal goes with it (un, in, con) | first when it has no onset or is con/com, else second |
| glide | unstressed u, ou, oo before a vowel is w | only |
| syncope | unstressed vowel after the stress, between a consonant and l, m, n, r | first |
| middle | t or d inside a cluster | second |

One syllable left is a plain stroke, or the same keys with `^` where the
plain stroke is taken (mare is mayor's PHAEUR; matter is `^PHART`). Two are
`^` and the XOR. river is REUFR, stable STAEUBL, memory `^PHURPL`, disappoint
STKPOEUPBT, nation `^TPHAEUGS`.

Also changed:

- The resolver works in rounds by cost. A word's cost is the number of
  rewrites it has another outline without, less the cost all its outlines
  share. An outline goes to the word that wants it at the least cost, then to
  the more frequent. Without the "less", throughout lost its only outline to
  outta.
- A plain stroke never takes a Typey Type outline, including one that
  typey-superseded.json holds. Without that, the two-syllable and vowels
  generators traded about 40 strokes every round and never reached a fixed
  point (ankle and ankles, sadly and salad).
- A long vowel before a consonant plus l or r is open: table is TAEUBL, not
  TABL.
- The lexicon splits ou and oo said u as one vowel, so pirouette's ou is the
  glide's. CMU has no t in pirouette, so its stroke is `^PWRU`, and the word
  is below the zipf floor anyway.

two-syllable.json goes from 4854 entries (4049 words) to 7184 (6106 words).
Of the words that had an entry, 88 have none now. Most of these lose their
outline to a more frequent word (courage to encourage, magic to imagine).

### Measurements

Each row is one change from the chosen pipeline. "Words" is the words with an
entry. "Coverage" is the sum of 10^zipf over those words, divided by 10^6, out
of about 157. "Lose" is the words in scope that get no outline because a
higher word took it.

| variant                                      | words | coverage | lose | user.json briefs reproduced |
|---                                           |---    |---       |---   |---                          |
| chosen                                       | 6097  | 128.3    | 392  | 42                          |
| -ly XORs y (old rule)                        | 6072  | 128.0    | 407  | 38                          |
| digraph XORs its chord, not its first letter | 6081  | 127.9    | 396  | 38                          |
| -tion by its sounds, not -GS                 | 5947  | 125.4    | 411  | 34                          |
| syncope only before l and r                  | 5790  | 125.0    | 447  | 37                          |
| lead drop as second choice only              | 6063  | 128.0    | 402  | 37                          |
| prefix cluster kept apart from the onset     | 5820  | 124.2    | 389  | 34                          |

The rows after the -ly row each change that row's pipeline, not "chosen".

Decisions:

- XOR operand: first letter, as decided. The chord gains 9 words, loses 0.1
  coverage, and writes money `^PHAEPB` where the first letter writes
  `^PHOEPB`. `DigraphOp` in contract.xfst is the one-line switch.
- -tion: the affix chord. Sounds lose 125 words and 2.6 coverage, and your
  briefs use -GS (vision, revision, attention, addition, deception).
- -ly: its L, which uses the syllable as -er's R does. sadly is `^SALD`
  (SALD is salad), kindly KAOEUPBLD. This is better on every count. -ing stays
  the runtime -G fold.
- Syncope before m and n stays. Without it, the pipeline loses 282 words and
  3 coverage. The cost: written is WREUPBT, not your `^WRUPBT`.
- Leading syllable. As a first choice for every syllable, it wrote director
  `^RERBGT` and hotel `^TEL`. So it is narrowed: it is first only with no
  onset or for con/com, and a second choice for the rest. As first or second
  for the narrowed set, the numbers are a draw. Your briefs drop it (append,
  another, anoint, container), so it is first.
- A prefix cluster that merges with the onset (company KPH+P is TKPHEU) stays.
  Keeping them apart loses 252 words.

Collisions by rule. For each rule: the words in scope it rewrites, how many of
them lose their outline, and how many take another word's outline. The
generator's report prints this table.

| rule | rewrites | lose | take |
|---|---|---|---|
| prefix | 678 | 32 | 28 |
| suffix | 590 | 22 | 13 |
| lead | 2577 | 164 | 133 |
| glide | 15 | 2 | 0 |
| syncope | 3208 | 71 | 74 |
| middle | 1001 | 14 | 25 |

lead has the most collisions, at 6%. Most of the words that lose had no
outline before the rule (unhappy to happy, unsure to ensure). No rule's
collision rate is more than 6%, so none was dropped.

### user.json

- 44 briefs that the rules now write left user.json for
  `tests/briefs/two-syllable.tsv` (40) and `tests/briefs/two-word.tsv` (4).
  `--check` fails if a rule stops writing one.
- 4 stay because the rule writes them only while your entry is there:
  PAEURPBT patient (Typey Type's parent), STKORD discord (the rule gives it to
  disorder), `^AOUFL` all of, `^TPOUFL` full of.
- I deleted no entry as an obvious error.
- No new Plover entries were uncommitted at the start of this phase.

The `^` briefs that the rule writes differently (yours / the rule's):

- Leading syllable not dropped: addition `^TKAEUGS`/`^TKEUGS` and attention
  `^TAEPBGS`/`^TEPBGS`. You XOR the a here, but drop it in append and
  another.
- The vowel table's chord: container `^TAEURPB`/`^TA*EURPB`, encourage
  `^KOURPBLG`/`^KAOURPBLG`, public `^PEBLG`/`^P*EBLG`.
- Syncope: written `^WRUPBT`/WREUPBT, paper `^PAEURP`/PAEURP, water
  `^WART`/WAURT, modern `^PHOERPBD`/PHORPBD, happiness
  `^HAEUPBS`/`^HAEPBS`.
- Syllabification around the stress (decided): epic `^PUBG`/`^UPBG`, isolate
  `^SHRAT`/`^OEULTS`.
- Other rules or drops the pipeline does not make: advantage, disadvantage,
  rematch, rely, very (`^SRU`, R dropped), implicit, unpleasant, unfortunate,
  uncertain, regardless, frustrate, reduced, semantics, revision `^SREUGS`
  (the rule writes `^SRUGS`, which you also have), access, accessory,
  enormous, empty (`TKPWEU`, the em prefix merged with t), around, await,
  another.
- STK and SPW briefs that the rule does not write: decline, disgust (you
  drop the g), disposal, discovery, internet (`STPWHRET`), intelligence,
  integrate, beside, and the phrases (so good, do something).

### Left for later

- A Typey Type two-stroke outline as input to `^` (PEUR/WET) is not done. The
  pipeline reaches pirouette from CMU, through the glide.
- -ment, -ness, -ful and -less could be suffix chords, as -tion is. Your
  improvement `^PROFPLT` suggests -ment.
- The stage golden files for two-syllable are now 17 MB, because each rewrite
  stage lists the whole lexicon.

## Phase 3: one contraction over two strokes

Stopped early: usage credits ran out. check.sh was not run.

### Done (3 commits)

- `join.xfst` makes a two-word pair one written-out word of two syllables,
  so `contract-pair.xfst` is gone and pairs go through the same split,
  theory and contract stages as words. contract.xfst no longer drops a word
  for a final e or a vowel-vowel run (the lexicon aligns those), and says a
  consonant on both sides of the dropped vowel once. two-syllable: 6106 to
  6230 words, coverage 128.5 to 130.4 (maybe, idea, science, quiet, poem).
  two-word: 3775 pairs with a stroke, up from 3723, none lost.
- The pipeline is now `... middle, split, theory, contract, caret`.
  `split.xfst` splits around the stressed vowel and heads each syllable with
  `1:` or `0<operand>:`. `theory.xfst` writes each syllable as a stroke.
  `contract.xfst` works on keys: it unions the two strokes' banks, moves keys
  between the strokes across banks by their sound (-PB is TPH, TK is -D), and
  carries the operand to the mark. A cluster no longer needs keys as a whole.
  Also: an H after a consonant in an onset is no key (upheld `^PELD`), W and H
  in a coda are none (hallway `^HUL`), and an H, W after P/K, or KWR at the
  start of the stressed stroke is dropped after keys the other stroke brought
  (perhaps `^PRAEPS`, do you). two-syllable: 7212 words, coverage 141.9;
  two-word 4060 pairs.
- A word's Typey Type outline of two strokes (typey-type-full, composed and
  superseded) is a lexicon entry, stress from CMU (`stressed_stroke` in
  gen/lexicon.py), and the source of its ^ stroke: pirouette is `^PWRUT`.
  14947 words have one. Their CMU entries are flagged `@` and taken only
  where a rewrite shortened them, so REUFR river and the prefix clusters stay.
- Rewrites flag what they did in the rank (`!` a dropped syllable, `&` an
  affix chord). split.xfst takes a form with at most one dropped syllable.
- Result: two-syllable.json 8825 words, coverage 164.4 (from 128.5 at the
  start of the phase). 414 words lose their stroke, mostly to a more frequent
  word: although, available, damage, responsible.
- kindly, disable, disappoint, update went back from tests/briefs to
  user.json: the rule now writes them otherwise (disappoint's outline
  TKEUS/POEUPBT has no prefix rewrite, so it is `^STKPOEPBT`).

### Not measured separately

The cap on dropped syllables and the outline source landed in one run.
Toggle `Taken` in split.xfst (drop the `~$[ Dropped ?* Dropped ]` term) and
rerun gen-two-syllable to measure the cap alone (6 minutes).

### Half done, not committed

- Two-word order (task 2). Cause found: `^TKPW` is "to go", and the -G fold
  inflects the last word, so `^TKPWG` is "to going". The fix drafted: for each
  pair stroke and suffix key, compare the corpus counts of the stressed word
  inflected on either side of the other word against the fold's reading, and
  write an entry where the reordered text wins (`^TKPWG` "going to"). It is a
  function `inflected(out, taken)` in gen/two_word.py, called after the
  folded pairs are removed. Reverted unrun.
- S- "is" and ST- "it's" (task 3) as folds: two `Rule`s in folds.xfst after
  the "and" rules, `Before([ S T ?+ ], {it's}, S T)` then `Before([ S ?+ ],
  {is}, S)`, with `Before(C, Text, Keys) Rule([ [ Stroke & C ] - Caret ],
  [ "[FOLD]" Text "[STRIP]" Keys "[END]" ])` defined after `Caret`. Reverted
  unmeasured. The measure: over ("is"|"it's", w) bigrams, add S/ST to w's
  one-stroke outlines, skip strokes an entry holds, and count what
  `Stack.folds` writes; compare the order of the two rules and placing them
  before or after the suffix folds.

### Next step, exactly

1. Regenerate two-word and affixes (the stage files changed), then
   gen-typey-vowels and gen-two-syllable in turn until `--check` passes, then
   `scripts/stages.sh NAME --update` for the three, and run check.sh. The
   fixtures under tests/translate, tests/editable and tests/hole will need
   their candidate counts and strokes updated, as phase 2's did.
2. Read the outline-derived strokes for junk: Typey briefs as outlines give
   odd contractions (control `^TKPHRAOEL`, actually `^TAOEL`). A filter on
   which outlines count as written out (both strokes the word's own
   syllables, as `vowels.align` checks) is the likely fix.
3. Prefix rewrites over outline strokes (TKEUS/... as STK) so disappoint is
   STKPOEUPBT again.
4. Tasks 2 and 3 above, then the -ment/-ness/-ful/-less suffix chords.

## Phase 3b: finishing phase 3, and your notes from the evening

check.sh passes at 870b4b3, once, at the end.

### Every free outline, and which rules write them

The resolver now gives a word each outline no other word takes, after its
first. Cost decides only contested strokes. "Words" and "coverage" are as in
phase 2 (coverage: the sum of 10^zipf over words with a stroke, / 10^6).

| two-syllable.json | entries | words | coverage | lose | user.json briefs reproduced |
|---|---|---|---|---|---|
| first outline only | 10996 | 11381 | 192.5 | 545 | 18 |
| every free outline (chosen) | 19732 | 11381 | 192.5 | 546 | 30 |

So the dictionary grows 79% and the words do not change: the extra entries
are second ways to write the same words. 4261 of them are bare (no `^`). The
cost is in two-word: pairs that leave their stroke to two-syllable go from
402 to 605. If that matters more than the second outlines, give the extras a
lower priority than two-word's first outlines (an "extras" layer below
two-word), or keep extras only where no pair wants the stroke.

Each rule below, measured by dropping it from the chosen pipeline (every free
outline in all rows):

| variant | entries | words | coverage | lose | briefs |
|---|---|---|---|---|---|
| chosen | 19732 | 11381 | 192.5 | 546 | 30 |
| no -ment/-ness/-ful/-less chords | 19605 | 11338 | 191.9 | 546 | 29 |
| lead as in phase 3 (drop whole syllable, any length) | 19992 | 11379 | 192.7 | 551 | 29 |
| no prefix cluster over outline strokes | 19956 | 11386 | 192.5 | 545 | 28 |
| no iron rule (r-coloured vowel fused) | 19721 | 11375 | 192.3 | 543 | 30 |
| no written-out filter on outlines | 20913 | 11595 | 194.4 | 515 | 30 |
| no filler strip (KWR, SKWR, W) | 19837 | 11388 | 192.5 | 541 | 30 |

Decisions:

- Lead (your attempt and creative notes). A leading syllable is dropped first
  only in a word of three syllables or more, a prefix counted. One with an
  onset keeps it: only its vowel goes (creative `^KRAFT`, not `^AFT`). A word
  of two syllables is contracted whole first, and the onsetless or con/com
  drop is one rewrite later, so with every free outline it can have both:
  anoint `^TPHAOEUPBT` and `^TPHOEUPBT`. attempt is `^TAEPLT` only, because
  teammate takes `^TEPLT`. The numbers are a draw with phase 3's lead.
- Outline filter (task 2). An outline counts as written out when its
  stressed chord is the stressed vowel's short or long chord, the vowel
  table's, or spells one of its letters, and the other chord does the same
  for another vowel. It drops 663 of 14947 words' outlines: actually's
  TAOUL/HREU, control's KAUPB/TROEL, attempt's A/TOEPLT. It costs 214 words
  and 1.9 coverage, most of them words whose brief-outline wrote a junk
  stroke. vowels.align was stricter (7987 of 14947 words, coverage 86.9 of
  142) and rejects pirouette, so I did not use it.
- Fillers (your makeup note). KWR, SKWR and W (W only after an o or u
  stroke) are dropped from an outline stroke's onset when the word has no Y,
  JH or W sound: makeup is `^PHAEPBG`. pirouette keeps its W (its first
  stroke is i). 7 words fewer.
- Prefix over outline strokes (task 3). TK0:EUs/TK0:Es as STK, 0:EUpbt SPW,
  0:Ebgs KP, K0:Opl KPW before PW or else KPH, 0:Epl/0:EUpl KPW. disappoint
  is STKPOEUPBT again (and `^STKPAOEUPBT`). Of the other rewrites: suffix,
  lead, glide, syncope and middle are about sounds and do not apply to
  outline strokes. A two-stroke outline is already the written-out result,
  and those rules would make it one syllable, which Typey Type writes as its
  own one-stroke outline anyway. Only the prefix cluster is a steno
  convention, so only it applies.
- iron (your note). An r-coloured unstressed vowel right after the stressed
  one, with no consonant between, is in its syllable: iron `AOEURPB` and
  `^AOEURPB`. fire, hour and tire were one syllable already in CMU.
- -ment, -ness, -ful, -less as -PLT, -PBS, -FL, -LS: +43 words, +0.6
  coverage, and it writes your PHOEPLT moment. Kept.
- The lexicon ordered a word's outlines by which Typey Type file held them,
  which moved each time the vowel generator superseded one, so the
  generators did not reach a fixed point. They are ordered by keys now.

### Superseded Typey Type outlines

The committed typey-superseded.json was stale after phase 3. Regenerated, it
moved many frequent one-stroke outlines. Part of that was the extra outlines:
`^AFR` for after superseded AFT. So an outline is now superseded only when it
has at least as many keys as each of the rule's outlines of as many strokes.
That keeps AFT, HREUL little, ERL early and TPHAO* into. Newly superseded
by phase 1's rule, because phase 3's outline source writes the word in as
many keys or fewer: AULS always (`^ULS`), SPH-PB someone, TPHARBL national.
(TPHEU any and OEPBL only were superseded before this phase.) typey-superseded.json: 3319 outlines at phase 3's commit,
2896 now. The hole fixture writes always as `^ULS` now. If you want AULS
back, the simplest rule is that a word above zipf 5.5 keeps its Typey Type
outlines (31 outlines).

### Two-word order and the S-/ST- folds (task 4)

- A pair's stroke with a suffix key becomes an entry where the stressed word
  inflected, before or after the other, is in the corpora at least 150 times
  and more often than what the fold writes: `^TKPW-G` "going to", `^WAOPBTD`
  "wanted to", `^PHEUBGS` "makes a". 181 entries. The stressed word must be a
  content word ("a" + -S is not "as").
- folds.xfst: S- on a word's stroke is "is", unless S-K-P- says "and", and
  ST- is "it's" where T- and the rest is no word: STPHOU "is now", STPWAD
  "it's bad". Over the "is"/"it's" bigrams (at least 50 uses) whose stroke no
  entry holds, this reads 93% of the use right. "it's" first: 87%. With a T-
  word's T put back after ST- (as after "and"): 66%. After the suffix folds:
  77%. 67% of all such bigram use is on strokes an entry already holds.

### user.json

- 24 entries the rule now writes moved to tests/briefs/two-syllable.tsv
  (uncertain, addition, attention, crayon, modern, paper, public, await,
  rely, around, empty, disappear, sleepless, departure, detector, explore,
  correctly, kindly x2, curtain, moment, disable, disappoint, update).
- 7 stay because the rule writes them only while your entry is there:
  written `^WRUPBT`, happiness `^HAEUPBS`, inbox, discord, discrete (discreet
  takes STKRAOET), garden, patient.
- `^` briefs the rule writes differently (yours / the rule's first), 26:
  access, advantage, another, argue, assemble, avalanche, beyond, container,
  disadvantage, encourage, enormous, epic, episode, frustrate, implicit,
  isolate, nowhere, potential, reduced, regardless, revision, unfortunate,
  unpleasant, very `^SRU`/`^SRUR`, water `^WART`/WAURT. The report of
  gen-two-syllable lists them with strokes.

### Left

- Two-word's 203 pairs lost to two-syllable's extra outlines (above).
- AULS always and the other frequent outlines superseded by phase 1's rule.
- control is `^TROL`, TKPHREL and `^TKPHR-L`: contract.xfst moves con's -PB
  to the left bank as TPH. A lead drop for con before a cluster would be the
  cleaner first choice.
- semantics: the report says your `^SPHAEUPBGTS` differs, but the rule writes
  it by the -S fold.

## Phase 4: tooling, the evening's fixes, and the plan for the rest

### Tooling

- `make regen` (scripts/regen.sh) compiles the stack, runs each generator's
  `--check`, reruns the stale ones in dependency order (typey-vowels, lexicon,
  theory, two-syllable, two-word, affixes), and repeats until every check
  passes, failing after 5 rounds. Then it writes the stage golden files.
  `--composed` runs find-typey-composed.py first. From phase 3b's tree it
  settled in 2 rounds, so the vowels/two-syllable oscillation did not return.
- `stfst compile` caches each stage's net and each prefix's net and file
  under build/work/cache by a hash of what they are built from, so an
  unchanged pipeline costs nothing and a change to one stage rebuilds that
  stage and the prefixes after it. A forced two-syllable compile is 7.5 min;
  a cached one is under a second. Entries no compile has used for 3 days are
  removed.
- `stfst compile --dict NAME --ablate STAGE` writes the pipeline without
  that stage to build/work/NAME/without-STAGE, and no artifact.

### Fixes

- A Typey Type outline of one stroke is never superseded: AULS always,
  SPH-PB someone, TPHARBL national are back. typey-superseded.json: 2896
  outlines to 290.
- A pair's first outline in two-word outranks a two-syllable word's second
  and later outlines. Pairs that leave their stroke to two-syllable: 605 to
  410. anoint's `^TPHOEUPBT` is "not in"'s first outline, so your brief went
  back to user.json.
- control: contract.xfst moved con's -PB to the left bank as TPH before the
  stressed onset (TKPHR). No English onset has a nasal before a consonant, so
  a nasal coda now crosses only to an empty onset. control is `^TROL` only;
  the same junk is gone from include (TKPHRAOED), increase, enhance,
  concede, consult and himself (`^SPHUF`).

		** REVIEW: does this increase collisions? we don't necessarily need
		** to be cosntrained here by phonotactics. If this reduces coverage,
		** scrap it; altho otherwise, reducign keys needing to be pressed +
		** making the briefs prnounceable (= more memorable) is good.

Answer: it reduces coverage, and it adds collisions. Measured at phase 5's tip
(the secondary-stress lead drop in), with and without the rule:

| two-syllable.json | entries | words | coverage | lose |
|---|---|---|---|---|
| with the rule (phase 5) | 19436 | 11347 | 192.7 | 599 |
| without | 20021 | 11489 | 194.1 | 581 |

Without the rule, 166 words gain a stroke and 36 lose theirs. Most gains are
the unpronounceable kind (champagne `KPHEUPB`, concede `STKPHAOED`, consist
`STKPHO*EUS`, contempt `TKPHOEFRP`), but some are plain strokes freed by the
shuffle (confirmed `^TPEURPLD`, assumed `^SAOUPLD`). The losses are mostly
plurals and -ed forms (effects, events, arrested, affairs). So the rule is
reverted: contract.xfst is back to before it, and the dictionaries and stage
golden files are regenerated (uncommitted).

| two-syllable.json | entries | words | coverage | lose |
|---|---|---|---|---|
| phase 3b | 19732 | 11381 | 192.5 | 546 |
| now | 19058 | 11146 | 189.3 | 520 |

214 words lost their stroke and 66 gained one. 160 of the 214 had only a
nasal-crossing contraction (include, increase, enforce, confirmed); in-, en-
and con- words with secondary stress on the prefix get no lead drop, so
nothing replaces it. If you want them back, let the lead rule drop a prefix
syllable with secondary stress too.

### Rules into xfst: not done, the plan

The affix parse was not straightforward, so it stays in Python. What it
needs, in order:

1. affixes.toml as xfst: gen-affixes writes each prefix and suffix as a
   relation over the lexicon's segments (spelling and sounds), not over raw
   CMU phonemes. The lexicon aligns by syllable, and an affix's boundary can
   fall inside a segment (in-vi-ta-tion: `N.V=nv`), so the stage must split a
   consonant segment at the affix's edge.
2. A `parse` stage over lexicon.lexc: prefix, a stem of one or two syllables
   with the more stressed vowel re-marked 1, suffix. The doubled consonant
   (com-mission) is the stem's.
3. Longest affixes first, as a priority union (`PU` in aligned.xfst), which
   replaces affixes-stems.lexc and the second compile.
4. The stem then goes through written, split, theory, contract and caret as
   now. Python keeps the misread count (corpora) and resolution.

The vowel theory (gen/vowels.py) is about 900 lines: the vowel table already
lives in theory-tables.xfst. What is left in Python is the rewrite of Typey
Type's outlines (aligning each stroke to a syllable, which needs Typey Type's
strokes as a stage input), collisions and displacement (resolution, which
stays Python), vowel briefs and superseding (stack questions, Python). Order:
first Typey Type's outlines as a lexc stage aligned to the lexicon, then the
vowel rewrite as a stage over it, then the coda table as a rule over that
stage's output rather than counts.

## Phase 5: the lead drop for secondary stress, docs, and wrap-up

### Lead drop of a secondary-stressed prefix

Phase 4's nasal fix cost 160 words their only stroke (include, increase,
enforce, confirmed): CMU gives in-, en- and con- secondary stress, and the lead
rule dropped only an unstressed syllable. Now it drops one with secondary stress
too (`Weak` in lead.xfst, a one-line change).

| two-syllable.json | entries | words | coverage | lose |
|---|---|---|---|---|
| phase 4 | 19058 | 11146 | 189.3 | 520 |
| now | 19436 | 11347 | 192.7 | 599 |

207 words gain a stroke and 36 lose theirs. Gains: include `^KHRAOUD`,
increase `^KRAES`, instead `^STAED`, indeed `^TKAOED`, however `^HEFR`,
although `^THROE`, install, invite, involve, understand `STKRAPBD`,
information `^TPHRAEUGS`, conversation `^SRAEUGS` and many -ation words. The
losses go to more frequent words: conclude to include, consist to insist,
divide to understood (`STKRAOD`), seventy to incentive, credible to
incredibly. enforce and confirmed still have no stroke. Kept.

`make regen` settled in one round. check.sh passes at the tip, once.

### Docs

README.md (the vowel theory and two-word sections, the regen workflow) and
VOWELS-DESIGN.md §8 and §13 (a list of the pipeline as it is now, the S-/ST-
folds, the two-word resolution) describe the current pipeline. The cheat
sheets and Anki decks are stale (see the summary).

## Dev experience

Reported by the phases, merged. Fixed now:

- The generators' fixed-point loop was by hand (phases 1, 2, 3b). Fixed:
  `make regen`.
- A pipeline compile took 4 to 10 minutes per variant, and ablations were
  measured by hand-patching (phases 2, 3, 3b). Fixed: the stage cache and
  `--ablate STAGE`. A change to an early stage still rebuilds everything after
  it, but a prefix is now composed by hfst-compose, not hfst-xfst, and its
  paths listed from hfst-fst2txt, not hfst-fst2strings: a lead change takes 34
  s, not 5.5 minutes. The dictionaries compile at once, so a full compile is
  2.5 minutes, not 5.5, and is as long as phrasing-tag alone. regen.sh runs the
  generators' checks at once: a regen with nothing stale is 11 s.

Open:

- check.sh stops at the first failure (phases 1, 2). Fix: collect failures and
  exit non-zero at the end.
- Stage golden files are 17 MB and slow to diff (phases 2, 3). Fix: store only
  the lines a stage changes.
- Generators can read a stale stack or pipeline silently (phases 1, 3b, 4).
  `make regen` compiles first, but a standalone `--check` does not. Fix: Stack
  checks stamps; `--check` calls the cached compile.
- Any compiler change changes every artifact's stamp, so the whole stack
  rebuilds. Fix: stamp with only the code that builds that artifact.
- find-typey-composed.py takes about 12 minutes under Plover's python. Fix:
  cache verdicts per entry.
- DUPT (`TKUPT`, ADD_OUTLINE) leaves no log of its target dictionary. Fix: the
  add-outline plugin logs it on save.
- The caret stage's output loses plain against `^` fallback; Python infers it.
  Fix: keep a mark in the last stage's output.
- xfst reads `"1:"` as one multichar symbol, and lexc read TH/SH in key text as
  phonemes; both failed silently. Fix: export head symbols from one stage
  file, and escape all key text with `Keys` from gen/lexicon.py.
- `sed -i ''` fails because nix puts GNU sed on PATH. Fix: a note in the
  README, or use perl.
- `--check` gives one exit code for every failure, and regen.sh keeps only a
  report's tail (the full one is in build/regen-GEN.log). Fix: distinct exit
  codes, and a `--report` summary.

