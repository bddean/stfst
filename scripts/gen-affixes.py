#!/usr/bin/env python3
"""Write `~/.config/plover/affixes.json`: a word with a prefix and a suffix as two
strokes, the stem and then one stroke holding both affixes.

The affix stroke has no vowel keys. The left bank is the prefix and the right bank
the suffix, each with the keys it has alone or folded into a stroke: re is `R`
as in `RE`, dis is `STK` as in display `STKPHRAEU`, -tion is `-GS` as in action,
-able is `-BL`. So reservation is serve `SEFRB` and re…ation `R-GS`, and a word
with one affix is written as it was.

The stem is written as a word of its syllables: one syllable by the vowel
theory's chord and the consonants by sound, two by two-syllable.json's rule.

-ed and the plural fold onto the affix stroke and are not entries. A suffix
whose keys have S cannot take -S, so its plural is an entry with -Z: conditions
is `TKEU/K-GSZ`.

An outline is not written when the stem stroke writes a word and the affix
stroke, alone, is what often follows it: `TPH-T` is "in the", so inactivity would
turn "active in the" into a word. Nor when the affix stroke alone is a command:
`R-R` is a new line.

Two words on one outline go to the more frequent. An outline another dictionary
writes is left to it.

Usage (needs cmudict and wordfreq, which the phrasing venv has, and the corpora
under ~/.config/plover/phrasing/corpora):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-affixes.py            # write the JSON, print the report
    $PY scripts/gen-affixes.py --check    # fail if the committed JSON is stale
"""
import argparse
import collections
import glob
import importlib.util
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("gen_two_syllable", os.path.join(HERE, "gen-two-syllable.py"))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
g = t.g

OUTPUT = os.path.join(g.PLOVER, "affixes.json")
NAME = "affixes"
MIN_ZIPF = 3.0

# How often "stem word, then what the affix stroke writes" may occur in the
# corpora (17M tokens) before the outline is not written.
MISREAD = 3

# (spellings, phonemes, keys). A phoneme `V` is any vowel, and `A|B` either.
# The keys are the prefix's own: `RE`, `TKE`, `PRO`, the prefix chords of
# two-syllable.json. a- takes `^`, which marks the unstressed syllable before
# the stress there too, so it is a only where a is not stressed.
PREFIXES = [
    (("dis", "des", "dys"), "D V S|Z", "STK"),
    (("inter", "enter"), "V N T ER", "SPW"),
    (("int", "ent"), "V N T", "SPW"),
    (("com",), "K V M", "KPH"),
    (("con",), "K V N", "K"),
    (("col",), "K V L", "KHR"),
    (("cor",), "K V R", "KR"),
    (("re",), "R V", "R"),
    (("de",), "D V", "TK"),
    (("pro",), "P R V", "PR"),
    (("pre", "per"), "P R|ER V|", "PR*"),
    (("ex",), "V K|G S|Z", "KP"),
    (("un",), "AH N", "TPH*"),
    (("in", "im", "en", "em"), "V N|M", "TPH"),
    (("a",), "AH|AE", "^"),
]

# (spellings, phonemes, keys), the keys each suffix has folded into a stroke:
# -tion `-GS` as in action, -ment `-PLT` as in payment. A compound suffix is its
# parts: -tional is -tion and -al, -ical -ic and -al, -ality -al and -ity. None
# has -D, and only -ize has -Z, so -ed and the possessive fold onto any of them.
SUFFIXES = [
    (("ational",), "EY SH AH N AH L", "LGS"),
    (("itional",), "IH SH AH N AH L", "LGS"),
    (("tional", "sional"), "SH|ZH AH N AH L", "LGS"),
    (("ation",), "EY SH AH N", "GS"),
    (("ition",), "IH SH AH N", "GS"),
    (("tion", "sion", "ssion", "cion"), "SH|ZH|CH AH N", "GS"),
    (("ment",), "M AH|EH N T", "PLT"),
    (("ness",), "N AH|IH S", "PBS"),
    (("ance", "ence"), "AH N S", "PBS"),
    (("ant", "ent"), "AH N T", "PBT"),
    (("ality",), "AE L AH|IH T IY", "LT"),
    (("ity",), "AH|IH T IY", "T"),
    (("able", "ible"), "AH B AH L", "BL"),
    (("ative",), "AH|EY T IH V", "F"),
    (("ive",), "IH V", "F"),
    (("ious",), "IY AH S", "S"),
    (("ous",), "AH S", "S"),
    (("ize", "ise"), "AY Z", "Z"),
    (("ist",), "IH|AH S T", "FT"),
    (("ism",), "IH Z AH M", "FPL"),
    (("ical",), "IH|AH K AH L", "BLG"),
    (("ic",), "IH|AH K", "BG"),
    (("ful",), "F AH L", "FL"),
    (("less",), "L AH S", "LS"),
    (("ular",), "Y AH L ER", "LR"),
    (("ial",), "IY AH L", "L"),
    (("al",), "AH L", "L"),
    (("ly",), "L IY", "L"),
    (("er", "or", "ure"), "ER", "R"),
]


