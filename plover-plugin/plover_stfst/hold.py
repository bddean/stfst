"""The stroke that waits: `[AFTER]` in Plover.

A record's tail belongs to the unit after it, so it cannot be written when its
keys arrive. `stfst-board` holds the stroke. Plover's dictionary API has no way
to: a dictionary answers "what does this outline mean", and the answer here
depends on what the *next* stroke means, which another dictionary owns.

So the tail leaves the dictionary inside a meta, `{:stfst_ahead:…}`, which writes
nothing, and an extension writes it when a translation puts a word on screen.

    ^*G   ->  {:stfst_ahead:\\{^ing\\}}    nothing on screen, "ing" waiting
    RUPB  ->  run                         a word, so the tail lands
              running

Two plugins for one mechanism, because a dictionary can only hand the formatter a
meta and only an extension hook sees the next translation.

## Why the marker is read twice

`Translator._listeners` is a *set*, so the extension's hook may run before the
formatter or after it. Nothing here may depend on which: the hook cannot read
`Translation.formatting`, because it may not exist yet, and it cannot let the
meta be what arms the wait, because the meta runs inside the formatter. So the
hook reads the translation's own text for the marker and for whether a word was
written, and the meta exists only to write nothing where the marker sits.

The tail lands as a translation of its own, so Plover's orthography and any meta
inside it read the word from the formatter's history, where a word written by the
same translation would not be. One `*` takes the tail back and a second takes the
word; `stfst-board` undoes both at once, and that is the one place the two hosts
differ.
"""
import re

from plover.formatting import ATOM_RE
from plover.translation import Translation

MARKER = "{:stfst_ahead:"

# Every directive that writes nothing, by the shape of its first character. A
# word is what a held tail waits for, so this only has to be right about whether
# *something* was written.
SILENT = ("#", ":", "*", "$", "-|", ">", "<", "^^")
SILENT_EXACT = ("", "^", "&", "~|")


def _unescape(text):
    """A meta argument as the text it stands for. Plover's own escapes."""
    out, i = [], 0
    while i < len(text):
        if text[i] == "\\" and i + 1 < len(text):
            out.append(text[i + 1])
            i += 2
            continue
        out.append(text[i])
        i += 1
    return "".join(out)


def split_tail(english):
    """A translation as its body and the tail it holds, or `None` for no tail.

    The marker's argument is a translation of its own with its braces escaped, so
    the atom ends at the first brace that is not.
    """
    at = english.find(MARKER)
    if at < 0:
        return english, None
    i = at + len(MARKER)
    arg = []
    while i < len(english):
        if english[i] == "\\" and i + 1 < len(english):
            arg.append(english[i:i + 2])
            i += 2
            continue
        if english[i] == "}":
            i += 1
            break
        arg.append(english[i])
        i += 1
    return english[:at] + english[i:], _unescape("".join(arg))


def writes_text(english):
    """Whether a translation puts anything on screen.

    Not "is it non-empty": a command, a case directive and a held tail all write
    nothing, and a tail waits for a word rather than for a stroke.
    """
    for atom in ATOM_RE.findall(english):
        atom = atom.strip()
        if not atom.startswith("{"):
            if atom:
                return True
            continue
        inner = atom[1:-1] if atom.endswith("}") else atom[1:]
        if inner.startswith("~|"):
            inner = inner[2:]
        bare = inner.strip("^").lstrip("&")
        if bare in SILENT_EXACT or inner in SILENT_EXACT:
            continue
        if inner.startswith(SILENT) or "PLOVER:" in inner or inner.startswith("mode:"):
            continue
        if bare:
            return True
    return False


class _Waiting:
    """The tail this engine is holding, and nothing else: one engine, one hold.

    A second `[AFTER]` before the first has landed replaces it, as it does in
    `stfst-board`: what waits is one record, not a queue.
    """

    def __init__(self):
        self.text = None
        # The translation a tail last landed on, and the tail. What undo needs:
        # taking the word back leaves the tail waiting again, as the mark does in
        # `stfst-board`.
        self.landed = None

    def arm(self, text):
        self.text = text

    def clear(self):
        self.text = None


waiting = _Waiting()


def stfst_ahead(ctx, arg):
    """The meta: write nothing. `Ahead` is what reads the argument."""
    return ctx.copy_last_action()


class Ahead:
    """The extension: land a held tail on the next word."""

    def __init__(self, engine):
        self._engine = engine

    def start(self):
        self._engine.hook_connect("translated", self._translated)

    def stop(self):
        self._engine.hook_disconnect("translated", self._translated)
        waiting.clear()

    def _translated(self, undo, do):
        for translation in undo:
            _, tail = split_tail(translation.english or "")
            if tail is not None:
                # The stroke that held it is gone, so nothing is waiting.
                waiting.clear()
            elif waiting.landed and translation is waiting.landed[0]:
                # The word a tail landed on is gone, so the tail waits again.
                waiting.arm(waiting.landed[1])
                waiting.landed = None
        for translation in do:
            english = translation.english or ""
            body, tail = split_tail(english)
            # In this order, so a stroke that both lands a tail and holds one of
            # its own does the two in the order it wrote them.
            if waiting.text is not None and writes_text(body):
                self._land(translation)
            if tail is not None:
                waiting.arm(tail)

    def _land(self, translation):
        """Write the tail with the word, as one translation.

        One translation and not a second one after it, so that one `*` takes back
        both, as `stfst-board` does, and so that a meta in the tail is read in
        the same pass as the word.

        Two ways to write it, because this hook does not know whether the
        formatter has run: `formatting` is what says. Before it, the translation
        can simply grow its tail and be formatted once. After it, the word is on
        screen already and has to be taken back and written again, which costs a
        flicker and cannot be helped.
        """
        text = waiting.text
        # Cleared before the tail is written, because writing it tells this hook
        # again and there is nothing left to wait for.
        waiting.clear()
        waiting.landed = (translation, text)
        if not translation.formatting:
            translation.english += text
            return
        translator = self._engine.translator
        translator.untranslate_translation(translation)
        landed = Translation(translation.strokes, translation.english + text)
        waiting.landed = (landed, text)
        translator.translate_translation(landed)
        # This hook runs inside the flush that told it, so what it wrote needs a
        # flush of its own or it waits in the queue until the next stroke.
        translator.flush()
