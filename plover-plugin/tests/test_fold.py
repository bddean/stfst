"""A fold record's alternatives, as the Plover host reads them.

Run: python3 -m unittest discover plover-plugin/tests
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from plover_stfst.record import Unsupported, parse_fold

NAMES = {"S": "S-", "T": "T-", "K": "K-", "P": "P-", "H": "H-", "A": "A-",
         "d": "-D", "t": "-T"}


def tape(printed):
    """A printed record as symbols: a tag is one, a character is one."""
    out, i = [], 0
    while i < len(printed):
        j = printed.find("]", i) + 1 if printed[i] == "[" else i + 1
        out.append(printed[i:j])
        i = j
    return out


class FoldTest(unittest.TestCase):
    def test_a_suffix_writes_after_and_nothing_before(self):
        [alt] = parse_fold(tape("[FOLD][STRIP]t[END]the"), NAMES)
        # Nothing before is no text, not `{}`: a base that extends an outline
        # already written can only take an alternative with nothing before it.
        self.assertEqual("", alt.before)
        self.assertEqual("the", alt.after)
        self.assertEqual({"K-", "-D"}, alt.base({"K-", "-D", "-T"}))
        self.assertIsNone(alt.base({"-T"}))
        self.assertIsNone(alt.base({"K-"}))

    def test_alternatives_keep_their_order_and_can_put_a_key_back(self):
        alts = parse_fold(tape("[FOLD]and[STRIP]SKP[ADD]T[END][FOLD]and[STRIP]SKP[END]"), NAMES)
        stroke = {"S-", "K-", "P-", "H-", "A-"}
        self.assertEqual([{"T-", "H-", "A-"}, {"H-", "A-"}], [a.base(stroke) for a in alts])
        self.assertEqual(["and", "and"], [a.before for a in alts])

    def test_an_inflection_after_is_a_meta(self):
        [alt] = parse_fold(tape("[FOLD][STRIP]d[END][META=infl]ed[END]"), NAMES)
        self.assertEqual("{:infl:ed}", alt.after)

    def test_an_ordinary_record_is_not_a_fold(self):
        with self.assertRaises(Unsupported):
            parse_fold(tape("the"), NAMES)


if __name__ == "__main__":
    unittest.main()
