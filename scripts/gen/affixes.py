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
- The stem. Its letters and phonemes, the more stressed vowel carrying the
  stress, are an entry of the lexicon's kind, and `dictionaries/affixes-stems.lexc`
  lists them. The pipeline recipe.toml names writes each as a stroke: one
  syllable as one-syllable words are written, two as two-syllable.json writes
  them. A stem whose onset no one-syllable word has (the kt of activity) is
  not listed.
- Which words keep their outline. Two words on one outline go to the more
  frequent, and an outline another dictionary writes is left to it. An outline is
  not written when the stem stroke writes a word and the affix stroke alone is a
  command, or is text that follows that word three times in the corpora: `TPH-T`
  is "in the", so inactivity would turn "active in the" into a word.

It writes the stems, compiles the pipeline, and then writes
`dictionaries/affixes-lexicon.xfst`, the affix tables and one line per word, and
`tests/golden/affixes.tsv`, every entry the grammar should write with
the outline this script computes for it, so the grammar and this script are held
to the same rule.

Usage (needs cmudict and wordfreq, which the phrasing venv has, the corpora under
~/.config/plover/phrasing/corpora, a compiled stack, and HFST: run it in the dev
shell):

    PY=~/.config/plover/phrasing/.venv/bin/python
    $PY scripts/gen-affixes.py            # write the lexicon and golden file, print the report
    $PY scripts/gen-affixes.py --check    # fail if either is stale
"""
import argparse
import collections
import os
import re
import sys
import tomllib

import plover_tape

from . import ROOT, corpora
from . import vowels as g
from .lexicon import MIN_ZIPF, align, lexc
from .resolve import candidates, compile_pipeline, resolve, typey_gives_way
from .stack import Stack

SYSTEM = plover_tape.System.load(g.SYSTEM)

TABLE = os.path.join(ROOT, "dictionaries", "affixes.toml")
LEXICON = os.path.join(ROOT, "dictionaries", "affixes-lexicon.xfst")
STEMS = os.path.join(ROOT, "dictionaries", "affixes-stems.lexc")
GOLDEN = os.path.join(ROOT, "tests", "golden", "affixes.tsv")
NAME = "affixes"

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


def stem_entry(letters, phones, onsets):
    """The lexicon entry of a stem, which the pipeline writes as a stroke: of one
    syllable as one-syllable words write it, of two as two-syllable.json does,
    the more stressed vowel carrying the stress. None where the stem does not
    align, or has an onset no one-syllable word has (the kt of activity)."""
    vowels = [i for i, q in enumerate(phones) if q[-1].isdigit()]
    if tuple(phones[:vowels[0]]) not in onsets:
        return None
    rank = {"1": 0, "2": 1, "0": 2}
    top = min(vowels, key=lambda i: (rank[phones[i][-1]], i))
    pron = [q[:-1] + ("1" if i == top else "0") if q[-1].isdigit() else q for i, q in enumerate(phones)]
    entry = align(letters, pron)
    return entry and str(entry)


def stems(words, onsets):
    """{word: [(stem entry, prefix keys, prefix, stem letters, suffix keys, suffix)]}."""
    out = {}
    for w in words:
        for pron in g.prons(w):
            for pkeys, p, letters, phones, skeys, s in parses(w, pron):
                if (e := stem_entry(letters, phones, onsets)):
                    out.setdefault(w, []).append((e, pkeys, p, letters, skeys, s))
    return out


def outline_for(parsed, strokes):
    """(outline, (prefix, stem letters, suffix)) by the rule, or None when no
    parse's stem has a stroke. The longest affixes win: invitation is
    in…ation, not in…tion."""
    best = None
    for e, pkeys, p, letters, skeys, s in parsed:
        if (stroke := next(iter(strokes.get(e, {None: 0})))):
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

# `stfst_copy` writes the word before it again, so it is text and not a command.
COPY = re.compile(r"\{:stfst_copy:1\}")
COMMAND = re.compile(r"\{#|\{PLOVER|\{:(?!stfst_copy:)|\{\^~\||[\x00-\x1f]")


def is_command(translation):
    """Whether a translation does something other than write text."""
    return bool(COMMAND.search(translation))


def plain_text(translation, before=()):
    """The words a translation writes after the words `before`, or None when it
    is a command. Attach marks are only spacing: `{^ly}` writes ly."""
    if is_command(translation):
        return None
    text = COPY.sub(" " + " ".join(before[-1:]) + " ", translation)
    return tuple(re.findall(r"[a-z']+", re.sub(r"[{}^]", " ", text).lower())) or None


# --- Build ---------------------------------------------------------------------

def build(typey, parsed, strokes):
    """({word: (stem stroke, prefix, stem letters, suffix)}, {outline: word}, the
    report): the lexicon, and every entry the grammar writes from it."""
    found = {w: f for w, ps in parsed.items() if (f := outline_for(ps, strokes))}
    outlines = {w: o for w, (o, _) in found.items()}
    plurals = {w: (pl, o + "Z") for w, (o, (_, _, s)) in found.items() if (pl := plural(w, s))}

    stack = Stack(g.written(), skip=(NAME,))
    singles = {s for o in outlines.values() for s in o.split("/")}
    answers = stack.ask(list(outlines.values()) + [o for _, o in plurals.values()] + sorted(singles))
    said = {s: answers[s][0][1] for s in singles if answers.get(s)}

    # "stem word, then the affix stroke's words", for each outline that could misread.
    risk = {}
    for w, o in outlines.items():
        stem, affix = o.split("/")
        if stem in said and affix in said:
            a = plain_text(said[stem])
            b = plain_text(said[affix], a or ())
            risk[w] = "command" if is_command(said[affix]) else (a + b if a and b else None)
    counts = corpora.ngrams("affix-ngrams", {r for r in risk.values() if isinstance(r, tuple)})
    misread = {w: r for w, r in risk.items() if r == "command" or (r and counts[r] >= MISREAD)}

    taken = {o: theirs[0] for o, found in answers.items()
             if (theirs := [(n, tr) for n, tr in found if n != NAME])}
    out, lost, left = resolve({w: {o: 0} for w, o in outlines.items() if w not in misread},
                              lambda w: (-g.zipf(w), w), taken, typey_gives_way())
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
    rel = os.path.relpath(TABLE, ROOT)
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


def stems_lexc(parsed):
    """dictionaries/affixes-stems.lexc: each stem, which the pipeline writes."""
    entries = sorted({e for ps in parsed.values() for e, *_ in ps})
    return lexc("Written by scripts/gen-affixes.py: every stem of a word the affix grammar\n"
                "could write, as the lexicon writes an entry. Do not edit.",
                [(e, e, None) for e in entries])


def parse_words(typey):
    """stems() over every word in scope."""
    words = sorted((w for w in typey.known if re.fullmatch("[a-z]+", w) and g.zipf(w) >= MIN_ZIPF),
                   key=lambda w: (-g.zipf(w), w))
    onsets = {tuple(on) for w in typey.words.values() for on, _ in w.phones}
    return stems(words, onsets)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--check", action="store_true", help="fail if the lexicon or golden file is stale")
    args = ap.parse_args()

    typey = g.Typey.load()
    parsed = parse_words(typey)
    text = stems_lexc(parsed)
    current = os.path.exists(STEMS) and open(STEMS).read() == text
    if args.check and not current:
        sys.exit(f"{STEMS} stale: run {sys.argv[0]}")
    if not args.check:
        if not current:
            with open(STEMS, "w") as f:
                f.write(text)
        compile_pipeline(NAME)
    lexicon, out, r = build(typey, parsed, candidates(NAME))
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
