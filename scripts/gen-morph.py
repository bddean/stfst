#!/usr/bin/env python3
"""Write the two morphology lexicons from the data already installed on this machine.

`morphology/ortho.xfst` is the rules, hand written and reviewable. The rest of
what a host needs to join a suffix is vocabulary, and vocabulary is data:

  words.lexc  Plover's own `american_english_words.txt`, word to frequency. The
              rules generate candidates and the frequency picks between them,
              which is how `discover` avoids doubling and `signal` picks the
              American spelling. Without this the rules alone reach 99.0%.

  infl.lexc   lemminflect's inflection tables, `word ^ mode` to form, for the
              `{:infl:…}` meta. This is the `run` to `ran` half: `plover-
              semantic-suffixes` uses lemminflect and no rule can know that a
              verb is irregular.

Neither is committed. Both are derived from third-party assets that are already
installed, they are large, and a build machine that has Plover has them.

An entry goes into `infl.lexc` only where the lexicon disagrees with Plover's
`add_suffix`, because where they agree the host's orthography path already gives
the right answer. That is a filter and not an approximation: the residual is
counted and printed, and it is zero.

Needs Plover's python, which is where both data sets live:

  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/gen-morph.py build/morph
"""
import os
import sys

# The modes `{:infl:…}` can ask for, and the plain suffix each falls back to.
# `ofz` is absent on purpose: a possessive is `'s`, or `'` after an `s`, and a
# rule that short is host code. The one lexical part of it is thirteen pronouns.
MODES = {"ed": "ed", "ing": "ing", "s": "s"}


def escape(s):
    """lexc's metacharacter set, which is larger than its documentation admits.

    The same rule as `stfst_compile::lexc::escape`, which is the original.

    `%X` is literal for any X, so escaping the whole non-alphanumeric ASCII class
    is both safe and shorter than tracking the set. `0` is lexc's epsilon, so an
    unescaped one silently vanishes; a frequency of 80 is exactly that hazard.
    """
    out = []
    for c in s:
        if c == "0" or (c.isascii() and not c.isalnum()):
            out.append("%")
        out.append(c)
    return "".join(out)


def entries(pairs):
    out = ["LEXICON Root"]
    for left, right in pairs:
        out.append("%s:%s # ;" % (escape(left), escape(right)))
    return "\n".join(out) + "\n"


def verb_form(word, lemma, mode, tables, inflect):
    if mode == "ed":
        if word in tables.BE_PAST:
            return tables.BE_PAST[word]
        if word in tables.MODAL_PAST:
            return tables.MODAL_PAST[word]
        tag = "VBD"
    else:
        tag = {"ing": "VBG", "s": "VBZ"}[mode]
    forms = inflect(lemma, tag)
    return forms[0] if forms else None


def target_form(word, mode, tables, lemmas, inflect):
    """The inflected form, or None where the lexicon has nothing to say.

    `plover-semantic-suffixes`' `target_form` with the tagger taken out. Most of
    it needs no part of speech, because the mode supplies one: `ed` and `ing` are
    verb inflections by construction. Only `s` is genuinely ambiguous, and the
    plugin resolved it by looking at the word before ("she does" against "the
    feet").

    What resolves it here is the inflection itself. Try the noun reading, then the
    verb, and prefer whichever *changes the word*: the plural of the noun
    "abandon" is "abandon", so `-S` there means "abandons". Where neither reading
    changes it, that is the answer and not a failure, because an invariant plural
    is a lexicon fact: `sheep` and `-S` is `sheep`, and only a word list knows it.
    """
    known = lemmas(word) or {}
    order = ["NOUN", "VERB"] if mode == "s" else ["VERB"]
    unchanged = None
    for pos in order:
        if pos not in known:
            continue
        if pos == "NOUN":
            forms = inflect(known["NOUN"][0], "NNS")
            form = forms[0] if forms else None
        else:
            form = verb_form(word, known["VERB"][0], mode, tables, inflect)
        if form is None:
            continue
        if form != word:
            return form
        unchanged = form
    # Known only as an adjective or adverb, or not known at all: the rules.
    return unchanged


def main(out_dir):
    import plover.system as system
    from plover.registry import registry
    from plover.orthography import add_suffix
    import plover_semantic_suffixes as tables
    from lemminflect import getAllLemmas, getInflection

    registry.update()
    system.setup("English Stenotype")
    os.makedirs(out_dir, exist_ok=True)

    freq = system.ORTHOGRAPHY_WORDS
    with open(os.path.join(out_dir, "words.lexc"), "w") as f:
        f.write(entries((w, str(n)) for w, n in freq.items()))
    print("words.lexc: %d words" % len(freq))

    # The vocabulary a steno writer actually produces, which is what bounds this.
    # Lowercase only: a host lowercases the pair and re-cases the answer, so an
    # entry per capitalisation would be the same relation written many times.
    vocabulary = sorted({w.lower() for w in freq})
    pairs, agreed = [], 0
    # `ofz` is a rule with thirteen exceptions: an apostrophe after a final `s` and
    # `'s` otherwise, except that the possessive of "I" is "my". The rule is host
    # code and the exceptions are vocabulary, so they go here with the rest.
    for word, form in tables.POSS_PRON.items():
        if form != (word + "'" if word.endswith("s") else word + "'s"):
            pairs.append(("%s^z" % word, form))
    for word in vocabulary:
        for mode, suffix in MODES.items():
            form = target_form(word, mode, tables, getAllLemmas, getInflection)
            # lemminflect answers a few compounds with two words ("clubfoot" ->
            # "club feet"). A suffix joins inside one word, so those are not forms
            # this can use.
            if form is None or " " in form:
                continue
            if form == add_suffix(word, suffix):
                agreed += 1
                continue
            pairs.append(("%s^%s" % (word, mode), form))
    with open(os.path.join(out_dir, "infl.lexc"), "w") as f:
        f.write(entries(pairs))
    print("infl.lexc: %d entries the rules get wrong, %d they already get right"
          % (len(pairs), agreed))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "build/morph"))
