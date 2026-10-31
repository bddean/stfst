#!/usr/bin/env python3
"""Write the words a layout design is scored on, each as the atoms of its strokes.

`stfst-layout` runs this and caches what it prints. It reads one JSON object on
stdin, the design's `[words]` and `[encode]` tables, and prints JSON lines: a
`meta` line, then one line per word, `{"w", "f", "nsyl", "kind", "strokes"}`,
where `strokes` lists each stroke's atoms (`V:AE`, `L:S`, `Wa:AH`), and the
`ireland` scheme adds `clusters`, each onset and coda in spoken order. An atom
names one sound in one slot; the solver gives it a chord, and a stroke is the
union of its atoms' chords.

Sources (`words.source`):
- `handoff`: the foot-steno handoff's `allw.jsonl` (CMUdict, wordfreq, MorphoLex).
- `stfst`: `dictionaries/lexicon.lexc`, each word's first CMU pronunciation.
  Its inflection base is found by sound: a word is its stem's pronunciation
  plus -s, -ed or -ing.

- `stfst-two-syllable`: stfst's own two-syllable dictionary as
  `scripts/gen-two-syllable.py` resolves it today: its words, their outlines
  from the compiled pipeline with their costs and rank, and what the rest of
  the stack holds. It needs the phrasing venv and a compiled stack; see
  `two_syllable_units`.

Schemes (`encode.scheme`) port the handoff's encodings so its numbers can be
reproduced: `foot` is `fv2.py`'s, with its ENC names as `encode.rules`;
`ireland` is `theory.chunk_atoms`, the Ireland + weak-syllable-marker baseline.
"""
import collections
import itertools
import json
import os
import re
import sys
from collections import Counter

VOW = set("AA AE AH AO AW AY EH ER EY IH IY OW OY UH UW".split())
STOP_PLACE = {"P": "lab", "B": "lab", "T": "cor", "D": "cor", "K": "dor", "G": "dor", "CH": "cor", "JH": "cor"}
NAS_PLACE = {"M": "lab", "N": "cor", "NG": "dor"}
MERGE = {"IY": "IH", "EY": "EH", "UW": "UH", "OW": "AO"}

# The handoff's list; these are briefed, not written by theory.
FUNCTION_WORDS = set("""
a an the this that these those there here where when what which who whom whose why how
i me my mine myself you your yours yourself yourselves he him his himself she her hers herself
it its itself we us our ours ourselves they them their theirs themselves one ones
am is are was were be been being have has had having do does did doing done
will would shall should can could may might must ought need dare
not no nor yes yeah ok okay oh um uh
and or but if then than so because as while until unless though although whether since
of in on at by for with about against between into through during before after above below
to from up down out off over under again further once onto upon within without along across
behind beyond toward towards among around near past via per like unlike
all any both each few more most other some such only own same too very just also even
much many lot lots little less least enough every either neither another
s t don didn doesn isn wasn aren weren won wouldn couldn shouldn hasn haven hadn mustn
ll ve re d m
let lets get got gets
""".split())


def bare(p):
    return re.sub(r"\d", "", p)


def is_vowel(p):
    return p[-1].isdigit()


# ---- sources: records {w, f, ph, nsyl, base, segm}

def handoff_records(path):
    for line in open(path):
        r = json.loads(line)
        yield {"w": r["w"], "f": r["f"], "ph": r["ph"], "nsyl": r["nsyl"], "base": r["base"], "segm": r["segm"]}


def stfst_records(path):
    """Rank-1 pronunciations of lexicon.lexc, with the inflection base found by sound."""
    words = {}
    for line in open(path):
        m = re.match(r"^(\S+?):(.*?)\s+# \"weight: (-?[\d.]+)\" ;$", line)
        if not m or "=" not in m.group(2):
            continue
        w = m.group(1).replace("%", "")
        lower = re.sub(r"%(.)", r"\1", m.group(2))
        rank, *segs = lower.split(" ")
        if rank.rstrip("@") != "1" or w in words:
            continue
        ph = [p for s in segs for p in s.split("=")[0].split(".") if p]
        words[w] = (ph, -float(m.group(3)))
    sounds = {w: tuple(bare(p) for p in ph) for w, (ph, _) in words.items()}
    tails = {"s": {("S",), ("Z",), ("IH", "Z"), ("AH", "Z")},
             "ed": {("T",), ("D",), ("IH", "D"), ("AH", "D")},
             "ing": {("IH", "NG")}}
    for w, (ph, z) in words.items():
        base = None
        for infl, tail in tails.items():
            if not w.endswith(infl):
                continue
            stem = w[:-len(infl)]
            # walk, use (used), stop (stopped), box (boxes), try (tried)
            for cand in (stem, stem + "e", stem[:-1], stem[:-1] + "y"):
                if cand in words and cand != w and sounds[w][:len(sounds[cand])] == sounds[cand] \
                        and sounds[w][len(sounds[cand]):] in tail:
                    base = cand
                    break
            if base:
                break
        yield {"w": w, "f": 10 ** (z - 9), "ph": ph, "nsyl": sum(map(is_vowel, ph)), "base": base, "segm": None}


