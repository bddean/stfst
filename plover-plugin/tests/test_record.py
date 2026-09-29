"""The record language against Plover's translation language, both ways.

Every case here is a pair: a Plover translation and the record it means. The
translation direction is checked back, because a spelling that does not read as
the record it came from is a spelling of something else.

Run: python3 -m unittest discover plover-plugin/tests
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from plover_stfst.record import Record, Unsupported

# (translation, record). The record is the printed tape form.
PAIRS = [
    ("cat", "cat"),
    ("{^ing}", "ing[ORTHO_L]"),
    ("{^}", "[ATTACH_L][ATTACH_R]"),
    ("{^-^}", "-[ATTACH_L][ATTACH_R]"),
    ("{&a}", "a[GLUE]"),
    ("{-|}", "[CAP_NEXT]"),
    ("{>}", "[LOWER_NEXT]"),
    ("{<}", "[UPPER_NEXT]"),
    ("{$}", "[WORD_END]"),
    ("{~|}", "[CARRY_CAP]"),
    ("{*!}", "[CANCEL_SPACE]"),
    ("{*?}", "[RETRO_SPACE]"),
    ("{*<}", "[META=retro.upper][END]"),
    ("{*(£c)}", "[META=retro.currency]£c[END]"),
    ("{:infl:ed}", "[META=infl]ed[END]"),
    ("{mode:CAPS}", "[CMD=mode.caps][END]"),
    ("{mode:SET_SPACE:_}", "[CMD=mode.set_space]_[END]"),
    ("{PLOVER:TOGGLE}", "[CMD=plover.toggle][END]"),
    ("{#control(Left)}", "[KEY]Left[CTRL][END]"),
    ("{*+}", "[CMD=engine.repeat_stroke][END]"),
    ("{}", ""),
    ("{^~|'^}", "'[ATTACH_L][ATTACH_R][CARRY_CAP]"),
    ("{=(?i)[aeiou]/an/a}", "[IF_NEXT](?i)[aeiou][THEN]an[ELSE]a[END]"),
    ("such {=x/an/a}", "such [IF_NEXT]x[THEN]an[ELSE]a[END]"),
    (r"{=a\/b/X/Y\\Z}", r"[IF_NEXT]a/b[THEN]X[ELSE]Y\Z[END]"),
]

# Translations that read as a record which spells back as something else. Each is
# a place where Plover says a thing two ways and this module picks one.
SPELLED = [
    # A pending capital is for the next word, and the word can be in this record,
    # so the flag comes after the body rather than before it.
    ("{.}", ".[ATTACH_L][CAP_NEXT]", "{^^}.{-|}"),
    ("{,}and", ", and[ATTACH_L]", "{^^}, and"),
    # A tape merges the runs an atom boundary once separated. Orthography joins a
    # suffix and a suffix is letters, so the split is recovered there.
    ("{^ed}{?}", "ed?[ORTHO_L][CAP_NEXT]", "{^ed}{^?}{-|}"),
    ("{:case:cap_first_word}", "[CAP_NEXT]", "{-|}"),
    ("{:attach:}", "[ATTACH_L][ATTACH_R]", "{^}"),
    ("{:glue:x}", "x[GLUE]", "{&x}"),
    ("{:stop:.}", ".[ATTACH_L][CAP_NEXT]", "{^^}.{-|}"),
    # `\/` is a slash in any field, and the else field is the rest.
    (r"{=a/X/Y/Z}", "[IF_NEXT]a[THEN]X[ELSE]Y/Z[END]", r"{=a/X/Y\/Z}"),
    ("{^^}", "[ATTACH_L][ATTACH_R]", "{^}"),
    ("{#Alt_L(Tab)}", "[KEY]Tab[ALT][END]", "{#alt(Tab)}"),
    ("{#a b}", "[KEY]a[END][KEY]b[END]", "{#a}{#b}"),
]


class ReadsPloverTranslations(unittest.TestCase):
    def test_each_pair(self):
        for translation, want in PAIRS + [(t, r) for t, r, _ in SPELLED]:
            with self.subTest(translation):
                self.assertEqual(str(Record.from_translation(translation)), want)

    def test_a_brace_is_a_glyph_when_it_is_escaped(self):
        self.assertEqual(str(Record.from_translation(r"\{")), "{")
        self.assertEqual(str(Record.from_translation(r"{^\}^}")),
                         "}[ATTACH_L][ATTACH_R]")

    def test_a_directive_naming_no_operation_is_refused(self):
        # Plover writes nothing at all for one, and guessing either way would be
        # this module guessing.
        for translation in ("{pgh}", "{--}", "{...}", "{", r"{=\d/X/Y}", "{=x//y}"):
            with self.subTest(translation):
                self.assertRaises(Unsupported, Record.from_translation, translation)

    def test_a_macro_is_written_and_not_read(self):
        # `=undo` is a macro, and Plover resolves one before the formatter sees
        # it. Nothing reads one back, because a dictionary holding `=` holds an
        # equals sign: `KW*L` in typey-type is the character.
        self.assertEqual(str(Record.from_translation("=undo")), "=undo")

    def test_flags_the_caller_knows_fold_in(self):
        got = Record.from_translation("!", ["[ATTACH_L]"])
        self.assertEqual(str(got), "![ATTACH_L]")

    def test_a_case_from_the_caller_beats_one_from_the_translation(self):
        # The caller's flags come from keys the stroke spells after the glyph, and
        # Plover spends the last case a translation names.
        got = Record.from_translation("{<}", ["[CAP_NEXT]"])
        self.assertEqual(str(got), "[CAP_NEXT]")

    def test_an_empty_atom_closes_the_gap_a_caret_opened(self):
        # `{}` is Plover's empty text atom: it writes nothing and ends the join,
        # where a keystroke beside it leaves the join as it found it.
        self.assertEqual(str(Record.from_translation("{^} {#Tab} {}")),
                         "[KEY]Tab[END][ATTACH_L]")
        self.assertEqual(str(Record.from_translation("{^} {#Tab} {^}")),
                         "[KEY]Tab[END][ATTACH_L][ATTACH_R]")


class SpellsRecords(unittest.TestCase):
    def test_each_pair_spells_itself(self):
        for translation, _ in PAIRS:
            with self.subTest(translation):
                got = Record.from_translation(translation)
                self.assertEqual(got.translation(), translation)

    def test_the_other_spelling_reads_back(self):
        for translation, record, spelled in SPELLED:
            with self.subTest(translation):
                got = Record.from_translation(translation)
                self.assertEqual(got.translation(), spelled)
                self.assertEqual(str(Record.from_translation(spelled)), record)

    def test_a_leading_gap_is_spelled_as_a_join_no_rule_can_fire_on(self):
        # A bare text atom loses its edge whitespace, and the bracketed form is an
        # orthographic join. Orthography joins a suffix and a suffix is letters,
        # so over a space the two say the same thing, and only this one keeps the
        # space.
        got = Record.parse([" ", "<", "[ATTACH_L]"])
        self.assertEqual(got.translation(), "{^ <}")

    def test_a_repetition_is_written_out(self):
        # An FST cannot copy an unbounded string and Plover has no repetition, so
        # the one place both can say it is the text itself.
        self.assertEqual(Record.parse(list("ab") + ["[REP3]"]).translation(), "ababab")

    def test_what_plover_cannot_hold_is_refused(self):
        for tape in (["a", "[REPLACE1]"], ["[LETTER]"], ["[DROP]"],
                     ["a", "[STATE]", "x", "[END]"]):
            with self.subTest(tape):
                self.assertRaises(Unsupported, Record.parse(tape).translation)

    def test_a_bound_word_is_a_meta(self):
        # `plover_stfst.host` is what knows the word, because a lookup is not
        # told it and a meta reads the text the engine has.
        self.assertEqual(Record.parse(list("and ") + ["[COPY1]"]).translation(),
                         "and {:stfst_copy:1}")

    def test_a_window_record_is_rewritten_and_not_spelled(self):
        # A class glyph stands for a character of the span, so it has no Plover
        # translation at all: `rewrite` is the direction that reads one.
        record = Record.parse(list("(") + ["[UPPER]", "[LETTER]"] + list(")"))
        self.assertRaises(Unsupported, record.translation)
        self.assertEqual(record.rewrite(["c", "at"[0]]), "(Ca)")


class TheTape(unittest.TestCase):
    def test_a_record_survives_it(self):
        for translation, _ in PAIRS:
            with self.subTest(translation):
                got = Record.from_translation(translation)
                self.assertEqual(Record.parse(got.symbols()), got)

    def test_a_control_character_is_named_by_its_code_point(self):
        got = Record.from_translation("a\nb")
        self.assertEqual(got.symbols(), ["a", "[U+]", "0", "A", "[END]", "b"])
        self.assertEqual(Record.parse(got.symbols()).body, [("text", "a\nb")])


if __name__ == "__main__":
    unittest.main()
