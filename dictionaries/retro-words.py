# Retro word operators: recase, join/split, and wrap already-typed text, by
# word rather than by stroke. Stenotype Extended only (needs the ^ key).
#
# Plover's built-in retro pieces work on strokes or only the single previous
# word (`retro_case`, `retro_delete_space`). These ops instead treat the recent
# output as a list of space-separated words and rewrite a suffix of it, so they
# compose over words that came from any dictionary.
#
# Mechanism. A plain Python dictionary lookup never sees prior output, so the
# rewriting can't live in `lookup`; it lives in a *meta* (`{:retro_word:...}`),
# which Plover hands a formatting context with the output history. This one file
# is both: at import it registers the meta with Plover's registry (registration
# survives `registry.update()`), and `lookup`/`reverse_lookup` decode strokes to
# it. So the whole feature is one dictionary, `*`-undoable and reverse-lookable.
#
# ── Layout (stem ^A: "leading vowel", empty left hand, so ^A… is free) ───────
#
#   CASE dial   F R P B     recase one word
#     -P up / -B down   direction (pick one)
#     -F                whole word ("Full"); absent = first letter only
#     -R                the literal last word; absent = FIND (the last word not
#                       already in that form) — find is the default
#
#   VERB        right vowels
#     -E   join           insert a separator between the last two words
#     -U   split          break the last word into words
#     -EU  wrap           surround the last word with a bracket pair
#
#   MENU        L G T S    a 4-bit index (L=1 G=2 T=4 S=8; D/Z reserved):
#     for join/split -> SEPARATORS[index]   (0 = tight / case boundaries)
#     for wrap       -> PAIRS[index]        (0 = parens)
#
#   Case dial and the join verb compose (case, then join): ^AEP = cap-first +
#   tight join = camelCase. Wrap's find/last (-R) means wrap-or-extend vs wrap
#   the literal last word.
#
# Examples: ^AP Cap last non-cap · ^AFP UPPER · ^AE glue · ^AEL kebab ·
#   ^AEG snake · ^AEP camelCase · ^AU split on case · ^AEU wrap () · ^AEUL wrap []

import re

LONGEST_KEY = 1
WINDOW = 16  # how many words back an op may look

_META = "retro_word"

# Menus, indexed by the L/G/T/S bit value. Reorder / extend freely.
SEPARATORS = ["", "-", "_", ":", "::", "/", ".", "\t", "\n"]
PAIRS = [("(", ")"), ("[", "]"), ("{", "}"), ("<", ">"),
         ('"', '"'), ("'", "'"), ("`", "`"), ("*", "*")]

# --- word ops ---------------------------------------------------------------
# The recent output is a list of cells [text, trailing_ws], oldest->newest;
# carrying each word's own whitespace lets an op preserve exact spacing and
# punctuation attachment. An op returns (start, new_cells) to replace
# cells[start:] with, or None for a no-op.

_ALPHA = re.compile(r"[^\W\d_]")  # first cased letter, Unicode-aware


def _first_alpha(w):
    m = _ALPHA.search(w)
    return m.start() if m else None


def _cap_first(w):
    i = _first_alpha(w)
    return w if i is None else w[:i] + w[i].upper() + w[i + 1:]


def _low_first(w):
    i = _first_alpha(w)
    return w if i is None else w[:i] + w[i].lower() + w[i + 1:]


_TRANSFORM = {
    ("up", "first"): _cap_first,
    ("down", "first"): _low_first,
    ("up", "all"): str.upper,
    ("down", "all"): str.lower,
}


def op_case(work, direction, scope, mode):
    fn = _TRANSFORM[(direction, scope)]
    for i in range(len(work) - 1, -1, -1):
        text = work[i][0]
        if _first_alpha(text) is None:
            continue  # punctuation / numbers: nothing to case
        new = fn(text)
        if new == text:
            if mode == "find":
                continue  # already so; keep walking left
            return None   # last mode: the final word is already so
        return i, [[new, work[i][1]]] + [c[:] for c in work[i + 1:]]
    return None


def op_join(work, sep):
    if len(work) < 2:
        return None
    merged = work[-2][0] + sep + work[-1][0]
    return len(work) - 2, [[merged, work[-1][1]]]


_CAMEL = re.compile(r"[A-Z]+(?![a-z])|[A-Z]?[a-z0-9]+|[0-9]+")


def _split_word(w, sep):
    if sep:
        parts = w.split(sep)
    else:  # index 0: break on case transitions and any non-alphanumeric run
        parts = [p for chunk in re.split(r"[^0-9A-Za-z]+", w)
                 for p in _CAMEL.findall(chunk)]
    return [p for p in parts if p]


def op_split(work, sep):
    if not work:
        return None
    parts = _split_word(work[-1][0], sep)
    if len(parts) < 2:
        return None
    ws = work[-1][1]
    return len(work) - 1, [[p, " "] for p in parts[:-1]] + [[parts[-1], ws]]


