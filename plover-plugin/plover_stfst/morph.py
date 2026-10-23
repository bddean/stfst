"""Joining a suffix in Plover as `stfst_text::morph` joins it: Plover's own
`add_suffix`, then a completion where that makes no word, so `idea` and `cal` is
`identical` where Plover writes `ideacal`.

Plover's formatter calls `add_suffix` by name from `plover.meta.attach`, so
`install` replaces it there. The word list is Plover's, sorted once, so a
completion is a range of it.
"""
import bisect

import plover.meta.attach as attach
import plover.orthography as orthography
from plover import system

# `stfst_text::morph`'s, and both have to agree.
COMPLETION_GAP = 3
VOWELS = "aeiouy"

_sorted = (None, [])


def _words():
    """Plover's word list, sorted, and sorted again only when a system swaps it."""
    global _sorted
    words = system.ORTHOGRAPHY_WORDS
    if _sorted[0] is not words:
        _sorted = (words, sorted(words))
    return _sorted[1]


def between(prefix, gap, suffix):
    """Every word that is `prefix`, then at most `gap` letters, then `suffix`."""
    words = _words()
    shortest, longest = len(prefix) + len(suffix), len(prefix) + gap + len(suffix)
    out = []
    i = bisect.bisect_left(words, prefix)
    while i < len(words) and words[i].startswith(prefix):
        w = words[i]
        i += 1
        fill = w[len(prefix):len(w) - len(suffix)]
        if shortest <= len(w) <= longest and w.endswith(suffix) and (not fill or fill.isalpha()):
            out.append(w)
    return out


def complete(word, suffix, gap):
    """The words that complete a lowercase join: the word less a final vowel,
    then at most `gap` letters, then the suffix."""
    stem = word[:-1] if word[-1:] in VOWELS else word
    if len(stem) < 2:
        return []
    return between(stem, gap, suffix)


def match_case(joined, word):
    """`joined` with the case of the word it came from, wherever the two agree."""
    out = []
    for n, c in enumerate(joined):
        if n < len(word) and word[n].lower() == c.lower():
            out.append(word[n])
        else:
            out.extend(joined[n:])
            break
    return "".join(out)


def join_or_complete(word, suffix):
    """`Morph::join_or_complete`."""
    words = system.ORTHOGRAPHY_WORDS
    lower, suffix_lower = word.lower(), suffix.lower()
    # A one-letter suffix ends too many words: `child` and `s` would be `childes`.
    if len(suffix_lower) < 2 or orthography._add_suffix(lower, suffix_lower) in words:
        return orthography._add_suffix(word, suffix)
    gap = min(len(suffix_lower), COMPLETION_GAP)
    found = complete(lower, suffix_lower, gap)
    if not found:
        return orthography._add_suffix(word, suffix)
    return match_case(min(found, key=lambda w: (words[w], len(w), w)), word)


def add_suffix(word, suffix):
    """Plover's `add_suffix`, which joins only up to the first space."""
    suffix, sep, rest = suffix.partition(" ")
    return join_or_complete(word, suffix) + sep + rest


_replaced = {}


def install():
    """Join with completion wherever Plover joins. Returns what takes it out."""
    for module in (orthography, attach):
        _replaced.setdefault(module, module.add_suffix)
        module.add_suffix = add_suffix
    return uninstall


def uninstall():
    for module, original in _replaced.items():
        module.add_suffix = original
    _replaced.clear()
