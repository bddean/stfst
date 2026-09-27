"""Write `dictionaries/lexicon.lexc`: each word Typey Type writes, its CMU
pronunciations aligned to its spelling, and its frequency.

A pronunciation is aligned by syllable. The word alternates consonant segments
and vowel segments, starting and ending with a consonant one, and each segment
pairs phonemes with letters: report is `R=r IY0=e P=p AO1=o R.T=rt`. A segment
is its phonemes joined by `.`, `=`, and its letters, and either side may be
empty: at is `= AE1=a T=t`. Letters that spell nothing follow a segment's
own after a `_`: a final e or ue (`T=t_e`), and the e of aye, owe and awe
(`EY1=ay_e`). A run of vowel letters that is two vowels is split after its
first letter: create is `K.R=cr IY0=e = EY1=a T=t_e`.

The phonemes come first so that a rule reads a segment's sound before its
spelling, which is the order a steno theory asks in.

Each entry starts with the pronunciation's place in CMU's order and a space, so
a rule can prefer the first. The pronunciations are CMU's as steno hears them
(`vowels.prons`). A word whose vowel letters do not line up with its
pronunciation is left out. The weight is the word's zipf negated, so the more
frequent word costs less.

Usage (needs cmudict and wordfreq, which the phrasing venv has):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-lexicon.py            # write the lexicon, print the report
    $PY scripts/gen-lexicon.py --check    # fail if it is stale
"""
import argparse
import json
import os
import re
import sys
from dataclasses import dataclass

from . import ROOT
from . import vowels as g

LEXICON = os.path.join(ROOT, "dictionaries", "lexicon.lexc")


@dataclass(frozen=True)
class Segment:
    phones: tuple
    letters: str
    silent: str = ""    # letters after these that spell nothing: a final e, or aye's e

    def __str__(self):
        return f"{'.'.join(self.phones)}={self.letters}" + (f"_{self.silent}" if self.silent else "")


@dataclass(frozen=True)
class Entry:
    word: str
    rank: int           # the pronunciation's place in CMU's order, from 1
    segments: tuple     # consonant, vowel, consonant, ..., consonant

    def __str__(self):
        return f"{self.rank} " + " ".join(map(str, self.segments))


def align(word, pron, rank=1):
    """The Entry for a word and one pronunciation, or None when its vowel
    letters and vowels do not line up.

    Two vowels with no consonant between them (react) may be one run of
    letters, split after its first letter; an r-coloured first vowel has its r
    between (around). A final e or ue after q or g is silent where the vowels
    line up without it (fire, create), and in -le and -re only where they do
    not line up with it (acre, but agile)."""
    vowels = [i for i, p in enumerate(pron) if p[-1].isdigit()]
    runs = g.vowel_runs(word)
    if not vowels:
        return None
    tries = [(runs, "")]
    if len(runs) > 1:
        i, j, v = runs[-1]
        if j == len(word) and i > runs[-2][1] and (v == "e" or (v == "ue" and word[i - 1] in "qg")):
            tries.insert(0 if word[-2:] not in ("le", "re") else 1, (runs[:-1], v))
    for rs, tail in tries:
        if (spans := vowel_spans(word, rs, pron, vowels)) is not None:
            break
    else:
        return None
    segs = []
    at = 0
    for n, (i, j, v) in enumerate(spans):
        segs.append(Segment(tuple(pron[vowels[n - 1] + 1 if n else 0:vowels[n]]), word[at:i]))
        segs.append(Segment((pron[vowels[n]],), v, word[i + len(v):j]))
        at = j
    end = len(word) - len(tail)
    segs.append(Segment(tuple(pron[vowels[-1] + 1:]), word[at:end], tail))
    return Entry(word, rank, tuple(segs))


def vowel_spans(word, runs, pron, vowels):
    """Each vowel's letters as (start, end, spelling), splitting a run where two
    vowels meet while the runs are too few, or None when they do not line up.
    A run's spelling can be less than its letters: aye is ay."""
    spans, k = [], 0
    for n, (i, j, v) in enumerate(runs):
        short = (len(vowels) - k) - (len(runs) - n)
        meet = k + 1 < len(vowels) and vowels[k + 1] == vowels[k] + 1 and pron[vowels[k]][:-1] != "ER"
        if short > 0 and meet and j - i > 1:
            spans += [(i, i + 1, word[i]), (i + 1, j, word[i + 1:j])]
            k += 2
        else:
            spans.append((i, j, v))
            k += 1
    return spans if k == len(vowels) and len(spans) == k else None


def entries(words):
    """{word: (zipf, [Entry])} for the words that align at least once."""
    out = {}
    for w in sorted(words):
        es = {}
        for r, p in enumerate(g.prons(w), 1):
            if (e := align(w, p, r)):
                es.setdefault(e.segments, e)
        es = list(es.values())
        if es:
            out[w] = (g.zipf(w), es)
    return out


def vocabulary():
    """The words Typey Type writes that CMU pronounces."""
    with open(g.UPSTREAM) as f:
        return {w for w in json.load(f).values() if re.fullmatch("[a-z]+", w) and w in g.CMU}


# --- The lexc file ---------------------------------------------------------------

PHONES = sorted({p for ps in g.CMU.values() for pron in ps for p in pron if len(p) > 1})


def lexc_escape(text):
    return re.sub(r"([ !%;:<>#\"0])", r"%\1", text)


def lexc(header, rows):
    """A lexc lexicon of `rows`, (word, entry text, weight or None), under a
    comment `header`."""
    lines = [f"! {line}" for line in header.split("\n")]
    lines += ["", "Multichar_Symbols", " ".join(lexc_escape(p) for p in PHONES), "", "LEXICON Root"]
    for word, text, weight in rows:
        tail = f' "weight: {weight:g}"' if weight is not None else ""
        lines.append(f"{lexc_escape(word)}:{lexc_escape(text)} #{tail} ;")
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the lexicon is stale")
    args = ap.parse_args()

    words = vocabulary()
    lex = entries(words)
    text = lexc("Written by scripts/gen-lexicon.py: see scripts/gen/lexicon.py for the format.\nDo not edit.",
                [(w, str(e), -z) for w, (z, es) in lex.items() for e in es])
    if args.check:
        if not os.path.exists(LEXICON) or open(LEXICON).read() != text:
            sys.exit(f"{LEXICON} is stale: run {sys.argv[0]}")
        return
    with open(LEXICON, "w") as f:
        f.write(text)
    n = sum(len(es) for _, es in lex.values())
    left = sorted(words - lex.keys(), key=lambda w: -g.zipf(w))
    print(f"{len(lex)} of {len(words)} words, {n} pronunciations; {len(left)} do not align, "
          f"most frequent first:")
    print("  " + " ".join(left[:200]))