def op_wrap(work, pair, mode):
    open_, close = pair
    tgt = next((k for k in range(len(work) - 1, -1, -1)
                if _first_alpha(work[k][0]) is not None), None)
    if tgt is None:
        return None
    start = None  # index of the cell holding the current opening delimiter
    if work[-1][0].endswith(close):
        for j in range(len(work) - 1, -1, -1):
            if open_ in work[j][0]:
                start = j
                break
    if mode == "last" or start is None:
        return tgt, ([[open_ + work[tgt][0] + close, work[tgt][1]]]
                     + [c[:] for c in work[tgt + 1:]])
    if start == 0:
        return None  # already wrapped from the clause start; nothing to extend
    inner = [c[0] for c in work[start:]]
    inner[0] = inner[0].replace(open_, "", 1)
    inner[-1] = "".join(inner[-1].rsplit(close, 1))
    content = " ".join(w for w in [work[start - 1][0]] + inner if w)
    return start - 1, [[open_ + content + close, work[-1][1]]]


def _subop(part):
    kind, *rest = part.split(".")
    if kind == "case":
        d, s, m = rest
        return lambda w: op_case(w, d, s, m)
    if kind == "join":
        sep = SEPARATORS[int(rest[0])]
        return lambda w: op_join(w, sep)
    if kind == "split":
        sep = SEPARATORS[int(rest[0])]
        return lambda w: op_split(w, sep)
    if kind == "wrap":
        pair, mode = PAIRS[int(rest[0])], rest[1]
        return lambda w: op_wrap(w, pair, mode)
    raise ValueError(part)


# --- the meta ---------------------------------------------------------------

def meta_retro_word(ctx, arg):
    action = ctx.copy_last_action()
    action.prev_attach = True
    tokens = ctx.last_words(count=WINDOW)
    cells = [[t.rstrip(), t[len(t.rstrip()):]] for t in tokens]

    work = [c[:] for c in cells]
    start = len(cells)
    for op in (_subop(p) for p in arg.strip().split("+")):
        result = op(work)
        if result is None:
            continue
        at, new = result
        work = work[:at] + new
        start = min(start, at)

    if start >= len(cells):
        action.text = ""  # every sub-op was a no-op
        return action
    action.prev_replace = "".join(tokens[start:])
    action.text = "".join(t + ws for t, ws in work[start:])
    action.word = None
    return action


try:
    from plover.registry import registry
    registry.register_plugin("meta", _META, meta_retro_word)
except ImportError:
    pass  # running outside Plover (tests import the ops directly)


# --- dictionary: strokes -> the meta ----------------------------------------
# Fully compositional, so the entries are generated from the layout rather than
# hand-listed. Right-hand keys, in Stenotype Extended canonical order after ^A:
# E U (verbs), F R P B (case dial), L G T S (menu bits).

def _bits(i):
    return "".join(k for k, v in (("L", 1), ("G", 2), ("T", 4), ("S", 8)) if i & v)


# (key letters in F R P B order, meta fragment) for every case variant.
_CASE = [((("F" if scope == "all" else "") + ("R" if mode == "last" else "")
           + ("P" if d == "up" else "B")), f"case.{d}.{scope}.{mode}")
         for d in ("up", "down")
         for scope in ("first", "all")
         for mode in ("find", "last")]

_ENTRIES = {}
for _keys, _arg in _CASE:                                  # case alone
    _ENTRIES["^A" + _keys] = _arg
for _i in range(len(SEPARATORS)):
    _ENTRIES["^AE" + _bits(_i)] = f"join.{_i}"             # join
    for _keys, _arg in _CASE:                              # case then join
        _ENTRIES["^AE" + _keys + _bits(_i)] = f"{_arg}+join.{_i}"
    _ENTRIES["^AU" + _bits(_i)] = f"split.{_i}"            # split
for _i in range(len(PAIRS)):
    _ENTRIES["^AEU" + _bits(_i)] = f"wrap.{_i}.find"       # wrap-or-extend
    _ENTRIES["^AEUR" + _bits(_i)] = f"wrap.{_i}.last"      # wrap the last word

_TRANSLATIONS = {s: "{:%s:%s}" % (_META, a) for s, a in _ENTRIES.items()}


def lookup(key):
    if len(key) != 1:
        raise KeyError
    return _TRANSLATIONS[key[0]]


def reverse_lookup(text):
    return [(s,) for s, v in _TRANSLATIONS.items() if v == text]


# A non-canonical key never matches a machine stroke; fail loudly at load.
try:
    from plover.steno import normalize_steno
    for _k in _ENTRIES:
        try:
            _norm = normalize_steno(_k)
        except ValueError:
            continue  # not strokable in the active system (^ outside Extended)
        if _norm != (_k,):
            raise ValueError(f"non-canonical steno key: {_k!r} -> {_norm}")
except (ImportError, AttributeError):
    pass  # no system loaded yet (import outside Plover); skip the check
