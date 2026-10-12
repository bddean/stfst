# Systematizing the theory

The morning report for the overnight run. Each phase appends a section.

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

| variant | words | coverage | lose | user.json briefs reproduced |
|---|---|---|---|---|
| chosen | 6097 | 128.3 | 392 | 42 |
| -ly XORs y (old rule) | 6072 | 128.0 | 407 | 38 |
| digraph XORs its chord, not its first letter | 6081 | 127.9 | 396 | 38 |
| -tion by its sounds, not -GS | 5947 | 125.4 | 411 | 34 |
| syncope only before l and r | 5790 | 125.0 | 447 | 37 |
| lead drop as second choice only | 6063 | 128.0 | 402 | 37 |
| prefix cluster kept apart from the onset | 5820 | 124.2 | 389 | 34 |

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
