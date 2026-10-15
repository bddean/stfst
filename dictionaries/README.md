# dictionaries

Every dictionary source, whatever host reads it. The `.xfst`, `.lexc` and `.dict`
files are the grammars and flat sources `../README.md` describes. The rest are
Plover's own formats, and this file says what each one is.

In `Stenotype Extended`, Plover loads only the artifacts `../recipe.toml` builds
into `../build/dicts/`, through the `plover-stfst` plugin. `user.json`,
`stfst-user.json` and `semantic-retro.json` are editable there: the artifact
names the file and the plugin reads and saves it, so an edit needs no build.

`English Stenotype` loads the plain JSON and Python files from here by absolute
path, because an artifact names the extended system's keys and the plugin refuses
it there. So `emily-symbols.py`, `emily-modifiers.py` and
`Shrimple-WSI-English.py` are still read by Plover, and are also the upstream
tables `../scripts/gen-*-tables.py` transcribe. `retro-words.py` and
`repeat-word.py` are read by no system: their tables are transcribed the same
way, and their artifacts replace them.

A generated JSON file says so below. Change its generator or its table, not the
file.

| File | Role |
|------|------|
| `wm.py` | Python dict: window-manager control (yabai) as steno strokes. Navigation lives under the extended `^` key (`^U`/`^E` + right-hand operand; spaces in `-RBGS` binary, hjkl on `-FPLT`), Stenotype Extended only; actions/launchers (`PW*S` …) work in both systems. **Not enabled.** See its header. |
| `retro-words.py` | Python dict: retro word operators: recase, join/split, and wrap the last *words* (not strokes). One file that both registers a `{:retro_word:…}` meta at import and binds strokes to it, so it is `*`-undoable and reverse-lookable. Stem `^A` (Extended only): case dial `F R P B`, verbs `-E` join / `-U` split / `-EU` wrap, and an `L G T S` bit-menu picking the separator or bracket pair. Composes: `^AEP` = camelCase. See its header. |
| `repeat-word.py` | Python dict: repeat the previous *word* after a folded connective, which is the `X-conn-X` reduplication steno otherwise spells twice. A connective's ordinary brief folded onto `^` (`and`=`^SKP`, `to`=`^O`/`^TO`, `by`=`^PWEU`, `in`=`^TPH`, `on`=`^OB`, `over`=`^OEFR`, plus `or`/`of`/`the`/`for`) registers a `{:repeat_word:<conn>}` meta and echoes the last word: `PWABG/^O` → back to back. Repeats the word, not the stroke. Stenotype Extended only (needs `^`). See its header. |
| `repeat-idioms.json` | Higher-priority overrides above `repeat-word.py`: the non-literal trigrams whose third word isn't the first (`TPH/^SKP` → in and out, `TH/^OR` → this or that). A curated seed of fixed expressions merged with corpus-mined high-freq trigrams (see `repeat-trigrams.json`). Literal reduplications are left to the meta, per "prefer the repeat". Built by `../scripts/build-repeat-idioms.py`; must sit above `typey-type-full.json`. |
| `repeat-trigrams.json` | Mining intermediate for `repeat-idioms.json`: per-connective reduplication mass (the stem signal) and concentration-ranked idioms, from the Leipzig corpora. Regenerate with `../scripts/mine-repeat-trigrams.py` (needs `~/.config/plover/phrasing/corpora/`). |
| `../scripts/test-repeat-word.py` | End-to-end tests for the repeat-word meta + idioms, driving the real Plover translator/formatter. Run with Plover's bundled python. |
| `two-syllable.json` | Generated: a word of two syllables as one stroke marked with `^` (report `^PROERT`, happy `^HAEUP`), or a prefix's cluster in place of `^` (display `STKPHRAEU`). Written by `../scripts/gen-two-syllable.py`, whose docstring states the rule; see `~/.config/plover/VOWELS-DESIGN.md` §8. Sits just above `typey-type-full.json`, and `user.json` overrides it. Stenotype Extended only (needs `^`) |
| `two-word.json` | Generated: two one-syllable words often written together, as one `^` stroke by the rule of `two-syllable.json` (at all `^TUL`, make it `^PHABGT`). Written by `../scripts/gen-two-word.py`, whose docstring states the rule; see `~/.config/plover/VOWELS-DESIGN.md` §13. Sits just below `two-syllable.json`. Stenotype Extended only (needs `^`) |
| `stfst-user.json` | What the Android keyboard defines, and the second editable dictionary. `make install` pulls it off the phone, unions it with this copy and pushes the result back, so an outline defined on the phone works here too. Sits *below* `user.json` in the recipe, which keeps `user.json` the first writable dictionary and so the Add Translation target; on the phone it sits above, because an outline redefined there has to beat whatever it used to write. Stenotype Extended only, since its outlines are written in that system. |
| `user.json` | Personal briefs and overrides. Includes `^` briefs for words the rule of `two-syllable.json` does not write, such as `^SEBL` assemble and `^TPHOER` another (three syllables). |
| `phrases.json` | Multi-word phrase briefs (idioms, phrasal verbs, connectives), corpus-mined, stemmed, rule-systematized. See `~/.config/plover/PHRASES.md`. |
| `commands.json` | Editing/navigation chords and `{PLOVER:…}` meta-commands. `TKUPT` is `{PLOVER:ADD_OUTLINE}`, not Plover's own add-translation: see `~/.config/plover/plover-add-outline/`. |
| `emily-modifiers.py` | The `@`-key keysym namespace (retriggered [emily-modifiers](https://github.com/EPLHREU/emily-modifiers): reuses its spelling/symbol tables). Left hand = payload (letter / symbol / digit / F-key), right top row = modifiers (`F`=ctrl `P`=shift `L`=super `T`=alt `D`=fn), thumb `E`/`U` + `*`/`-Z` = mode. The literal-number 128 bit rides on `^`. `#` is phrasing's, so the compiled dictionary leaves out the Python's `#` bits (16 on a digit or F-key, 256 on a literal number). Combos matching a `~/.config/skhd/skhdrc` binding run that shell command instead of emitting the (skhd-invisible) keystroke; the compiled stack leaves that to `~/.config/plover/plover-skhd-relay/`, which does it for every combo rather than only this dictionary's. Unmodified printable keysyms are emitted as attached text so `*` can undo them. Stenotype Extended only (needs `@` on `#2`). See `~/.config/plover/MODIFIERS-DESIGN.md`. |
| `emily-symbols.py` | [emily-symbols](https://github.com/EPLHREU/emily-symbols), mirrored to the left hand for consistency with emily-modifiers: symbol chord on `STKPWHR` (emily-modifiers' own symbol table), identifier `#U` instead of the `SKWH` starter; variants on `A/O`, attach `-R/-B`, capitalise `-G`, repeat `-TS`. Adds rows upstream lacks, on free `S`+ chords: `STR` = Enter/Ins/Menu/Print (also in emily-modifiers, so shift-enter etc. work) and the bs block-sigil glyphs on `SPW`/`STK`/`SR`/`SKPR`. Sits at the *top* of the stack; the one accepted shadow is typey's `40U` → `{:}40`. The compiled dictionary moved the identifier to `-TZ` and the repeat to `-F`/`-P`, which frees `#` and `-U`; this file keeps `#U` and `-TS` for `English Stenotype`. The compiled one also writes numbers: `U` with `-TZ` reads the left bank as `emily-modifiers`' binary number (`SRUTZ` → 9) and `O` appends a zero (`SROUTZ` → 90), as `~/.config/plover/MODIFIERS-DESIGN.md` says. See the file header. |
| `Shrimple-WSI-English.py` | Whole-keyboard fingerspelling dict for foreign words/names. Forked from the downloaded original: standalone `^` is the sole entry stroke (`^/SH` → "sh"; a folded `^SH` is not grabbed), and empty lookups return `{#}` so they don't emit raw steno. The compiled stack adds a second way in, which this file has no key for: `🦐` on a stroke says that stroke is a syllable, so `🦐TAP/🦐KAT` needs no entry stroke and no exit. |
| `semantic-suffixes.json` | Remaps `-G -D -S -Z` to the `{:infl:…}` meta of `~/.config/plover/plover-semantic-suffixes/`: morphological inflection where it makes sense, and where it doesn't, determiner-zone insertion. `-D`/`-G` inflect the clause's bare verb even past a function word (`expect to` + `-D` → "expected to", the phrase-brief case) and append "the"/"a" where nothing inflects; `-S`/`-Z` insert "of" on quantifiers/partitives and a demonstrative ("this"/"that") after a preposition (else stock plural/possessive), composing to "some of the" / "from these". A key rewrites only the words its own stroke wrote (a fold reaches its base translation, a bare stroke the last word), and it only appends. `English Stenotype` only, where Plover's `SUFFIX_KEYS` fold it; must sit above `typey-type-full.json`. In Stenotype Extended `folds.xfst` holds the same four meanings and does the folding, with `-T` "the" and `SKP-` "and" (`KAOEPT` keep the, `SKPAD` and add). |
| `semantic-retro.json` | Binds lone `+` strokes (`+D +G +S +* #+ @+ ^+ …`) to general `{:retro:…}` clause ops over a tagger, for text the phrasing engine did not write. Stenotype Extended only (needs the `+ ^ # @` keys). Its `#+` holds `#`, which is otherwise phrasing's; phrasing has no entry for a bare `#+`, so the two do not clash. |
| `typey-type-full.json` | Generated: [Typey Type](https://didoesdigital.com/typey-type/) with its vowels rewritten by the spelling theory of `~/.config/plover/VOWELS-DESIGN.md`. Written by `../scripts/gen-typey-vowels.py` from `typey-type-upstream.json` and `typey-vowels.toml`; do not edit it. The same run moves the outlines a rule replaces into `typey-superseded.json`, and the entries the stack writes stroke by stroke into `typey-composed.json`. JSON rather than a transducer because `English Stenotype` reads it too. The compiled import leaves out its 437 `#` entries (digits, times, numerals), since `#` is phrasing's. |
| `typey-type-upstream.json` | Typey Type as imported, locally pruned (see note below). The generator's input. |
| `typey-superseded.json` | Generated beside `typey-type-full.json`: the Typey Type outlines of a word a rule writes, and of its -s and -ed forms, as long as the rule's outline: one stroke for a word of `two-syllable.json` (`OER` other, `PHUPB` money), two for one of the affix grammar (`REZ/SRAEUGS` reservation). A shorter one is a brief and stays. Stenotype Extended does not load it, so only the rule's outlines are there to learn and to suggest. `English Stenotype` has neither rule and loads it after `typey-type-full.json`. |
| `typey-composed.json` | Generated beside `typey-type-full.json`: the Typey Type entries whose strokes the rest of the stack writes the same without them, in both systems, before and after the words a test tries, and with a suffix key folded on: `RAOE/HROEBGTS` relocates is {re^} then locates. Loaded by neither system; it is the record of what left, and `../scripts/find-typey-composed.py` (Plover's python) decides it. |
| `vowel-briefs.json` | Generated beside `typey-type-full.json` by the same script: the shorter upstream strokes of a word of zipf > 5 whose shortest outline the vowel theory lengthened, where no other entry writes them (`PWEU` by, `THE` they, `SED` said). Sits just below `typey-type-full.json`, so it shadows nothing. Enabled in both systems. |
| `main.json` | Stock Plover dict (currently disabled). |
| `bqn.json` | BQN glyph entry (not currently enabled). |
| `kak.py`, `suppress_untranslates.py` | Solo-dict set for Kakoune, swapped in by the `SKAEBG` command. |

## typey-type-upstream.json local prune

Removed 108 multi-stroke entries whose outline was entirely fingerspelling
chords mapping to plain (unglued) text, e.g. `A*/TK*/SR*` → `adv`,
`TK*/O*/S*/T*` → `dost`, `P*/PW*/SKWR*` → `peanut butter and jelly`. These
hijack a fingerspelled letter run: the glued letters get replaced by the
unglued word, so the *next* fingerspelled letter has no glue neighbour and
gains a stray space (`a`,`d`,`v`,`a` came out `adv a`). Regenerate the list
with `../scripts/prune-typey-fingerspell.py` if the dict is ever refreshed, then
rerun the vowel generator.

