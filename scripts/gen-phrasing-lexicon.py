#!/usr/bin/env python3
"""Transcribe the phrasing verb lexicon into xfst.

`~/.config/plover/phrasing_table.json` is mined data with an owner: the pipeline
in `~/.config/plover/phrasing/` scores a verb set, assigns it to chords, and
`gen_dict.py` writes the table. This turns that table into the finite maps the
grammar reads.

- The **right bank**: 658 chords naming a verb, a satellite and a tense, and 8
  naming an adverb. Split by tense, so the grammar says the tense before it
  reads the chord and the record keeps one order.
- The **conjugation**, five forms of 107 verbs. English inflection is a rule and
  `morphology/` has it, but this verb set is closed and its irregulars are the
  common ones, so the table is smaller than the composition would be.

The nominal sublanguage is not here. It is a design rather than mined data, so it
is hand-written in `dictionaries/phrasing-subjects.xfst`.

Usage: scripts/gen-phrasing-lexicon.py > dictionaries/phrasing-lexicon.xfst
"""
import json
import os
import sys

FORMS = ("base", "3sg", "past", "prespart", "pastpart")
# `@` names the empty verb slot `do` (§5), and that is the one verb no chord
# reaches, so the table has no row for it.
DO = {"base": "do", "3sg": "does", "past": "did",
      "prespart": "doing", "pastpart": "done"}

# ---- the hole
#
# `-RG` says the verb of this clause is the word you write next, so no chord
# there can name a verb. The reservation lives here and not in the mined table,
# because the pipeline that writes that table rewrites it.
#
# The hole is a lemma like any other, which is what keeps it out of every rule:
# `Chain` picks the form the corner needs, and the form relation maps the hole to
# the tail that asks the host for it. So agreement, aspect, modality, the
# passive, the question and do-support all keep working with nothing added.
HOLE = "_"
HOLE_SYM = '"[V:%s]"' % HOLE
HOLE_CHORDS = {"RG": "pres", "RGD": "past"}
# "hear" moves out of the way and its satellite form moves further. The family
# stays together and the least common of the three pays: bare "hear" costs a key
# and "hear about" costs two. `-RGTS` is not free for it, adverb or not: that is
# "still".
MOVES = {"RG": "RGT", "RGD": "RGTD",
         "RGT": "RPGT", "RGTD": "RPGTD", "RGTZ": "RPGTZ"}
# What the deferred word is asked for, per form the corner chose. A base form
# asks nothing: the word as written *is* the base form, so "it can often run" is
# a hole with no tail at all.
HOLE_TAIL = {"base": None, "3sg": "s", "past": "ed",
             "prespart": "ing", "pastpart": "en"}


def reassign(table):
    """The mined table with the hole reserved and what it displaced moved.

    Every destination is checked against the adverbs as well as the verbs, which
    is not pedantry: `-RGTS` looks free in the verb table and is "still".
    """
    out = {}
    for chord, row in sorted(table["rhs"].items()):
        moved = MOVES.get(chord, chord)
        if moved in out or moved in table["adv"]:
            raise SystemExit("-%s is taken, so %s cannot move there"
                             % (moved, row[0]))
        out[moved] = row
    for chord, tense in HOLE_CHORDS.items():
        if chord in out or chord in table["adv"]:
            raise SystemExit("the hole chord -%s is not free: give it a move"
                             % chord)
        out[chord] = [HOLE, 0, tense]
    return out


def hole_tail(form):
    """The record tail that asks the host to inflect the next unit's word."""
    mode = HOLE_TAIL[form]
    if mode is None:
        return "0"
    return '[ "[AFTER]" "[META=infl]" {%s} "[END]" "[END]" ]' % mode


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


ORDER = "FRPBLGTSDZ"


def canon(chord):
    """A key set in steno order, which is the only order a chord is written in."""
    return "".join(k for k in ORDER if k in chord)


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


def verb_rows(table):
    """The right bank: one row per chord, split by the tense it names.

    A satellite sits on the side of the verb it surfaces on, so the record needs
    no rule to place it: a pre-satellite is written before the lemma and a
    post-satellite after.
    """
    rows = {"pres": [], "past": []}
    sats = {"PRE": set(), "POST": set()}
    lemmas = {"do"}
    for chord, (lemma, sat, tense) in sorted(reassign(table).items()):
        lemmas.add(lemma)
        out = ['"[V:%s]"' % lemma]
        if sat:
            s = table["verbs"][lemma]["satellites"][sat - 1]
            pos = s["pos"].upper()
            tag = '"[%s:%s]"' % (pos, s["word"])
            sats[pos].add((tag, s["word"]))
            out.insert(0 if pos == "PRE" else 1, tag)
        rows[tense].append((right(chord), " ".join(out)))
    return rows, {k: sorted(v) for k, v in sats.items()}, sorted(lemmas)


def verbword(table, lemma, form):
    return DO[form] if lemma == "do" else table["verbs"][lemma]["conj"][form]


def main(path):
    table = json.load(open(path))
    out = ["! Generated by scripts/gen-phrasing-lexicon.py from",
           "! ~/.config/plover/phrasing_table.json. Edit the mining pipeline that",
           "! writes that table, not this. The grammar over these maps is in",
           "! phrasing.xfst, and the subject map is in phrasing-subjects.xfst.",
           ""]

    rows, sats, lemmas = verb_rows(table)
    out.append(alphabet("Lemma", ['"[V:%s]"' % v for v in lemmas],
                        "! The verb set.\n"))
    out.append("! The hole, named so the grammar need not spell it.\n"
               "define Hole %s ;\n" % HOLE_SYM)
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

    advs = sorted(table["adv"].items())
    out.append(alphabet("Adv", ['"[ADV:%s]"' % w for _, w in advs],
                        "\n! Adverb enders (§4.6). Each one carries the hole: an\n"
                        "! adverb inflects for nothing, so a clause that ends on one\n"
                        "! has no verb to inflect either, and the word written next\n"
                        "! is that verb. `-d` is the tense it always was.\n"))
    for tense, keys in (("Pres", lambda c: c), ("Past", lambda c: canon(c + "D"))):
        out.append(relation(
            "Adv" + tense,
            [(right(keys(c)), '"[ADV:%s]" %s' % (w, HOLE_SYM)) for c, w in advs]))
    out.append(relation("AdvW",
                        [('"[ADV:%s]"' % w, literal(" " + w)) for _, w in advs]))

    for form in FORMS:
        out.append(relation(
            "V" + form.capitalize(),
            [('"[V:%s]"' % v,
              hole_tail(form) if v == HOLE
              else literal(" " + verbword(table, v, form)))
             for v in lemmas],
            "\n! The %s form of each verb.\n" % form))
    print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1
         else os.path.expanduser("~/.config/plover/phrasing_table.json"))
