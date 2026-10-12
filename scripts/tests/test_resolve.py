"""How `gen.resolve` decides who keeps an outline."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

try:
    from gen.resolve import resolve
except ImportError:         # cmudict and wordfreq, which the phrasing venv has
    resolve = None

RANK = {"the": 0, "they": 1, "then": 2}


@unittest.skipIf(resolve is None, "no cmudict or wordfreq")
class Resolution(unittest.TestCase):
    def test_the_first_by_rank_keeps_the_outline(self):
        out, lost, left = resolve({"then": {"TH": 0}, "the": {"TH": 0}, "they": {"THE": 0}}, RANK.get, {})
        self.assertEqual(out, {"TH": "the", "THE": "they"})
        self.assertEqual(lost, [("then", "the", "TH")])
        self.assertEqual(left, [])

    def test_a_second_choice_yields_to_a_first(self):
        taken = {"TH": ("user", "thus")}
        out, _, left = resolve({"the": {"TH": 0, "THE": 1}, "they": {"THE": 0}}, RANK.get, taken)
        self.assertEqual((out, left), ({"THE": "they"}, [("the", "TH", ("user", "thus"))]))

    def test_the_stack_keeps_its_outline_unless_it_gives_way(self):
        taken = {"TH": ("user", "thus")}
        out, _, left = resolve({"the": {"TH": 0}}, RANK.get, taken)
        self.assertEqual((out, left), ({}, [("the", "TH", ("user", "thus"))]))
        out, _, _ = resolve({"the": {"TH": 0}}, RANK.get, taken, lambda o, w, other: other[0] == "user")
        self.assertEqual(out, {"TH": "the"})

    def test_the_stack_writing_the_same_word_is_no_conflict(self):
        out, _, left = resolve({"the": {"TH": 0}}, RANK.get, {"TH": ("typey-type", "the")})
        self.assertEqual((out, left), ({"TH": "the"}, []))


if __name__ == "__main__":
    unittest.main()
