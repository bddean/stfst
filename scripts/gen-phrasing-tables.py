#!/usr/bin/env python3
"""Transcribe the phrasing *data* from `phrasing.py` into xfst.

Only what is a finite map by nature lives here. The paradigm that reads these
tables -- the auxiliary chain, do-support, agreement, the contraction modes --
is hand-written in `phrasing.xfst`, because it is a rule.

- The **subject map**, one row per left-key set. `PHRASING-DESIGN.md` §4.1 gives
  it as a sublanguage with growth, coordination, politeness order and a type
  shift, but every one of those is a finite map over subsets of five keys, so it
  precomputes away, which is what `FST-DICT-DESIGN.md` §7 item 1 says.
- The **right bank**: 658 chords naming a verb, a satellite and a tense, and 8
  naming an adverb. Split by tense, so the grammar says the tense before it
  reads the chord and the record keeps one order.
- The **conjugation**, five forms of 107 verbs. English inflection is a rule and
  `morphology/` has it, but this verb set is closed and its irregulars are the
  common ones, so the table is smaller than the composition would be.

Usage: scripts/gen-phrasing-tables.py > dictionaries/phrasing-tables.xfst
"""
import contextlib
import importlib.util
import io
import itertools
import os
import sys

LEFT = "STKPWHR"
AGR = {"1sg": '"[1sg]"', "3sg": '"[3sg]"', "pl": '"[pl]"'}
FORMS = ("base", "3sg", "past", "prespart", "pastpart")


def load(path):
    spec = importlib.util.spec_from_file_location("phrasing", path)
    module = importlib.util.module_from_spec(spec)
    with contextlib.redirect_stdout(io.StringIO()):
        spec.loader.exec_module(module)
    return module


def literal(text):
    """A text as xfst symbols.

    `%` is not an escape inside `{...}`, so `{isn%'t}` is six symbols with a
    literal `%` among them. Alphanumeric runs go in braces and every other
    character is its own quoted symbol.
    """
    if text == "":
        return "0"
    out, run = [], ""
    for c in text:
        if c.isalnum():
            run += c
            continue
        if run:
            out.append("{" + run + "}")
            run = ""
        out.append('"' + c + '"' if c not in '"\\' else "%" + c)
    if run:
        out.append("{" + run + "}")
    return "[ " + " ".join(out) + " ]" if len(out) > 1 else out[0]


def keys(left):
    """A left-key set as its tape symbols, in steno order."""
    return " ".join('"%s"' % k for k in LEFT if k in left) or "0"


def right(chord):
    """A right-bank chord as its tape symbols, which are lower case."""
    return " ".join('"%s"' % k.lower() for k in chord)


def relation(name, rows, comment=""):
    body = " |\n    ".join("[ [ %s ] .x. [ %s ] ]" % (i, o) for i, o in rows)
    return "%sdefine %s [\n    %s ] ;\n" % (comment, name, body)


def alphabet(name, symbols, comment=""):
    body = " |\n    ".join(" | ".join(symbols[i:i + 6])
                           for i in range(0, len(symbols), 6))
    return "%sdefine %s [\n    %s ] ;\n" % (comment, name, body)


def nominal(ph, subj):
    """A subject bundle as record symbols.

    A set with no referent and no `W` still heads a clause when the right bank
    names a verb ("+EB" is "are adding"), so it has agreement and no subject
    word. `phrasing.xfst` is what refuses it an empty verb slot.
    """
    if subj is None:
        return '"[pl]"'
    # A clausal "and" (§4.1a) precedes the whole clause, so it stays a marker of
    # its own rather than folding into the subject word: an inverted question is
    # "and am I", not "am and I".
    conj = '"[AND]" ' if subj.get("conj_and") else ""
    agr = AGR[ph._agr(subj["person"], subj["number"])]
    if subj["kind"] == "inf":
        return conj + '"[INF]" ' + agr
    word = subj["word"]
    clitic = ' "[C]"' if word in ph._CONTRACTIBLE_SUBJ else ""
    return conj + '"[SUBJ]" %s "[/SUBJ]"%s %s' % (literal(word), clitic, agr)


def subject_rows(ph):
    """The nominal sublanguage (§4.1), one row per left-key set."""
    named, bare = [], []
    for n in range(len(LEFT) + 1):
        for combo in itertools.combinations(LEFT, n):
            s = ph._subject(set(combo))
            (bare if s is None else named).append((keys(set(combo)),
                                                   nominal(ph, s)))
    return named, bare


def verb_rows(ph):
    """The right bank: one row per chord, split by the tense it names.

    A satellite sits on the side of the verb it surfaces on, so the record needs
    no rule to place it: a pre-satellite is written before the lemma and a
    post-satellite after.
    """
    rows = {"pres": [], "past": []}
    sats = {"PRE": set(), "POST": set()}
    lemmas = {"do"}                    # `@` reaches `do`, which has no chord (§5)
    for chord, (lemma, sat, tense) in sorted(ph.RHS.items()):
        lemmas.add(lemma)
        out = ['"[V:%s]"' % lemma]
        if sat:
            s = ph.VERBS[lemma]["satellites"][sat - 1]
            pos = s["pos"].upper()
            tag = '"[%s:%s]"' % (pos, s["word"])
            sats[pos].add((tag, s["word"]))
            out.insert(0 if pos == "PRE" else 1, tag)
        rows[tense].append((right(chord), " ".join(out)))
    return rows, {k: sorted(v) for k, v in sats.items()}, sorted(lemmas)


