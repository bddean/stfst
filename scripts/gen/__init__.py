"""The generators that share the vowel theory, as one package.

`vowels` rewrites Typey Type's vowels. `lexicon` and `theory` write the sources
of the pipeline stages recipe.toml names, and `two_syllable`, `two_word` and
`affixes` resolve what their pipelines relate, by `resolve`. `stack` answers
what the rest of the stack already writes, and `corpora` how often text occurs. Each generator's command is `scripts/gen-*.py`.
"""
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
PLOVER = os.path.expanduser("~/.config/plover")
SYSTEM = os.path.join(ROOT, "systems", "stenotype-extended.toml")
RECIPE = os.path.join(ROOT, "recipe.toml")

# `plover_tape` beside this package, and the plugin's reader and record language,
# which need no Plover.
for path in (os.path.join(ROOT, "scripts"), os.path.join(ROOT, "plover-plugin")):
    if path not in sys.path:
        sys.path.insert(0, path)
