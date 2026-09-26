#!/usr/bin/env python3
"""Write the lexicon of the affix grammar: which words `dictionaries/affixes.xfst`
writes, and the stem stroke of each.

A word with a prefix and a suffix is the stem's stroke, then one stroke with the
prefix's keys on the left bank and the suffix's on the right, no vowel:
reservation is serve `SEFRB`, then re…ation `R-GS`. The grammar holds that rule
and the plural; `dictionaries/affixes.toml` holds each affix's spellings, sound
and keys. What this script decides is the rest, which is not a rule:

- The parse. The word's CMU phonemes must split into the prefix's sound, a stem
  of one or two syllables, and the suffix's. The longest affixes win: invitation
  is in…ation, not in…tion.
- The stem's stroke. One syllable is written as one-syllable words write it, by
  the vowel theory's chord and the consonants by sound; two as two-syllable.json
  does, the more stressed vowel carrying the stress.
- Which words keep their outline. Two words on one outline go to the more
  frequent, and an outline another dictionary writes is left to it. An outline is
  not written when the stem stroke writes a word and the affix stroke alone is a
  command, or is text that follows that word three times in the corpora: `TPH-T`
  is "in the", so inactivity would turn "active in the" into a word.

It writes `dictionaries/affixes-lexicon.xfst`, the affix tables and one line per
word, and `tests/golden/affixes.tsv`, every entry the grammar should write with
the outline this script computes for it, so the grammar and this script are held
to the same rule.

Usage (needs cmudict and wordfreq, which the phrasing venv has, the corpora under
~/.config/plover/phrasing/corpora, and a compiled stack):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-affixes.py            # write the lexicon and golden file, print the report
    $PY scripts/gen-affixes.py --check    # fail if either is stale
"""
import argparse
import collections
import glob
import importlib.util
import json
import os
import re
import sys
import tomllib

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("gen_two_syllable", os.path.join(HERE, "gen-two-syllable.py"))
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)
g = t.g
sys.path.insert(0, HERE)
import plover_tape

SYSTEM = plover_tape.System.load(g.SYSTEM)

TABLE = os.path.join(HERE, "..", "dictionaries", "affixes.toml")
LEXICON = os.path.join(HERE, "..", "dictionaries", "affixes-lexicon.xfst")
GOLDEN = os.path.join(HERE, "..", "tests", "golden", "affixes.tsv")
NAME = "affixes"
MIN_ZIPF = 3.0

# How often "stem word, then what the affix stroke writes" may occur in the
# corpora (17M tokens) before the outline is not written.
MISREAD = 3


def load_affixes(path=TABLE):
    """(prefixes, suffixes, countable suffix spellings), each affix a row of
    (spellings, phonemes, keys) in the file's order."""
    with open(path, "rb") as f:
        doc = tomllib.load(f)
    rows = lambda kind: [(tuple(r["spellings"]), r["sound"], r["keys"]) for r in doc[kind]]
    countable = {s for r in doc["suffix"] if r.get("countable") for s in r["spellings"]}
    return rows("prefix"), rows("suffix"), countable


PREFIXES, SUFFIXES, COUNTABLE = load_affixes()


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
    """(prefix keys, prefix, stem letters, stem phonemes, suffix keys, suffix) for
    each way the word splits into a prefix, a stem of one or two syllables and a
    suffix. A consonant doubled across the prefix's end is the stem's sound too:
    commission is com and mission."""
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
                        yield pkeys, p, word[len(p):len(word) - len(s)], stem, skeys, s


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
    """(outline, (prefix, stem letters, suffix)) by the rule, or None when `word`
    is not in scope. The longest affixes win: invitation is in…ation, not
    in…tion."""
    best = None
    for pron in g.prons(word):
        for pkeys, p, letters, phones, skeys, s in parses(word, pron):
            stroke = stem_stroke(letters, phones, table, coda, onsets)
            if stroke:
                cand = (len(letters), f"{stroke}/{affix_keys(pkeys, skeys)}", (p, letters, s))
                best = min(best or cand, cand)
    return best and best[1:]


