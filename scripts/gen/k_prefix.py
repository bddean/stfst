"""Write `dictionaries/k-prefix.json`: K- stands for the syllable that starts a
co- word, as -L stands for the syllable that ends an -l word.

A Typey Type outline that starts with con, com, col or cor has the same outline
with `K` as its first stroke: concept `KOPB/SEPT` is `K/SEPT`, and compute
`KOPL/PAOUT` is `K/PAOUT`. An outline two words want goes to the more frequent.

This has to be outlines rather than a join on `K` writing text, because the
stroke after `K` is a sound: `PAOUT` alone is "output".

Usage (needs wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-k-prefix.py            # write the JSON
    $PY scripts/gen-k-prefix.py --check    # fail if the committed JSON is stale
"""
import argparse
import json
import os
import re
import sys

from . import DICTS
from . import vowels as g
from .two_syllable import write_json

OUTPUT = os.path.join(DICTS, "k-prefix.json")
SYLLABLES = {"KOPB", "KAUPB", "KOPL", "KAUPL", "KOL", "KAUL", "KOR", "KAUR"}


def build(typey):
    """{outline: word}."""
    out = {}
    for outline, word in typey.items():
        first, _, rest = outline.partition("/")
        if rest and first in SYLLABLES and re.fullmatch(r"co[a-z]+", word):
            k = "K/" + rest
            if k not in out or (-g.zipf(word), word) < (-g.zipf(out[k]), out[k]):
                out[k] = word
    return dict(sorted(out.items()))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the committed JSON is stale")
    args = ap.parse_args()
    out = build(g.written())
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
    else:
        write_json(out, OUTPUT)
        print(f"{len(out)} outlines, {len(set(out.values()))} words")
