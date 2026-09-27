"""What `gen.stack` reads out of an artifact: Plover's syntax, one form."""
import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

from gen import ROOT
from gen.stack import _SYSTEM, translation
from plover_stfst import ol

WORDS = os.path.join(ROOT, "build", "test-dicts", "words.stfst", "forward.hfstol")


@unittest.skipUnless(os.path.exists(WORDS), "no build/test-dicts")
class AnArtifactsTranslation(unittest.TestCase):
    def ask(self, outline):
        return translation(ol.Automaton.open(WORDS), _SYSTEM.tape(outline))

    def test_a_word_is_itself(self):
        self.assertEqual(self.ask("KAT"), "cat")
        self.assertEqual(self.ask("TPR/KAT"), "tractor")

    def test_an_attach_is_plovers(self):
        # Attached with no orthography, which `{^s}` would ask for.
        self.assertEqual(self.ask("SKWR"), "{^^}s")

    def test_no_entry_is_none(self):
        self.assertIsNone(self.ask("TPR"))
        self.assertIsNone(self.ask("STKPW"))


if __name__ == "__main__":
    unittest.main()
