"""Folds in Plover: a stroke nothing matched, read as another stroke with
something written around it. `stfst_core::fold` is the record.

Plover folds its `SUFFIX_KEYS` in the translator, as the last step before a
stroke goes untranslated, and appends what the bare key means. A fold record
says more than that: which keys come off, which go back on, and what goes before
the base as well as after. So the system declares no suffix keys, and that step
is taken over here.

`Translator.translate_stroke` looks for a match of two strokes or more, then the
stroke alone, then calls `_find_longest_match` with `min_len` 1 and the suffix
keys. That third call is the only one with `min_len` 1, so it is where a fold
goes, and its order is Plover's: the longest outline the base can end first,
then each alternative in turn.
"""

from plover.steno import Stroke
from plover.translation import Translation


def install(translator):
    """Fold where the translator would otherwise leave a stroke untranslated.

    Returns what takes it out again.
    """
    find = translator._find_longest_match

    def find_or_fold(min_len, max_len, stroke, suffixes=()):
        found = find(min_len, max_len, stroke, suffixes)
        if found is None and min_len == 1:
            found = _fold(translator, max_len, stroke)
        return found

    translator._find_longest_match = find_or_fold
    return lambda: delattr(translator, "_find_longest_match")


def _alternatives(translator, stroke):
    """Every alternative the enabled dictionaries hold, highest first."""
    out = []
    for d in translator.get_dictionary().dicts:
        if d.enabled and hasattr(d, "folds"):
            out.extend(d.folds(stroke.rtfcre))
    return out


def _fold(translator, max_len, stroke):
    alts = _alternatives(translator, stroke)
    if not alts:
        return None
    # The translations a match may take over, as Plover counts them.
    translations = translator.get_state().translations
    count, strokes = 0, 1
    for t in reversed(translations):
        strokes += len(t)
        if strokes > max_len:
            break
        count += 1
    translations = translations[len(translations) - count:]
    for i in range(len(translations) + 1):
        replaced = translations[i:]
        prior = [s for t in replaced for s in t.strokes]
        for alt in alts:
            # What goes before the base is a unit of its own, so the base has to
            # begin one.
            if alt.before and prior:
                continue
            base = alt.base(stroke.keys())
            if base is None:
                continue
            base = Stroke(sorted(base))
            mapping = translator._lookup_with_prefix(max_len, translations[:i], prior + [base])
            if mapping is None:
                continue
            english = " ".join(x for x in (alt.before, mapping, alt.after) if x)
            t = Translation(prior + [stroke], english)
            t.replaced = replaced
            return t
    return None