def phones_match(pron, pattern):
    """Whether the phonemes are the pattern's, ignoring stress. An empty
    alternative matches no phoneme: `V|` is an optional vowel."""
    def go(ps, pat):
        if not pat:
            return [ps]
        out = []
        for alt in pat[0].split("|"):
            if alt == "":
                out += go(ps, pat[1:])
            elif ps and (ps[0][:-1] if ps[0][-1].isdigit() else ps[0]) == alt \
                    or ps and alt == "V" and ps[0][-1].isdigit():
                out += go(ps[1:], pat[1:])
        return out
    return go(list(pron), pattern.split())


def affix_keys(prefix, suffix):
    """The affix stroke: the prefix on the left bank and the suffix on the right."""
    star = "*" in prefix
    left = prefix.replace("*", "")
    mark = "^" if left == "^" else ""
    left = "" if mark else left
    return mark + left + ("*" if star else "-") + "".join(k for k in "FRPBLGTSDZ" if k in suffix)


def parses(word, pron):
    """(prefix keys, stem letters, stem phonemes, suffix keys) for each way the
    word splits into a prefix, a stem of one or two syllables and a suffix. A
    consonant doubled across the prefix's end is the stem's too: commission is
    com and mission."""
    for pspell, pphones, pkeys in PREFIXES:
        p = next((x for x in pspell if word.startswith(x)), None)
        if not p:
            continue
        for rest in phones_match(pron, pphones):
            n = len(pron) - len(rest)
            if pkeys == "^" and pron[0][-1] == "1":
                continue
            shared = p[-1] not in "aeiou" and word[len(p):len(p) + 1] == p[-1]
            if shared:
                n -= 1
            for sspell, sphones, skeys in SUFFIXES:
                s = next((x for x in sspell if word.endswith(x)), None)
                if not s or len(p) + len(s) >= len(word):
                    continue
                pat = " ".join(reversed(sphones.split()))
                for left in phones_match(list(reversed(pron[n:])), pat):
                    stem = pron[n:len(pron) - len(pron[n:]) + len(left)]
                    if 1 <= sum(q[-1].isdigit() for q in stem) <= 2:
                        yield pkeys, word[len(p):len(word) - len(s)], stem, skeys


def stem_stroke(letters, phones, table, coda, onsets):
    """The stroke for a stem: of one syllable as one-syllable words write it, of
    two as two-syllable.json does, the more stressed vowel carrying the stress.
    An onset no one-syllable word has (the kt of activity) is not written."""
    vowels = [i for i, q in enumerate(phones) if q[-1].isdigit()]
    if tuple(phones[:vowels[0]]) not in onsets:
        return None
    if len(vowels) == 2:
        rank = {"1": 0, "2": 1, "0": 2}
        top = min(vowels, key=lambda i: (rank[phones[i][-1]], i))
        pron = [q[:-1] + ("1" if i == top else "0") if q[-1].isdigit() else q for i, q in enumerate(phones)]
        return t.stroke_of(letters, pron, table, coda)
    st = g.stressed([q[:-1] + "1" if q[-1].isdigit() else q for q in phones])
    sp = g.spell(letters)
    if not st or not sp:
        return None
    onset, sound, co = st
    rule = g.rule_for(table, sp, sound)
    right = coda(co, re.sub(r"g?h$", "", sp.coda)) if co else ("", False)
    if not rule or right is None or any(q not in t.LEFT for q in onset):
        return None
    left = set()
    for q in onset:
        left |= set("S" if q == "Z" and "z" not in letters else t.LEFT[q])
    return str(g.Stroke("".join(k for k in "STKPWHR" if k in left), rule.keys,
                        rule.star or right[1], right[0]))


def outline_for(word, table, coda, onsets):
    """The rule's outline for `word`, or None when it is not in scope. The longest
    affixes win: invitation is in…ation, not in…tion."""
    best = None
    for pron in g.prons(word):
        for pkeys, letters, phones, skeys in parses(word, pron):
            stroke = stem_stroke(letters, phones, table, coda, onsets)
            if stroke:
                cand = (len(letters), f"{stroke}/{affix_keys(pkeys, skeys)}")
                best = min(best or cand, cand)
    return best and best[1]


# --- Misreads ------------------------------------------------------------------

COMMAND = re.compile(r"\{#|\{PLOVER|\{:|\{\^~\||\n|\[KEY\]|\[U\+\]|\[META")


def is_command(record):
    """Whether a translation, in Plover's syntax or the record language, does
    something other than write text."""
    return bool(COMMAND.search(record))


def plain_text(record):
    """The words a translation writes, or None when it is a command. Attach marks
    are only spacing: `{^ly}` and `ly[ORTHO_L]` write ly."""
    if is_command(record):
        return None
    return tuple(re.findall(r"[a-z']+", re.sub(r"\[[A-Z_]+\]|[{}^]", " ", record).lower())) or None