# ---- theory.load

def load(records, minf, max_syl, function_words, merge=True):
    rs = list(records)
    onsets = set()
    for r in rs:
        ph = [bare(p) for p in r["ph"]]
        k = 0
        while k < len(ph) and ph[k] not in VOW:
            k += 1
        onsets.add(tuple(ph[:k]))
    pron = {r["w"]: r["ph"] for r in rs}
    units = {}
    for r in rs:
        w = r["w"]
        if r["f"] < minf or len(w) < 2 or (not function_words and w in FUNCTION_WORDS) \
                or not re.search("[aeiouy]", w) or r["nsyl"] > max_syl:
            continue
        key = tuple(bare(p) for p in r["ph"]) if merge else w
        segm = r["segm"]
        kind = "inflected" if r["base"] else \
            ("derived" if segm and (">" in segm or "<" in segm) else
             "compound" if segm and segm.count("(") > 1 else
             "root" if segm else "unknown")
        if key in units:
            u = units[key]
            if r["f"] > u["f0"]:
                u.update(w=w, f0=r["f"])
            u["f"] += r["f"]
        else:
            infl = None
            if r["base"] and r["base"] in pron:
                infl = "ing" if w.endswith("ing") else "ed" if w.endswith("ed") else "s"
            units[key] = {"w": w, "f": r["f"], "f0": r["f"], "ph": r["ph"], "nsyl": r["nsyl"], "kind": kind,
                          "infl": infl, "base_ph": pron.get(r["base"]) if infl else None}
    return list(units.values()), onsets


def split_word(ph, policy, onsets):
    """(phones, centre index) chunks: one around the primary stress, one per full
    vowel, or one per vowel (`syllable`)."""
    vi = [i for i, p in enumerate(ph) if is_vowel(p)]
    if policy == "one":
        st = [i for i in vi if ph[i][-1] == "1"] or vi[:1]
        return [(ph, st[0])]
    full = vi if policy == "syllable" else [i for i in vi if ph[i][-1] in "12"] or vi[:1]
    if len(full) == 1:
        return [(ph, full[0])]
    cuts = []
    for a, b in zip(full, full[1:]):
        k = b
        while k - 1 > a and not is_vowel(ph[k - 1]) and tuple(bare(p) for p in ph[k - 1:b]) in onsets:
            k -= 1
        cuts.append(k)
    out, start = [], 0
    for c, center in zip(cuts + [len(ph)], full):
        out.append((ph[start:c], center - start))
        start = c
    return out


# ---- scheme "foot" (fv2.py)

