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
