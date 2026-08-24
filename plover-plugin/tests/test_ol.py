"""The `hfstol` reader, on what a file can get wrong and what a symbol table means.

Lookup itself is checked against the Rust reader over tens of thousands of tapes
by `scripts/crosscheck-py.sh`, which is the only evidence either reading of the
format is right. What is here is what a cross-check cannot reach: a file that is
not one, and the corners of the symbol table.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from plover_stfst.ol import Alphabet, Automaton, OlError


def alphabet():
    return Alphabet(["@_EPSILON_SYMBOL_@", "S", "T", "[KEY]", "x"], 4)


class TheSymbolTable(unittest.TestCase):
    def test_longest_match_wins(self):
        self.assertEqual(alphabet().tokenize("ST[KEY]"), [1, 2, 3])

    def test_a_symbol_past_the_input_count_is_not_tokenizable(self):
        self.assertIsNone(alphabet().tokenize("x"), "x is output only")

    def test_epsilon_renders_as_nothing(self):
        self.assertEqual(alphabet().render([1, 0, 2]), "ST")

    def test_a_missing_symbol_is_not_an_empty_answer(self):
        self.assertIsNone(alphabet().numbers(["S", "nope"]))

    def test_flag_diacritics_are_refused(self):
        self.assertRaises(OlError, Alphabet, ["@_EPSILON_SYMBOL_@", "@U.CASE.UP@"], 2)


class TheFile(unittest.TestCase):
    def test_a_truncated_file_is_an_error(self):
        self.assertRaises(OlError, Automaton, b"HFST\0\x02\0")

    def test_a_short_file_is_an_error(self):
        self.assertRaises(OlError, Automaton, b"")


if __name__ == "__main__":
    unittest.main()