class Foot:
    def __init__(self, cfg, units, onsets):
        self.rules = set(cfg.get("rules", []))
        self.onsets = onsets
        unknown = self.rules - {"ordS", "arch", "clus", "end", "tail2", "pfx", "nasrule", "vtrade",
                                "lptie", "nowb"}
        if unknown:
            sys.exit(f"layout-units: unknown foot rules {sorted(unknown)}")
        self.policy = cfg.get("policy", "split")
        self.chunks = [self.chunk(u) for u in units]
        cc, ec, pc = Counter(), Counter(), Counter()
        for u, ch in zip(units, self.chunks):
            for j, (c, s) in enumerate(ch):
                base, vi, wb, wa, slot = self.parse(c, s)
                r = self.r1_run(base, slot)
                if len(r) >= 2:
                    cc[r] += u["f"]
                if j == len(ch) - 1 and wa:
                    ec[self.tail(base, wa)] += u["f"]
            c, s = ch[0]
            base, vi, wb, wa, slot = self.parse(c, s)
            if wb:
                pc[tuple(base[:wb[-1] + 1])] += u["f"]
        self.clus = {k for k, _ in cc.most_common(cfg.get("clusters", 40))}
        self.ends = {k for k, _ in ec.most_common(cfg.get("endings", 12))}
        self.pfx = {k for k, _ in pc.most_common(cfg.get("prefixes", 12))}
        if cfg.get("lists"):
            lists = json.load(open(cfg["lists"]))
            self.clus = {tuple(x) for x in lists["clusters"]}
            self.ends = {tuple(x) for x in lists["endings"]}
            self.pfx = {tuple(x) for x in lists["prefixes"]}

    def lists(self):
        return {"clusters": sorted(self.clus), "endings": sorted(self.ends), "prefixes": sorted(self.pfx)}

    def chunk(self, u):
        ph = u["base_ph"] if u["infl"] else u["ph"]
        ch = split_word(ph, "split" if self.policy == "split" else "one", self.onsets)
        if self.policy == "hyb":
            # one stroke, unless 2+ syllables precede the main stress
            vi = [i for i, p in enumerate(ph) if is_vowel(p)]
            st = ch[0][1]
            if sum(1 for i in vi if i < st) >= 2:
                sp = split_word(ph, "split", self.onsets)
                k = next((j for j, (c, s) in enumerate(sp) if sum(len(x) for x, _ in sp[:j]) + s == st), 0)
                if k > 0:
                    first = [p for c, _ in sp[:k] for p in c]
                    ch = [(first, sp[0][1]), (ph[len(first):], st - len(first))]
        if len(ch) > 2:
            ch = [ch[0], ([p for c, _ in ch[1:] for p in c], ch[1][1])]
        return ch

    @staticmethod
    def parse(ph, s):
        base = [bare(p) for p in ph]
        vi = [i for i, p in enumerate(ph) if is_vowel(p) and i != s] + [s]
        wb = sorted(i for i in vi if i < s)
        wa = sorted(i for i in vi if i > s)
        slot = {}
        for i in range(len(base)):
            if i in vi:
                continue
            if i < s:
                slot[i] = "Lp" if wb and i < wb[-1] else "L"
            else:
                slot[i] = "R2" if wa and i > wa[0] else "R1"
        return base, set(vi), wb, wa, slot

    @staticmethod
    def r1_run(base, slot):
        return tuple(base[i] for i in sorted(slot) if slot[i] == "R1")

    def tail(self, base, wa):
        return tuple(base[wa[0 if "tail2" in self.rules else -1]:]) if wa else None

    def atoms(self, c, s, first, last, infl):
        R = self.rules
        base, vi, wb, wa, slot = self.parse(c, s)
        r = self.r1_run(base, slot)
        v = base[s]
        if "vtrade" in R and len(r) >= 2:
            v = MERGE.get(v, v)
        out = {"V:" + v}
        skip = set()
        if "pfx" in R and wb and first and tuple(base[:wb[-1] + 1]) in self.pfx:
            out.add("P:" + " ".join(base[:wb[-1] + 1]))
            skip = set(range(wb[-1] + 1))
        if "end" in R and last and wa and self.tail(base, wa) in self.ends:
            out.add("E:" + " ".join(self.tail(base, wa)))
            skip |= set(range(wa[0 if "tail2" in R else -1], len(base)))
        for i in wb + wa:
            if "nowb" in R and i < s:
                continue
            if i not in skip:
                out.add(("Wb:" if i < s else "Wa:") + base[i])
        if "clus" in R and len(r) >= 2 and r in self.clus:
            out.add("K:" + " ".join(r))
            skip |= {i for i in slot if slot[i] == "R1"}
        pos, run = {}, 0
        for i in range(len(base)):
            run = 0 if i in vi else run + 1
            pos[i] = run
        for i in sorted(slot):
            if i in skip:
                continue
            p = base[i]
            nxt = base[i + 1] if i + 1 < len(base) else None
            if "arch" in R and p in NAS_PLACE and STOP_PLACE.get(nxt) == NAS_PLACE[p]:
                p = "NAS"
            if i > s and "ordS" in R and p in ("S", "Z"):
                p = f"{p}@{min(pos[i], 3)}"
            if "nasrule" in R and slot[i] in ("R1", "R2") and p in NAS_PLACE:
                out |= {f"{slot[i]}:NAS", f"{slot[i]}:NV", f"{slot[i]}:" + {"M": "P", "N": "T", "NG": "K"}[p]}
                continue
            tag = "L" if ("lptie" in R and slot[i] == "Lp") else slot[i]
            out.add(f"{tag}:{p}")
        if infl and last:
            out.add("I:" + infl)
        return sorted(out)

    def strokes(self, i, u):
        ch = self.chunks[i]
        return [self.atoms(c, s, j == 0, j == len(ch) - 1, u["infl"]) for j, (c, s) in enumerate(ch)]


# ---- scheme "ireland" (theory.chunk_atoms, as irl_effort.py configures it)

def weak_class(v):
    return {"ER": "R", "IY": "Y", "OW": "O", "UW": "U"}.get(v, "S")


