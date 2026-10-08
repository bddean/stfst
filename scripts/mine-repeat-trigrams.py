#!/usr/bin/env python3
"""Mine W1-C-W2 trigrams from the Leipzig corpora to drive repeat-word stems
and idioms from real data. Run with any python3.

Signals per candidate connective C:
  * reduplication mass -- how often "X C X" occurs. This is why a connective
    earns a stem: the generic repeat pays off in bulk (day to day, one on one).
  * idiom concentration -- a fixed "X C Y" is one where a single Y dominates the
    completions of "X C ...". P(Y|X,C) high + robust across corpora = idiom.

Cross-corpus robustness (present in >=2 of news/web/wiki) rejects the topical
spikes a single 2020 news dump produces (covid, crypto, proper-noun pairs).

Writes repeat-trigrams.json: stem ranking + mined idioms per conn.
"""

import glob
import json
import os
import re
from collections import Counter, defaultdict

DICTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dictionaries")
OUT = os.path.join(DICTS, "repeat-trigrams.json")

CANDIDATES = {
    "and", "or", "to", "the", "of", "by", "for", "after", "than", "so", "in",
    "on", "over", "from", "with", "up", "off", "about", "into", "before",
    "against", "around", "through", "under", "per", "upon", "beyond", "down",
}

TOKEN = re.compile(r"[a-z]+(?:'[a-z]+)?")
FRAG = {"s", "th", "t", "d", "ve", "re", "ll", "m", "o", "y", "st", "n", "i",
        "u", "e", "co", "de", "en"}

# Idiom thresholds.
MIN_TOTAL = 25     # min "W1 C ..." occurrences to judge a completion
MIN_FREQ = 20      # min "W1 C W2" occurrences
MIN_SHARE = 0.35   # P(W2 | W1, C): how dominant the completion is
MIN_CORPORA = 2    # must appear in >=2 corpora to survive topical spikes


def corpus_files():
    for path in glob.glob(os.path.join(os.path.expanduser("~/.config/plover"), "phrasing", "corpora", "*",
                                        "*-sentences.txt")):
        yield os.path.basename(os.path.dirname(path)), path


def main():
    # C -> corpus -> Counter[(w1,w2)]
    pairs = defaultdict(lambda: defaultdict(Counter))
    redup = defaultdict(Counter)
    n = 0
    for corpus, path in corpus_files():
        with open(path, encoding="utf-8", errors="ignore") as f:
            for line in f:
                n += 1
                tab = line.find("\t")
                toks = TOKEN.findall((line[tab + 1:] if tab >= 0 else line).lower())
                for i in range(1, len(toks) - 1):
                    c = toks[i]
                    if c not in CANDIDATES:
                        continue
                    w1, w2 = toks[i - 1], toks[i + 1]
                    pairs[c][corpus][(w1, w2)] += 1
                    if w1 == w2:
                        redup[c][w1] += 1

    def good(w):
        return len(w) >= 3 and w not in FRAG

    result = {}
    ranking = []
    for c in CANDIDATES:
        merged = Counter()
        ncorp = defaultdict(set)
        for corpus, cnt in pairs[c].items():
            for pair, v in cnt.items():
                merged[pair] += v
                ncorp[pair].add(corpus)
        w1_total = Counter()
        for (w1, w2), v in merged.items():
            w1_total[w1] += v

        idioms = []
        for (w1, w2), v in merged.items():
            if w1 == w2 or not (good(w1) and good(w2)):
                continue
            if v < MIN_FREQ or w1_total[w1] < MIN_TOTAL:
                continue
            if len(ncorp[(w1, w2)]) < MIN_CORPORA:
                continue
            share = v / w1_total[w1]
            if share < MIN_SHARE:
                continue
            idioms.append((w1, w2, v, round(share, 2), len(ncorp[(w1, w2)])))
        # dedupe by w1 (keep most frequent), sort by frequency
        by_w1 = {}
        for it in sorted(idioms, key=lambda t: -t[2]):
            by_w1.setdefault(it[0], it)
        idioms = sorted(by_w1.values(), key=lambda t: -t[2])

        result[c] = {
            "redup_mass": sum(redup[c].values()),
            "top_redup": redup[c].most_common(10),
            "idioms": idioms,
        }
        ranking.append((c, result[c]["redup_mass"], len(idioms)))

    ranking.sort(key=lambda r: -r[1])
    with open(OUT, "w") as f:
        json.dump({"sentences": n, "conn": result}, f)

    print(f"scanned {n} sentences\n{'conn':8} {'redupX·C·X':>11} {'#idioms':>8}"
          "   top reduplications")
    for c, rm, ni in ranking:
        top = " ".join(f"{w}×{v}" for w, v in result[c]["top_redup"][:5]
                       if len(w) >= 3 and w not in FRAG)
        print(f"{c:8} {rm:11d} {ni:8d}   {top}")


if __name__ == "__main__":
    main()
