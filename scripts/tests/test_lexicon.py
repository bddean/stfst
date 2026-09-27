"""How `gen.lexicon` aligns a pronunciation to a spelling."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

try:
    from gen.lexicon import align
except ImportError:         # cmudict and wordfreq, which the phrasing venv has
    align = None


@unittest.skipIf(align is None, "no cmudict or wordfreq")
class Alignment(unittest.TestCase):
    def aligned(self, word, pron):
        entry = align(word, pron.split())
        return entry and str(entry)

    def test_a_segment_is_its_sounds_then_its_letters(self):
        self.assertEqual(self.aligned("report", "R IH0 P AO1 R T"), "1 R=r IH0=e P=p AO1=o R.T=rt")
        self.assertEqual(self.aligned("at", "AE1 T"), "1 = AE1=a T=t")

    def test_two_vowels_in_one_run_split(self):
        self.assertEqual(self.aligned("react", "R IY0 AE1 K T"), "1 R=r IY0=e = AE1=a K.T=ct")
        # With letters between, they are two runs already.
        self.assertEqual(self.aligned("towing", "T OW1 IH0 NG"), "1 T=t OW1=o =w IH0=i NG=ng")
        # An r-coloured vowel has its r between.
        self.assertEqual(self.aligned("around", "ER0 AW1 N D"), "1 = ER0=a =r AW1=ou N.D=nd")

    def test_a_final_e_is_silent_where_the_vowels_want(self):
        self.assertEqual(self.aligned("create", "K R IY0 EY1 T"), "1 K.R=cr IY0=e = EY1=a T=t_e")
        self.assertEqual(self.aligned("fire", "F AY1 R"), "1 F=f AY1=i R=r_e")
        self.assertEqual(self.aligned("acre", "EY1 K ER0"), "1 = EY1=a K=cr ER0=e =")
        # -le is a syllable where the vowels allow it.
        self.assertEqual(self.aligned("aisle", "AY1 AH0 L"), "1 = AY1=ai =sl AH0=e L=")
        self.assertEqual(self.aligned("agile", "AE1 JH AH0 L"), "1 = AE1=a JH=g AH0=i L=l_e")

    def test_aye_is_spelled_ay(self):
        self.assertEqual(self.aligned("delayed", "D IH0 L EY1 D"), "1 D=d IH0=e L=l EY1=ay_e D=d")

    def test_vowels_that_do_not_line_up_are_no_entry(self):
        self.assertIsNone(self.aligned("every", "EH1 V R IY0"))


if __name__ == "__main__":
    unittest.main()