class Ireland:
    def __init__(self, cfg, units, onsets):
        self.onsets = onsets
        self.policy = cfg.get("policy", "split")
        self.prefix_keys = {tuple(p.split()) for p in cfg.get("prefix_keys", [])}
        self.le_en = cfg.get("le_en", False)
        self.infl_keys = set(cfg.get("infl_keys", []))
        self.endings = {k: tuple(v.split()) for k, v in cfg.get("endings", {}).items()}
        # Ireland's chords for a nasal before its stop and for -sk, -st.
        self.fuse = cfg.get("fuse", True)

    def lists(self):
        return {}

    def chunk_atoms(self, ph, s, first, last, infl=None):
        """The stroke's atoms, and its onset and coda clusters in order (each
        `{"atoms", "fused"}`, the coda ending in the inflection)."""
        base = [bare(p) for p in ph]
        vi = [i for i, p in enumerate(ph) if is_vowel(p)]
        left, right, markers = [], [], []
        wb = [i for i in vi if i < s]
        wa = [i for i in vi if i > s]
        atoms = []
        pre_skip = set()
        if first and wb and self.prefix_keys:
            pre = tuple(base[i] for i in range(wb[-1]) if i not in vi)
            if len(wb) == 1 and pre in self.prefix_keys:
                atoms.append("P:" + " ".join(pre))
                pre_skip = set(range(wb[-1]))
        strip = set()
        if last and wa:
            tail = base[wa[-1]:]
            for name, pat in self.endings.items():
                if tuple(tail) == pat:
                    atoms.append("E:" + name)
                    strip = set(range(wa[-1], len(base)))
                    break
        for i, p in enumerate(base):
            if i == s or i in pre_skip or i in strip:
                continue
            side = left if i < s else right
            if i in vi:
                if p == "ER":
                    side.append("R")
                    markers.append(("b" if i < s else "a") + "R")
                else:
                    markers.append(("b" if i < s else "a") + weak_class(p))
            else:
                side.append(p)
        if base[s] == "ER":
            right.insert(0, "R")
        if last and wa and not strip and self.le_en:
            t = base[wa[-1] + 1:]
            if t[:1] in (["L"], ["N"]) and len(t) == 1:
                markers.append("aL")
                if "aS" in markers and markers.count("aS") == 1:
                    markers.remove("aS")
        if len(wa) >= 2:
            markers.append("a2")
        if len(wb) >= 2:
            markers.append("b2")

        def fix(seq, side):
            out = []
            for j, p in enumerate(seq):
                nxt = seq[j + 1] if j + 1 < len(seq) else None
                if self.fuse and p in NAS_PLACE and nxt and STOP_PLACE.get(nxt) == NAS_PLACE[p]:
                    out.append("NAS_" + NAS_PLACE[p])
                elif self.fuse and side == "R" and p == "S" and nxt in ("K", "T"):
                    out.append("S_" + nxt)
                else:
                    out.append(p)
            return out

        fixed_left, fixed_right = fix(left, "L"), fix(right, "R")
        atoms += ["L:" + p for p in set(fixed_left)] + ["R:" + p for p in set(fixed_right)]
        atoms += ["V:" + base[s]] + ["M:" + m for m in set(markers)]
        if infl:
            atoms.append("I:" + infl)
        clusters = [{"atoms": ["L:" + p for p in left], "fused": fixed_left != left},
                    {"atoms": ["R:" + p for p in right] + (["I:" + infl] if infl else []), "fused": fixed_right != right}]
        return sorted(set(atoms)), [c for c in clusters if len(c["atoms"]) > 1]

    def encode(self, u):
        use_keys = u["infl"] in self.infl_keys
        chunks = split_word(u["base_ph"] if use_keys else u["ph"], self.policy, self.onsets)
        out, clusters = [], []
        for j, (ph, s) in enumerate(chunks):
            last = j == len(chunks) - 1
            atoms, cs = self.chunk_atoms(ph, s, j == 0, last, u["infl"] if use_keys and last else None)
            out.append(atoms)
            clusters += cs
        return out, clusters


# ---- source "stfst-two-syllable": the real pipeline and stack

TAPE_ORDER = "🦐#@^+STKPWHRAO*EUfrpblgtsdz"
# caret.xfst's mark for each operand, the other vowel's first letter.
MARKS = {"A": "+", "E": "^", "EU": "^+", "O": "+O", "U": "+U", "": "^"}


def caret(op, base):
    """caret.xfst on a contract.tsv line: OP is `-`, or prefix keys and then
    optionally `^` and the operand's vowel keys. two_syllable_units checks it
    against the compiled caret stage, since it also writes voiced stems the
    pipeline never sees."""
    prefix, mark, vowels = op.strip("-").partition("^")
    keys = set(base) | set(prefix)
    if mark:
        keys |= set(MARKS[vowels])
    return "".join(sorted(keys, key=TAPE_ORDER.index))


# ---- contract.xfst on theory.tsv, with the left bank optionally voiced by a key

