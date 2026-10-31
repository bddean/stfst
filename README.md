# stfst

Steno dictionaries compiled to finite-state transducers, an Android steno
keyboard that runs them, and the author's personal dictionaries.

**This is entirely vibecoded.** Claude wrote all of the code and this
README, and none of it was written by hand. It is a personal project, built for one person's setup.

## Dictionaries as transducers

A Plover dictionary is a map from outline to translation. Here a dictionary is
a transducer from keys to a translation. It is written as an xfst grammar, or
imported from Plover JSON, and compiled with HFST.

The upper tape has one symbol per key, so a rule can name a key. That lets a
dictionary state rules instead of listing every entry. The phrasing dictionary
below is 20.7M chords, which is 881 KB as a grammar.

The compiled artifacts are read by a small Rust runtime with no HFST
dependency (`crates/stfst-ol`, `crates/stfst-dict`). `plover-plugin/` loads the
same artifacts in Plover.

## The Android keyboard

`android/` is a steno input method. Each thumb has a set of nested rings that
follow its half of the steno order. Touch down on a key, slide through the
other keys you want, and lift to write the stroke. Entering a key that steno
order still allows takes it. Nothing depends on timing or slide distance.

Above each bank it shows what the stroke would write if you lifted now. It can
also read a USB steno machine. It ships with the dictionaries below and the
system they are written for, and the APK is attached to each GitHub release. See
`android/README.md`.

## The dictionaries

These are for Plover's Stenotype Extended system, which adds the keys `^` and
`+`. This repo adds two more: `@`, and `🦐`, which marks a stroke as a Shrimple
syllable and is sent by holding a chord. This is not a standard theory. Typey
Type is the base, and the rest sits on top of it.

The board's left edge is two columns, `@` above `S`:

```
 ^  @  T  P  H
 +  S  K  W  R
```

`@` opens the modifiers' namespace, except in a chord with `^` or `+`, where it
is `S`: `^@TP` is `^STP`.



| Dictionary | What it does |
|---|---|
| phrasing | A clause grammar on `#`: subject, modality, aspect, negation and verb in one stroke. |
| two-syllable, two-word | A two-syllable word, or two short words, in one stroke: the stressed syllable, marked with the other vowel's letter (happy is `^+HAP`, at all is `+TAUL`). See [the mark](#the-two-syllable-mark). |
| affixes | A stem's stroke, then one stroke for its prefix and suffix. |
| folds | A stroke nothing matched, read as a word plus "the", "of", "and" or "so" (`KAOEPT` is "keep the"), or with `^+` as the word and -y (`^+TAOEUP` is "typey"). |
| frames, ahead | Write a frame now and fill it later: `STPR*T` writes "from to", and "here" makes it "from here to". |
| retro-words | Recase, join and split the last words written. |

`emily-symbols`, `emily-modifiers` and Shrimple are ports of other people's
dictionaries.

## The two-syllable mark

A word of two syllables is one stroke: its stressed syllable as stfst writes
it, onset, chord and coda, with the mark of the other vowel's first letter.
The mark's keys are added to the stroke's, so every key of the stressed chord
stays; only `+O` and `+U` can add a vowel key to it.

| The other vowel | Mark | Example |
|---|---|---|
| a | `+` | about `+PWOUT` |
| e, r-coloured, or dropped by a rewrite | `^` | money `^PHOPB`, report `^PRORT`, attempt `^TEPLT` |
| i or y | `^+` | happy `^+HAP`, city `^+SEUT` |
| o | `+O` | today `+TKAOEU`, also `+AOLS` |
| u | `+U` | focus `+TPOEUBGS` |
| none: one syllable, its plain stroke taken | `^` | matter `^PHART`, since mart is `PHART` |

The i or y mark is also a fold: where nothing holds a stroke, `^+` on it is
the word the rest writes and -y, joined as `{^y}` joins, so `^+TAOEUP` is
"typey" and `^+TPUPB` "funny" with no entry for either. It reads the last
stroke of an outline too (`KHOBG/^+HRAT` is "chocolaty"). The other marks are
not: `+` as a- or `^` as -er would write about 1% of their words.

A word a rewrite gives two outlines has both (attempt is `+TEPLT`, and
`^TEPLT` with its a dropped). Two short words written together take the same
mark. A word that loses its stroke to a more frequent one is the two strokes
the theory writes for it (animal is `APB/PHAL`), and a word the theory cannot
place either keeps its Typey Type outlines. The rule is
`dictionaries/caret.xfst`; `docs/caret-replacement.md` is the study that chose
it.

## More

`docs/internals.md` is the full tour: the build, the dictionary stack, the
Plover plugin, and how to add a dictionary.
