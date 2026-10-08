# Repeat the previous WORD after a folded connective. A connective's ordinary
# brief folded onto the ^ key means "write this connective, then echo the last
# word": the X-conn-X reduplication (back to back, side by side, more and more)
# that steno otherwise has to spell twice.
#
# Repeats the word, not the stroke: `{*+}` re-fires the last outline, so a brief
# or a fingerspelled run comes back as itself. This reads the finished output
# and copies the last word however it was produced.
#
# Mechanism mirrors retro-words.py: a plain lookup never sees prior output, so
# the echo lives in a *meta* ({:repeat_word:<conn>}) handed the formatting
# context; this one file both registers the meta at import and binds the stems
# to it, so each stem is `*`-undoable and reverse-lookable.
#
# ── Stems: ^ folded onto each connective's dictionary brief(s) ────────────────
# Multiple briefs give multiple stems (to = O and TO -> ^O, ^TO). Written forms
# are canonical for Stenotype Extended, where ^ sorts first. To add a stem, fold
# ^ onto any brief the word already has; to add a connective, do the same for a
# new word. The connective set is the one the corpus shows heavy X-conn-X
# reduplication for (scripts/mine-repeat-trigrams.py): to/by/in/on/over/and lead
# by mass. after/upon/against are left out on purpose: their briefs start with
# A, so the fold lands in retro-words.py's ^A namespace.
#
#   and  ^SKP        of   ^-F ^*F        in   ^TPH ^EUPB     over ^OEFR
#   or   ^OR         by   ^PWEU          on   ^OB ^OPB
#   to   ^O ^TO      the  ^-T            for  ^TP-R ^TPOR
#
# repeat-idioms.json sits above this and overrides only the trigrams whose third
# word is NOT the first (IN/^SKP -> in and out, TH/^OR -> this or that); a form
# that could read as either repeat or phrase (by the by vs by the way) is left
# to the repeat here, for consistency.

LONGEST_KEY = 1

_META = "repeat_word"

# connective -> stems (^ folded onto its dictionary briefs).
STEMS = {
    "and": ["^SKP"],
    "or": ["^OR"],
    "to": ["^O", "^TO"],
    "the": ["^-T"],
    "of": ["^-F", "^*F"],
    "by": ["^PWEU"],
    "for": ["^TP-R", "^TPOR"],
    "in": ["^TPH", "^EUPB"],
    "on": ["^OB", "^OPB"],
    "over": ["^OEFR"],
    "after": ["^AF"],
}


def meta_repeat_word(ctx, arg):
    action = ctx.copy_last_action()
    words = ctx.last_words(count=1, strip=True)
    last = words[0] if words else ""
    action.prev_attach = False
    action.text = f"{arg} {last}" if last else arg
    action.word = last or arg
    return action


try:
    from plover.registry import registry
    registry.register_plugin("meta", _META, meta_repeat_word)
except ImportError:
    pass  # running outside Plover (tests import meta_repeat_word directly)


_TRANSLATIONS = {stem: "{:%s:%s}" % (_META, conn)
                 for conn, stems in STEMS.items() for stem in stems}


def lookup(key):
    if len(key) != 1:
        raise KeyError
    return _TRANSLATIONS[key[0]]


def reverse_lookup(text):
    return [(s,) for s, v in _TRANSLATIONS.items() if v == text]


# A non-canonical key never matches a machine stroke; fail loudly at load.
try:
    from plover.steno import normalize_steno
    for _k in _TRANSLATIONS:
        try:
            _norm = normalize_steno(_k)
        except ValueError:
            continue  # ^ not strokable in the active system (non-Extended)
        if _norm != (_k,):
            raise ValueError(f"non-canonical steno key: {_k!r} -> {_norm}")
except (ImportError, AttributeError):
    pass  # no system loaded yet (import outside Plover); skip the check