SYLLABLE = re.compile(r"^([STKPWHR]*)([012])([AOEU]*):([AO*EU]*)([frpblgtsdz]*)$")
# ToLeft: coda keys crossing to the left bank, by their sound: (keys, voiceless keys, the voiced phoneme).
TO_LEFT = [("pblg", "SKWR", "KH", "JH"), ("fp", "KH", "KH", None), ("rb", "SH", "SH", None),
           ("pb", "TPH", "TPH", None), ("pl", "PH", "PH", None), ("bg", "K", "K", None), ("f", "TP", "TP", None),
           ("r", "R", "R", None), ("p", "P", "P", None), ("b", "PW", "P", "B"), ("l", "HR", "HR", None),
           ("g", "TKPW", "K", "G"), ("t", "T", "T", None), ("s", "S", "S", None), ("d", "TK", "T", "D"),
           ("z", "S", "S", "Z")]
TO_RIGHT = [("STKPW", "z"), ("TKPW", "g"), ("SKWR", "pblg"), ("KWR", ""), ("TPH", "pb"), ("PH", "pl"),
            ("PW", "b"), ("TK", "d"), ("TP", "f"), ("SR", "f"), ("HR", "l"), ("KH", "fp"), ("SH", "rb"),
            ("TH", "t"), ("K", "bg"), ("S", "s"), ("T", "t"), ("P", "p"), ("R", "r"), ("W", ""), ("H", "")]
SONORANT = {"TPH", "PH", "HR", "R", "W", "H", "KWR"}
# th/dh and sh/zh stay one chord each, as in the theory.
DEVOICE = {"B": "P", "D": "T", "G": "K", "V": "F", "Z": "S", "JH": "CH"}


def in_order(keys):
    return "".join(sorted(set(keys), key=TAPE_ORDER.index))


class Voice:
    """`encode.voice`: a voiced obstruent on the left bank is its voiceless
    partner's chord plus `key`. `rule` says which consonants of a contracted
    stroke's left bank the key voices: `any` sets it when any is voiced,
    `first` for the first obstruent, `onset` for the stressed syllable's onset;
    the others are written by place alone. `pretonic` is `onset`, but the
    consonants before the stressed syllable keep the theory's chords.
    `voices` lists the voiced phonemes it writes (all of DEVOICE by default);
    the others keep the theory's chords. `fresh` says no dictionary has the
    key yet. With no key it is the theory's own chords, which `encode.bare`
    uses to tell an onset from a skeleton."""

    def __init__(self, cfg, lexc):
        from gen.vowels import LEFT
        self.key = cfg.get("key", "")
        self.rule = cfg.get("rule", "any")
        self.fresh = cfg.get("fresh", False)
        self.devoice = {p: DEVOICE[p] for p in cfg.get("voices", DEVOICE)}
        self.left = {p: ways[0] for p, ways in LEFT.items()}
        count = Counter()
        for r in stfst_records(lexc):
            ph = [bare(p) for p in r["ph"]]
            count[tuple(itertools.takewhile(lambda p: p not in VOW, ph))] += r["f"]
        # The commonest word onset each chord writes, so that a chord reads back as sounds.
        self.onsets = {}
        for seq, _ in count.most_common():
            if all(p in self.left for p in seq):
                self.onsets.setdefault(in_order("".join(self.left[p] for p in seq)), seq)
        self.inverse = collections.defaultdict(list)
        for c in self.onsets:
            keys, v = self.onset(c)
            self.inverse[in_order(keys + self.key * v)].append(c)
        # The left banks a word's onset writes; any other is a skeleton.
        self.chords = set(self.inverse) | {""}

    def onset(self, keys):
        """(voiceless keys, voiced) for an onset chord; a chord no onset writes
        stays as it is."""
        seq = self.onsets.get(in_order(keys))
        if seq is None or not self.key:
            return keys, False
        return in_order("".join(self.left[self.devoice.get(p, p)] for p in seq)), any(p in self.devoice for p in seq)

    def part(self, keys):
        """(keys, voiceless keys, voiced, has an obstruent) for an onset chord."""
        seq = self.onsets.get(in_order(keys))
        obstruent = any(self.left.get(p) not in SONORANT for p in seq) if seq else bool(keys)
        return (keys, *self.onset(keys), obstruent)

    def stroke(self, tape):
        """A stroke of one onset, its left bank voiced."""
        keys, voiced = self.onset("".join(k for k in tape if k in "STKPWHR"))
        return in_order([k for k in tape if k not in "STKPWHR"] + list(keys) + [self.key] * voiced)

    def outline(self, o, system):
        if not self.key:
            return o
        return "/".join(system.outline(self.stroke(system.tape(s))) for s in o.split("/"))

    def preimages(self, o, system):
        """The outlines `outline` takes to `o`."""
        per = []
        for s in o.split("/"):
            tape = system.tape(s)
            left = in_order(k for k in tape if k in "STKPWHR" + self.key)
            rest = [k for k in tape if k not in "STKPWHR" + self.key]
            olds = [in_order(rest + list(c)) for c in self.inverse.get(left, [])]
            if self.stroke(tape) == tape and not (self.fresh and self.key in tape):
                olds.append(tape)
            per.append([system.outline(t) for t in olds if self.stroke(t) == tape])
        return ["/".join(p) for p in itertools.product(*per)]

    def skeleton(self, base):
        return in_order(k for k in base if k in "STKPWHR" + self.key) not in self.chords


