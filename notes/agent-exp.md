In stfst

- The compile failed outside the dev shell. stfst compile said "running hfst-xfst: No such file or directory". It should say that HFST is missing and that you need direnv exec ..
- A recipe change rebuilds everything. The recipe counts as a source, so adding one dictionary cost a 2.5-minute full compile. Invalidating only the dictionaries whose entries changed would make that seconds.
- check.sh is one 5-minute block. The generator --checks, the goldens and the fixtures should be runnable for one dictionary at a time, for example check.sh affixes.
- Adding a dictionary means editing six places: recipe.toml, plover.cfg (the same order, copied by hand), a --check line in check.sh, two READMEs, and two fixtures. The fixtures print every dictionary's stack index, so inserting one rewrites them. Three fixes:
  - Let the recipe say which script writes each dictionary, so check.sh finds the checks itself.
  - Have a check fail when plover.cfg's order and the recipe disagree.
  - Print names in fixtures, not indices.
- A fixture run wrote into the repo. Running the editable fixture by hand created tests/editable/stfst-user.json, which went into a commit before I caught it. The run should copy that directory to a temporary one.
- A lookup gives the translation in two formats. A JSON layer gives Plover syntax ({^ly}), and a compiled one gives the record language (ly[ORTHO_L]). The generator needs a parser for both. stfst lookup should be able to print Plover syntax, or the Python Stack should return one form.
- Nothing lists which strokes are used. To find free affix strokes, I looked up 2.2M candidate strokes across 18 artifacts in 1.5 minutes. A stfst dump of every outline in an artifact would make that instant.
- The generators load each other by file path. gen-affixes loads gen-two-syllable, which loads gen-typey-vowels, with importlib because the file names have hyphens. Also, each run repeats the ~10 s vowel resolution. They should be modules in a Python package that caches that result.
- The Rust tests have no morphology. The keyboard test failed with NoMorph on any -s or -ed. They should load build/morph when it exists.

In plover

- The regeneration order is a paragraph in CLAUDE.md. It says: after this script runs, run these five, then render two PDFs. That is a dependency graph written out by hand. It should be a Makefile or justfile.
- plover.cfg mixes machine settings with the dictionary list. Your uncommitted hold settings sit in the same file as the dictionary wiring, so every commit that adds a dictionary needs a hand-built partial patch. Generating the Extended dictionary list from the recipe would remove that.
- Rendering the PDFs needs undocumented tools. It takes Chrome headless plus a rasterizer for previews. The pdftoppm installed is xpdf's, not poppler's, so the flags differ. One render script would fix it.
