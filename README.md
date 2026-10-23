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
Type is the base, and the rest sits on top of it:

| Dictionary | What it does |
|---|---|
| phrasing | A clause grammar on `#`: subject, modality, aspect, negation and verb in one stroke. |
| two-syllable, two-word | A two-syllable word, or two short words, in one stroke marked `^` (report is `^PROERT`, at all is `^TUL`). |
| affixes | A stem's stroke, then one stroke for its prefix and suffix. |
| folds | A stroke nothing matched, read as a word plus "the", "of", "and" or "so" (`KAOEPT` is "keep the"). |
| frames, ahead | Write a frame now and fill it later: `STPR*T` writes "from to", and "here" makes it "from here to". |
| retro-words | Recase, join and split the last words written. |

`emily-symbols`, `emily-modifiers` and Shrimple are ports of other people's
dictionaries.

## More

`docs/internals.md` is the full tour: the build, the dictionary stack, the
Plover plugin, and how to add a dictionary.