def crossed(keys, table):
    """table's rewrites, in order, on a bank's keys: what each match writes."""
    out = []
    for pat, *to in table:
        if pat in keys:
            keys = keys.replace(pat, "|")
            out.append(to)
    return out


def contract(lead, strokes, voice):
    """contract.xfst on a theory.tsv line: [(op, base, voiced base)], the
    voiced base being the base itself when `voice` is None."""
    syl = [SYLLABLE.match(s) for s in strokes.split("/")]
    if not all(syl) or len(syl) > 2:
        return []
    syl = [m.groups() for m in syl]

    def left(pre, onset):
        """The left bank, plain and voiced: `pre` is [(keys, voiceless keys,
        voiced, obstruent)] before the stressed syllable's onset."""
        old = in_order("".join(k for k, *_ in pre) + onset)
        if voice is None or not voice.key:
            return old, old
        _, keys, v, _ = stressed = voice.part(onset)
        if voice.rule == "any":
            v = any(x for _, _, x, _ in pre + [stressed])
        elif voice.rule == "first":
            v = next((x for _, _, x, ob in pre + [stressed] if ob), False)
        before = "".join(k if voice.rule == "pretonic" else unv for k, unv, *_ in pre)
        return old, in_order(before + keys + voice.key * v)

    if len(syl) == 1:
        l, d, head, chord, coda = syl[0]
        if d != "1" or head:
            return []
        old, new = left([], l)
        return [(op, old + chord + coda, new + chord + coda) for op in (lead[:1] or ["-", "^"])]
    mark = (lead[0] if lead and lead[0] != "^" else "") + "^"
    (l1, d1, h1, c1, r1), (l2, d2, h2, c2, r2) = syl
    if d1 == "0" and d2 == "1" and not h2:
        cross = crossed(r1, TO_LEFT)
        pre = l1 + "".join(k for k, _, _ in cross)
        if pre and (l2 in ("H", "KWR") or (l2 == "W" and pre[-1] in "PK")):
            l2 = ""
        voiced = voice.devoice if voice else {}
        cross = [(k, unv, True, True) if p in voiced else (k, k, False, k not in SONORANT) for k, unv, p in cross]
        first = [voice.part(l1) if voice else (l1, l1, False, True)] if l1 else []
        old, new = left(first + cross, l2)
        return [(mark + h1, old + c2 + r2, new + c2 + r2)]
    if d1 == "1" and d2 == "0" and not h1:
        old, new = left([], l1)
        right = in_order(r1 + "".join(k for (k,) in crossed(l2, TO_RIGHT)) + r2)
        return [(mark + h2, old + c1 + right, new + c1 + right)]
    return []


def theory_sides(path):
    """{(word, rank, operand): "a" or "b"} from theory.tsv: whether the stroke
    the operand comes from is after the stressed one or before it."""
    out = {}
    with open(path) as f:
        for line in f:
            w, rest = line.rstrip("\n").split("\t")
            n, *_, strokes = rest.split(" ")
            strokes = strokes.split("/")
            if len(strokes) != 2:
                continue
            after = "1:" in strokes[0]
            m = re.search(r"0([AOEU]*):", strokes[1 if after else 0])
            if m:
                out.setdefault((w, n, m.group(1)), "a" if after else "b")
    return out


def stress_side(pron):
    """The side of the primary stress a word's other vowels are on: "a" when
    any follows it, "b" when all precede it, "1" for one vowel."""
    vowels = [p for p in pron if p[-1].isdigit()]
    if len(vowels) < 2:
        return "1"
    at = next((i for i, p in enumerate(vowels) if p[-1] == "1"), 0)
    return "a" if at < len(vowels) - 1 else "b"


