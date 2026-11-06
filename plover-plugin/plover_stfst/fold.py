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


def _based(alts, stroke):
    for alt in alts:
        base = alt.base(stroke.keys())
        if base is not None:
            yield alt, Stroke(sorted(base))


def _reads(translator, stroke):
    """Each alternative with its base, in the order to try them.

    An alternative that writes nothing is the stroke respelled, and the
    respelling's alternatives come right after it.
    """
    out = []
    for alt, base in _based(_alternatives(translator, stroke), stroke):
        out.append((alt, base))
        if not (alt.before or alt.after):
            out.extend(_based(_alternatives(translator, base), base))
    return out


def _fold(translator, max_len, stroke):
    reads = _reads(translator, stroke)
    if not reads:
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
        for alt, base in reads:
            # What goes before the base is a unit of its own, so the base has to
            # begin one.
            if alt.before and prior:
                continue
            mapping = translator._lookup_with_prefix(max_len, translations[:i], prior + [base])
            if mapping is None:
                continue
            # A word the base already writes at that end is not written again.
            words = mapping.split()
            before = "" if [alt.before] == words[:1] else alt.before
            after = "" if [alt.after] == words[-1:] else alt.after
            english = " ".join(x for x in (before, mapping, after) if x)
            t = Translation(prior + [stroke], english)
            t.replaced = replaced
            return t
    return None