def vocabulary(ph):
    """Every word a clause can be spelled with.

    The retro layer reads a clause back off the window, and the window carries a
    character as its *class* unless the grammar names it. So these are the
    characters the grammar has to name, and they are derived from the words rather
    than declared: a verb with a new letter in it cannot forget to make that letter
    visible.
    """
    words = {"and", "to", "not", "one", "there"}
    for table in (ph._BE, ph._BE_NEG, ph._HAVE, ph._HAVE_NEG, ph._DO, ph._DO_NEG):
        for tense in table.values():
            words.update(tense.values())
    for table in (ph._MODAL, ph._MODAL_NEG):
        for pair in table.values():
            words.update(pair)
    words.update(ph._AUX_SUFFIX.values())
    words.update(ph._CONTRACTIBLE_SUBJ)
    for word, plural, _ in ph._REFERENT.values():
        words.update((word, plural))
    for sg, grown, _ in ph._TYPE.values():
        words.update((sg, grown))
    words.update(ph.ADV.values())
    for entry in ph.VERBS.values():
        words.update(entry["conj"].values())
        words.update(s["word"] for s in entry["satellites"])
    words.update(("been", "being", "be", "could've", "should've", "would've"))
    return words


def main(path):
    ph = load(path)
    out = ["! Generated by scripts/gen-phrasing-tables.py from phrasing.py and",
           "! phrasing_table.json. Edit those, not this. Only finite maps live",
           "! here; the paradigm that reads them is in phrasing.xfst.", ""]

    named, bare = subject_rows(ph)
    out.append(relation(
        "Subjects", named,
        "! Left keys to a subject and its agreement: %d of the %d sets name one.\n"
        "! The other %d head a clause only when a verb follows.\n"
        % (len(named), 2 ** len(LEFT), len(bare))))
    out.append(relation("NoSubject", bare))

    rows, sats, lemmas = verb_rows(ph)
    out.append(alphabet("Lemma", ['"[V:%s]"' % v for v in lemmas],
                        "\n! The verb set.\n"))
    for pos in ("Pre", "Post"):
        rs = sats[pos.upper()]
        out.append(alphabet("Sat" + pos, [t for t, _ in rs],
                            "\n! The %s-verb satellite words, a menu slot on the\n"
                            "! chord (§4.5).\n" % pos.lower()))
        out.append(relation("Sat%sW" % pos,
                            [(t, literal(" " + w)) for t, w in rs]))
    for tense in ("pres", "past"):
        out.append(relation(
            "Rhs" + tense.capitalize(), rows[tense],
            "\n! The %d %s-tense verb chords.\n" % (len(rows[tense]), tense)))

    advs = sorted(ph.ADV.items())
    out.append(alphabet("Adv", ['"[ADV:%s]"' % w for _, w in advs],
                        "\n! Adverb enders (§4.6): a verb-less clause launchpad.\n"))
    out.append(relation("AdvChords",
                        [(right(c), '"[ADV:%s]"' % w) for c, w in advs]))
    out.append(relation("AdvW",
                        [('"[ADV:%s]"' % w, literal(" " + w)) for _, w in advs]))

    for form in FORMS:
        name = "V" + form.capitalize()
        out.append(relation(
            name, [('"[V:%s]"' % v, literal(" " + ph._verbword(v, form)))
                   for v in lemmas],
            "\n! The %s form of each verb.\n" % form))

    # The window's alphabet. A clause can arrive capitalised, because the
    # formatter may have capitalised the sentence it opened, so both cases of
    # every letter are named and the retro rules fold the case themselves.
    chars = sorted({c for w in vocabulary(ph) for c in w})
    cased = sorted({c for c in chars if c.isalpha()}
                   | {c.upper() for c in chars if c.isalpha()})
    out.append(alphabet("SigChar", ['"[=%s]"' % c for c in chars + [" "]]
                        + ['"[=%s]"' % c for c in cased if c not in chars],
                        "\n! The characters a clause is spelled with, which are the\n"
                        "! ones the window has to carry literally.\n"))
    out.append(relation(
        "Unsig", [('"[=%s]"' % c, literal(c)) for c in chars + [" "]],
        "\n! A window character as itself, so the clause grammar can read it.\n"))
    out.append(relation(
        "Uncap", [('"[=%s]"' % c.upper(), '"[=%s]"' % c)
                  for c in chars if c.isalpha()],
        "\n! And the case fold, for a clause the formatter capitalised. `I` is in\n"
        "! both columns, so \"I\'m\" and a sentence-initial \"It\'s\" both read.\n"))
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1
         else os.path.expanduser("~/.config/plover/phrasing.py"))