def captured():
    """What gen/two_syllable.py hands `resolve`: (outlines, rank, taken, report)."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from gen import lexicon as lex, two_syllable as t, vowels as g
    from gen import resolve as rs

    seen = {}

    def spy(outlines, rank, taken, gives_way, firsts=frozenset()):
        seen.update(outlines=outlines, rank=rank, taken=taken)
        return real(outlines, rank, taken, gives_way, firsts)

    real, t.resolve = t.resolve, spy
    _, r = t.build(g.Typey.load(), rs.candidates(t.NAME), lex.vocabulary())
    return seen["outlines"], seen["rank"], dict(seen["taken"]), r


def two_syllable_units(words, enc, out):
    """What gen/two_syllable.py hands `resolve`, captured as it runs.

    Each alternative is a list of strokes of atoms. `encode.form` picks them:
    `caret` is the pipeline's outline as one literal atom (`=^PRORT`); `operand`
    is the stroke before caret.xfst (`=PRORT`) plus the operand as an atom
    (`X:E`, `X:^` for the bare mark), so a design can give the operand keys of
    its own; `position` is `operand` with the side of the stress the other
    vowel is on (`X:a:E` after it, `X:b:E` before, `X:1:^` for a word of one
    syllable). An outline that caret() does not rebuild stays literal.
    The pipeline's own fallbacks are dropped, and `encode.fallback` adds
    outlines after the word's own, as the outlines a word that loses would
    keep, each alternative tagged with its fallback:
    `"typey"` its Typey Type outlines, `"theory"` the two strokes theory.tsv
    writes before contract.xfst joins them, or a list of both, in order.
    `encode.reach` is the keys whose chords on the base stroke are asked of
    the stack, or a list of those chords. `encode.voice` (see `Voice`)
    rewrites every outline's left bank, the stack's too: an outline is held
    when an outline the stack holds rewrites to it. `encode.bare` adds, before
    a two-syllable stroke, the same stroke unmarked where its left bank is a
    skeleton.

    A `held` line is an outline the rest of the stack writes. Its `f` is the
    frequency a word needs to take it from Typey Type (zipf - DISPLACE), or
    null where the holder never gives way: a word takes it at that frequency or above. An alternative's `displace` is
    whether its word may take a Typey Type stroke at all."""
    outlines, rank, taken, r = captured()
    from gen import two_syllable as t, vowels as g
    from gen import resolve as rs
    from gen.stack import _SYSTEM, retro_words
    base = r["base"]
    stem = lambda w: base[w][0] if w in base else w

    split = collections.defaultdict(dict)
    pipe = dict(rs.pipeline(t.NAME))
    voice = Voice(enc.get("voice", {}), os.path.join(os.path.dirname(g.TABLE), "lexicon.lexc"))
    voiced = {}
    if voice.key:
        with open(pipe["theory"]) as f:
            for line in f:
                w, rest = line.rstrip("\n").split("\t")
                n, *lead, strokes = rest.split(" ")
                for op, b, new in contract(lead, strokes, voice):
                    voiced.setdefault((w, n, op, b), new)
    compiled = rs.relation(pipe["caret"])
    with open(pipe["contract"]) as f:
        for line in f:
            w, rest = line.rstrip("\n").split("\t")
            n, op, b = rest.split(" ")
            tape = caret(op, b)
            if f"{n} {tape}" not in compiled[w]:
                sys.exit(f"caret() writes {w} {n} {op} {b} as {tape}, which caret.xfst does not: update caret()")
            split[w].setdefault(_SYSTEM.outline(tape), (op, voiced.get((w, n, op, b), b), n))
    V = lambda o: voice.outline(o, _SYSTEM)

    form = enc.get("form", "caret")
    sides = theory_sides(pipe["theory"]) if form == "position" else {}
    fallback = enc.get("fallback") or []
    fallback = [fallback] if isinstance(fallback, str) else fallback
    if form == "typey" or "typey" in fallback:
        with open(g.SUPERSEDED) as f:
            taken.update((o, ("typey-type", tr)) for o, tr in json.load(f).items())
    tagged = {}
    if form == "typey":
        typey = t.typey_outlines()
        outlines = {w: {o: 0 for o in typey.get(w, [])} for w in outlines}
    else:
        # The pipeline's own fallbacks, whose outlines are tagged here.
        live = r["fallback"]
        for w, cs in outlines.items():
            for o in [o for o in cs if (w, o) in live]:
                del cs[o]
        tagged = t.add_fallbacks(outlines, [(name, t.FALLBACKS[name]()) for name in fallback])
    reach = enc.get("reach", "")
    if isinstance(reach, str):
        chords = [""] + ["".join(sorted(c, key=TAPE_ORDER.index)) for n in range(1, len(reach) + 1)
                         for c in itertools.combinations(reach, n)]
    else:
        chords = [""] + ["".join(sorted(c.replace("-", ""), key=TAPE_ORDER.index)) for c in reach]

    def alt(w, o):
        if form == "typey" or "/" in o:
            return [["=" + s] for s in V(o).split("/")], None
        if form == "caret" and voice.key and o in split[w]:
            op, b, n = split[w][o]
            return [["=" + _SYSTEM.outline(caret(op, b))]], None
        if form in ("operand", "position") and o in split[w]:
            op, b, n = split[w][o]
            prefix, mark, vowels = op.strip("-").partition("^")
            b = caret(prefix or "-", b)
            x = vowels or "^"
            if form == "position":
                x = (sides.get((w, n, vowels)) if vowels else None or stress_side(g.prons(w)[0])) + ":" + x
            return [["=" + _SYSTEM.outline(b)] + (["X:" + x] if mark else [])], b
        return [["=" + V(o)]], None

    def unmarked(w, o):
        """The plain stroke of a two-syllable stroke whose left bank is a skeleton."""
        op, b, _ = split[w].get(o, ("-", "", ""))
        if not enc.get("bare") or not re.fullmatch(r"\^[AOEU]+", op) or not voice.skeleton(b):
            return None
        return _SYSTEM.outline(caret("-", b))

    order = sorted(outlines, key=rank)
    lines, reachable = [], set()
    for n, w in enumerate(order):
        alts = []
        for o, c in outlines[w].items():
            plain = unmarked(w, o)
            if plain:
                alts.append({"cost": c - 0.5, "strokes": [["=" + plain]], "displace": t.syllables(stem(w)) > 1})
                reachable.add(plain)
            strokes, b = alt(w, o)
            if voice.key and b is None:
                reachable.add("/".join(s[0][1:] for s in strokes))
            if b is not None:
                for ch in chords:
                    tape = "".join(sorted(set(b) | set(ch), key=TAPE_ORDER.index))
                    reachable.add(_SYSTEM.outline(tape))
            displace = form != "typey" and t.may_displace(o, outlines[w], stem(w))
            alts.append({"cost": c, "strokes": strokes, "displace": displace})
            if (w, o) in tagged:
                alts[-1]["fallback"] = tagged[w, o]
        lines.append({"w": w, "f": 10 ** (g.zipf(w) - 9), "nsyl": t.syllables(w), "kind": "two-syllable",
                      "rank": n, "strokes": alts[0]["strokes"] if alts else [], "alts": alts})

    if form == "typey":
        reachable = {o for cs in outlines.values() for o in cs}
    reachable |= {V(o) for w, o in tagged}
    with open(g.SUPERSEDED) as f:
        superseded = json.load(f)
    if voice.key:
        # What the stack holds, rewritten; then each novel outline's preimages, asked.
        old, taken = taken, {}
        if voice.fresh:
            old = {o: v for o, v in old.items() if voice.key not in o}
        for o, v in old.items():
            taken.setdefault(V(o), v)
        pre = {o: voice.preimages(o, _SYSTEM) for o in reachable - taken.keys()}
        found = r["stack"].ask(sorted({p for ps in pre.values() for p in ps} - old.keys()))
        for o, ps in pre.items():
            hit = next((found[p][0] for p in ps if found.get(p)), None)
            if hit:
                taken[o] = hit
    else:
        novel = sorted(reachable - taken.keys())
        taken.update((s, found[0]) for s, found in r["stack"].ask(novel).items() if found)
    taken.update((V(s), ("typey-type", tr)) for s, tr in superseded.items() if V(s) not in taken)
    taken.update((V(s), ("retro-words", "")) for s in retro_words() if V(s) not in taken)
    reserved = {V(o) for o in g.Typey.load().reserved}
    out.write(json.dumps({"meta": {"source": "stfst-two-syllable", "units": len(lines), "form": form,
                                   "held": len(taken), "reach": reach}}) + "\n")
    for o, (d, tr) in sorted(taken.items()):
        gives = d == "typey-type" and o not in reserved
        out.write(json.dumps({"held": o, "by": d, "word": tr,
                              "f": 10 ** (g.zipf(tr) - g.DISPLACE - 9) if gives else None}) + "\n")
    for line in lines:
        out.write(json.dumps(line) + "\n")


def main():
    cfg = json.load(sys.stdin)
    words, enc = cfg["words"], cfg.get("encode", {})
    source = words.get("source", "stfst")
    if source == "stfst-two-syllable":
        return two_syllable_units(words, enc, sys.stdout)
    recs = {"handoff": handoff_records, "stfst": stfst_records}[source](words["path"])
    if words.get("within") == "two-syllable":
        # The words stfst's two-syllable dictionary is scored on, at its frequencies.
        within = captured()[0]
        from gen import vowels as g
        recs = [dict(r, f=10 ** (g.zipf(r["w"]) - 9)) for r in recs if r["w"] in within]
    units, onsets = load(recs, words.get("min_f", 1e-6), words.get("max_syl", 4), words.get("function_words", False),
                         words.get("merge_homophones", True))
    scheme = {"foot": Foot, "ireland": Ireland}[enc.get("scheme", "foot")](enc, units, onsets)
    out = sys.stdout
    out.write(json.dumps({"meta": {"source": source, "units": len(units), "lists": scheme.lists()}}) + "\n")
    for i, u in enumerate(units):
        line = {"w": u["w"], "f": u["f"], "nsyl": u["nsyl"], "kind": u["kind"]}
        if isinstance(scheme, Ireland):
            line["strokes"], line["clusters"] = scheme.encode(u)
        else:
            line["strokes"] = scheme.strokes(i, u)
        out.write(json.dumps(line) + "\n")


if __name__ == "__main__":
    main()