def count_ngrams(wanted):
    """{ngram: count} over the phrasing corpora, for the n-grams in `wanted`."""
    counts = collections.Counter()
    longest = max(map(len, wanted), default=0)
    for path in sorted(glob.glob(os.path.join(g.PLOVER, "phrasing", "corpora", "*", "*-sentences.txt"))):
        with open(path, encoding="utf-8", errors="ignore") as f:
            for line in f:
                toks = re.findall(r"[a-z']+", line.split("\t", 1)[-1].lower())
                for n in range(2, longest + 1):
                    for i in range(len(toks) - n + 1):
                        gram = tuple(toks[i:i + n])
                        if gram in wanted:
                            counts[gram] += 1
    return counts


# --- Build ---------------------------------------------------------------------

def build(typey, table, result):
    coda = t.Coda(typey, result)
    words = sorted((w for w in typey.known if re.fullmatch("[a-z]+", w) and g.zipf(w) >= MIN_ZIPF),
                   key=lambda w: (-g.zipf(w), w))
    onsets = {tuple(on) for w in typey.words.values() for on, _ in w.phones}
    outlines = {w: o for w in words if (o := outline_for(w, table, coda, onsets))}
    # -S is the plural, and a suffix chord that has S cannot fold it, so the
    # plural is an entry with -Z, as Typey Type's differences is TKEUFRPBSZ.
    plural = {}
    for w, o in list(outlines.items()):
        if "S" in re.split(r"[-*]", o)[-1] and not o.endswith("Z"):
            for f in sorted(g.regular_forms(w, "s") & typey.known):
                outlines[f], plural[f] = o + "Z", w

    stack = g.Stack(json.load(open(g.OUTPUT)), skip=NAME)
    singles = {s for o in outlines.values() for s in o.split("/")}
    answers = stack.ask(list(outlines.values()) + sorted(singles))
    said = {s: answers[s][0][1] for s in singles if answers.get(s)}

    # "stem word, then the affix stroke's words", for each outline that could misread.
    risk = {}
    for w, o in outlines.items():
        stem, affix = o.split("/")
        if stem in said and affix in said:
            a, b = plain_text(said[stem]), plain_text(said[affix])
            risk[w] = "command" if is_command(said[affix]) else (a + b if a and b else None)
    counts = count_ngrams({r for r in risk.values() if isinstance(r, tuple)})
    misread = {w: r for w, r in risk.items() if r == "command" or (r and counts[r] >= MISREAD)}

    by = collections.defaultdict(list)
    for w, o in outlines.items():
        if w not in misread:
            by[o].append(w)
    out, lost, left = {}, [], []
    for o, ws in by.items():
        ws.sort(key=lambda w: (-g.zipf(w), w))
        found = [(n, tr) for n, tr in answers.get(o, []) if n != NAME]
        if found and found[0][1] != ws[0] and not (
                found[0][0] == "typey-type" and g.zipf(found[0][1]) <= g.zipf(ws[0]) + g.DISPLACE):
            left += [(w, o, found[0]) for w in ws]
            continue
        out[o] = ws[0]
        lost += [(w, ws[0], o) for w in ws[1:]]
    kept = set(out.values())
    out = {o: w for o, w in out.items() if plural.get(w, w) in kept}
    return dict(sorted(out.items())), dict(outlines=outlines, plural=plural, misread=misread,
                                          counts=counts, lost=lost, left=left, said=said)


def report(out, r):
    z = g.zipf
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 8]) for i in range(0, len(items), 8))
    print(f"{len(r['outlines']) - len(r['plural'])} words in scope; {len(out)} entries, "
          f"{sum(w in r['plural'] for w in out.values())} of them plurals with -Z")
    m = sorted(r["misread"].items(), key=lambda x: (-z(x[0]), x[0]))
    print(f"{len(m)} not written, because the stem and the affix stroke read as text:")
    print(wrap([f"{w}({'new line' if v == 'command' else ' '.join(v) + ' x' + str(r['counts'][v])})"
                for w, v in m]))
    lost = sorted(r["lost"], key=lambda x: (-z(x[0]), x))
    print(f"{len(lost)} lose their outline to a more frequent word:")
    print(wrap([f"{w}~{win}" for w, win, _ in lost]))
    left = sorted(r["left"], key=lambda x: (-z(x[0]), x[:2]))
    print(f"{len(left)} leave their outline to another dictionary:")
    print(wrap([f"{w}={o}({n}:{tr})" for w, o, (n, tr) in left]))
    keys = collections.Counter(o.split("/")[1] for o in out)
    print("affix strokes, most used first:", " ".join(f"{k}:{n}" for k, n in keys.most_common(30)))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the committed JSON is stale")
    args = ap.parse_args()

    typey = g.Typey.load()
    table, _ = g.load_table()
    result = g.resolve(typey, table)
    out, r = build(typey, table, result)
    if args.check:
        with open(OUTPUT) as f:
            if json.load(f) != out:
                sys.exit(f"{OUTPUT} is stale: run {sys.argv[0]}")
        return
    t.write_json(out, OUTPUT)
    report(out, r)


if __name__ == "__main__":
    main()
