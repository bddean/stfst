In stfst

Done:

- A missing HFST tool says HFST is missing and to run under `direnv exec .` or `nix develop`.
- An artifact is stale when its source files, the system, its own recipe entry or the compiler's source changed. Not the rest of the recipe and not the binary, so adding a dictionary compiles that one, and a change to the CLI compiles nothing. The artifact's `stamp` file records what it was built from. A change to stfst-core, stfst-ol or stfst-compile, tests included, still rebuilds everything, about three minutes.
- `scripts/check.sh NAME...` compiles those dictionaries and runs their generator's `--check`, golden files and fixture. The whole suite takes about 80 seconds when nothing needs compiling.
- The recipe names each generated dictionary's `generator` and any extra `golden`, and check.sh finds them there.
- `scripts/check-plover-cfg.py`, run by check.sh, fails when plover.cfg's Stenotype Extended list and the recipe disagree. It found phrasing out of place: the recipe had it at 10, and Plover loads it second. It is 95 now, phrasing-tag 96.
- The stack a translate run loaded goes to stderr, by name. A fixture holds only what the strokes wrote, so a new dictionary changes no fixture unless it changes a translation.
- `scripts/fixture.sh DIR [--update]` runs one fixture. An editable directory is copied to a temporary one first. Each fixture directory names its stack in `artifacts` and what it needs in `requires`.
- `gen.stack` in Python reads an artifact with the plugin's reader and returns Plover syntax, one form. `stfst lookup` still prints the record language: the record-to-Plover direction exists only in `plover_stfst.record`, on purpose, and a Rust copy would be a second one to keep in step.
- `stfst dump ARTIFACT [--records] [--strokes N]` lists every outline in steno order. typey-type takes 0.4 s; phrasing, 29.5M outlines, takes a minute.
- The vowel generators are the package `scripts/gen`, and `scripts/gen-*.py` run them. The slow part was the corpus pass in gen-affixes, not the vowel resolution (0.35 s). That pass is faster, and its counts are cached in `build/cache`. gen-affixes --check went from 13 s to 1.5 s.
- The Rust keyboard test loads `build/morph` when it exists, and a new test strokes a suffix.

Found, not fixed:

- The retro-words artifact answers 94 outlines. `retro-words.py` lists 114, and gen-two-syllable reserves all 114. The 20 it does not answer are the `-S` and `-LGT` menu rows (`^AES`, `^AEBS`, ...).

In plover

Done:

- `make` in the plover dir runs the regeneration in order, and each step is a target (`make composed`, `make vowels`, `make pdfs`, `make check`). The order is a cycle, not a file graph, so it is a sequence, and the Makefile says why.
- `scripts/render-pdf.sh [--preview]` renders a sheet with headless Chrome. `--preview` writes page 1 as a PNG with macOS's `sips`, so pdftoppm is not needed.

Not done:

- Generating plover.cfg's Extended list from the recipe. plover.cfg has uncommitted hold settings, and the list holds entries the recipe does not model: `stfst-user.json` below `user.json`, `semantic-retro.json`, `user.json` loaded as JSON and not as its artifact, and the disabled entries. The order check above catches drift in the meantime.
