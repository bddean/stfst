"""Counts over the phrasing corpora, ~/.config/plover/phrasing/corpora: the
Leipzig news, web and wiki sentences, 17M tokens.

A pass over them is most of what a generator's run costs, so each count is held
in `build/cache` against what it asked and the corpora's sizes and times.
"""
import collections
import glob
import hashlib
import json
import os
import re

from . import PLOVER, ROOT

PATHS = sorted(glob.glob(os.path.join(PLOVER, "phrasing", "corpora", "*", "*-sentences.txt")))
CACHE = os.path.join(ROOT, "build", "cache")
WORD = re.compile(r"[a-z']+")


def sentences():
    """Each sentence's words, lowercased."""
    for path in PATHS:
        with open(path, encoding="utf-8", errors="ignore") as f:
            for line in f:
                yield WORD.findall(line.split("\t", 1)[-1].lower())


def _cached(name, query, count):
    """{ngram: n} from `count()`, or from build/cache/NAME.json when it was
    counted for the same `query` over the same corpora."""
    path = os.path.join(CACHE, f"{name}.json")
    key = hashlib.sha256(json.dumps([query, [(p, os.stat(p).st_size, os.stat(p).st_mtime_ns)
                                             for p in PATHS]]).encode()).hexdigest()
    try:
        with open(path) as f:
            cached = json.load(f)
        if cached["key"] == key:
            return collections.Counter({tuple(gram.split(" ")): n for gram, n in cached["counts"].items()})
    except (OSError, ValueError, KeyError):
        pass
    counts = count()
    os.makedirs(CACHE, exist_ok=True)
    with open(path, "w") as f:
        json.dump({"key": key, "counts": {" ".join(gram): n for gram, n in counts.items()}}, f)
    return counts


def ngrams(name, wanted):
    """{ngram: count} for the n-grams in `wanted`, cached as NAME."""
    def count():
        counts = collections.Counter()
        # Only an n-gram's first word can start one, so most positions cost a lookup.
        lengths = collections.defaultdict(set)
        for gram in wanted:
            lengths[gram[0]].add(len(gram))
        for toks in sentences():
            for i, tok in enumerate(toks):
                for n in lengths.get(tok, ()):
                    gram = tuple(toks[i:i + n])
                    if len(gram) == n and gram in wanted:
                        counts[gram] += 1
        return counts
    return _cached(name, sorted(wanted), count)


def bigrams(name, vocabulary):
    """{(a, b): count} for each two words in a row that are both in `vocabulary`,
    cached as NAME."""
    def count():
        counts = collections.Counter()
        for toks in sentences():
            for gram in zip(toks, toks[1:]):
                if gram[0] in vocabulary and gram[1] in vocabulary:
                    counts[gram] += 1
        return counts
    return _cached(name, sorted(vocabulary), count)