def plural(word, suffix):
    """The -Z entry's word, where the grammar writes one: a countable noun whose
    suffix keys hold the -S that would fold its plural."""
    keys = next(k for sp, _, k in SUFFIXES if suffix in sp)
    if suffix in COUNTABLE and "S" in keys:
        forms = g.regular_forms(word, "s")
        assert len(forms) == 1, (word, forms)
        return forms.pop()
    return None


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
    """({word: (stem stroke, prefix, stem letters, suffix)}, {outline: word}, the
    report): the lexicon, and every entry the grammar writes from it."""
    coda = t.Coda(typey, result)
    words = sorted((w for w in typey.known if re.fullmatch("[a-z]+", w) and g.zipf(w) >= MIN_ZIPF),
                   key=lambda w: (-g.zipf(w), w))
    onsets = {tuple(on) for w in typey.words.values() for on, _ in w.phones}
    found = {w: f for w in words if (f := outline_for(w, table, coda, onsets))}
    outlines = {w: o for w, (o, _) in found.items()}
    plurals = {w: (pl, o + "Z") for w, (o, (_, _, s)) in found.items() if (pl := plural(w, s))}

    stack = g.Stack(g.written(), skip=NAME)
    singles = {s for o in outlines.values() for s in o.split("/")}
    answers = stack.ask(list(outlines.values()) + [o for _, o in plurals.values()] + sorted(singles))
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
        taken = [(n, tr) for n, tr in answers.get(o, []) if n != NAME]
        if taken and taken[0][1] != ws[0] and not (
                taken[0][0] == "typey-type" and g.zipf(taken[0][1]) <= g.zipf(ws[0]) + g.DISPLACE):
            left += [(w, o, taken[0]) for w in ws]
            continue
        out[o] = ws[0]
        lost += [(w, ws[0], o) for w in ws[1:]]
    lexicon = {w: (o.split("/")[0], *found[w][1]) for o, w in out.items()}
    # A plural is the grammar's, so it follows its singular. One another
    # dictionary also writes is reported, not dropped.
    shadow = []
    for w in lexicon:
        if w in plurals:
            pl, o = plurals[w]
            out[o] = pl
            shadow += [(pl, o, a) for a in answers.get(o, [])[:1] if a[0] != NAME]
    return lexicon, dict(sorted(out.items())), dict(
        outlines=outlines, plurals=plurals, misread=misread, counts=counts,
        lost=lost, left=left, shadow=shadow)


# --- Output --------------------------------------------------------------------

def xfst_string(text):
    """A symbol string as xfst reads it: alphanumeric runs in braces, every other
    character quoted on its own."""
    return " ".join(f"{{{run}}}" if run.isalnum() else f'"{run}"'
                    for run in re.findall(r"[A-Za-z]+|.", text))


def lexicon_xfst(lexicon):
    """dictionaries/affixes-lexicon.xfst: the tables of affixes.toml as spelling
    to keys, the countable suffixes, and one line per word."""
    rel = os.path.relpath(TABLE, os.path.join(HERE, ".."))
    union = lambda items: "\n    | ".join(items)
    pair = lambda upper, lower: f"[ [ {xfst_string(upper)} ] .x. [ {xfst_string(lower)} ] ]"
    pre = [pair(sp, SYSTEM.tape(k)) for sps, _, k in PREFIXES for sp in sps]
    suf = [pair(sp, SYSTEM.tape("-" + k)) for sps, _, k in SUFFIXES for sp in sps]
    countable = [xfst_string(sp) for sps, _, _ in SUFFIXES for sp in sps if sp in COUNTABLE]
    words = [" \"|\" ".join([xfst_string(SYSTEM.tape(stroke)), xfst_string(p), xfst_string(stem), xfst_string(s)])
             for w, (stroke, p, stem, s) in sorted(lexicon.items())]
    return (f"! Written by scripts/gen-affixes.py from {rel} and the words it chose.\n"
            "! Do not edit: see dictionaries/affixes.xfst for what these mean.\n\n"
            f"define AffixPrefix [ {union(pre)} ] ;\n\n"
            f"define AffixSuffix [ {union(suf)} ] ;\n\n"
            f"define AffixCountable [ {union(countable)} ] ;\n\n"
            f"! {len(words)} words: the stem's stroke, then prefix, stem and suffix.\n"
            f"define AffixWords [ {union(words)} ] ;\n")


def golden(out):
    """tests/golden/affixes.tsv: every entry, at the outline this script gives it."""
    lines = ["! Every entry dictionaries/affixes.xfst writes, at the outline",
             "! scripts/gen-affixes.py computes for it. Written by that script."]
    return "\n".join(lines + [f"{SYSTEM.tape(o)}\t{w}" for o, w in out.items()]) + "\n"


def entries():
    """{outline: word} from the committed golden file, for readers that want what
    the dictionary writes without building it."""
    with open(GOLDEN) as f:
        rows = [line.rstrip("\n").split("\t") for line in f if not line.startswith("!")]
    return {SYSTEM.outline(tape): word for tape, word in rows}


def report(lexicon, out, r):
    z = g.zipf
    wrap = lambda items: "\n".join("  " + " ".join(items[i:i + 8]) for i in range(0, len(items), 8))
    print(f"{len(r['outlines'])} words in scope; {len(lexicon)} in the lexicon, "
          f"{len(out)} entries with the plurals on -Z")
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
    print(f"{len(r['shadow'])} plurals on an outline another dictionary also writes:")
    print(wrap([f"{w}={o}({n}:{tr})" for w, o, (n, tr) in r["shadow"]]))
    keys = collections.Counter(o.split("/")[1] for o in out)
    print("affix strokes, most used first:", " ".join(f"{k}:{n}" for k, n in keys.most_common(30)))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the lexicon or golden file is stale")
    args = ap.parse_args()

    typey = g.Typey.load()
    table, _ = g.load_table()
    result = g.resolve(typey, table)
    lexicon, out, r = build(typey, table, result)
    files = {LEXICON: lexicon_xfst(lexicon), GOLDEN: golden(out)}
    if args.check:
        stale = [p for p, text in files.items() if not os.path.exists(p) or open(p).read() != text]
        if stale:
            sys.exit(f"{', '.join(stale)} stale: run {sys.argv[0]}")
        return
    for path, text in files.items():
        with open(path, "w") as f:
            f.write(text)
    report(lexicon, out, r)


if __name__ == "__main__":
    main()
