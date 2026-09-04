r"""What a dictionary needs the host to say, where a lookup cannot be told it.

Plover asks a dictionary one question and passes it one argument: the outline. So
four things an artifact can ask for have no way in, and each is a meta the
dictionary answers with instead. Three of them read text the engine has, and the
fourth waits for text nobody has written yet.

| a dictionary asks for | the meta | when it writes |
|---|---|---|
| a bound word, `[COPY1]` | `stfst_copy` | at once: the word is behind the cursor |
| a window of written text, `words = 16` | `stfst_retro` | at once, over the words it rewrote |
| a window of the text after it, `next = 2` | `stfst_hold` then `stfst_frame` | when a unit writes a word |
| a tail, `[AFTER]` | `stfst_hold` then the tail itself | when a unit writes a word |

A window record is not text: its class glyphs stand for the characters of the
span, so `window.py` encodes the span and `Record.rewrite` substitutes them back.
Both windowed kinds take the words back with `prev_replace`, which is how
Plover's own retro metas rewrite what is already written.

## What waits, and why it takes an extension

A tail and a frame belong to the unit *after* their keys, and the answer depends
on what the next stroke means, which another dictionary owns. So the dictionary
answers with `{:stfst_hold:…}`, which writes nothing and carries what the wait
needs, and the extension writes the answer when a translation puts a word on
screen.

    ^*G      ->  {:stfst_hold:tail:\{:infl:ing\}}   nothing, "ing" waiting
    RUPB     ->  run                                a word, so the tail lands
                 running

    STPR*T   ->  {:stfst_hold:frame:0:STPR*T}       nothing, a frame waiting
    HAOER    ->  here                               a word, so the frame reads it
                 from here to

## What a live candidate costs

Whether a windowed entry matches at all is unknown until the window arrives, so
its marker stands for a *live* candidate rather than a match. That makes a
windowed dictionary a fallback, and a fallback in Plover means the bottom of the
dictionary list, because the first dictionary in a collection wins. It also means
such a dictionary claims a stroke that begins one of its entries even where the
rule turns out not to apply: `stfst-board` lets that stroke fall through to a
lower dictionary and this cannot, which is the one place the two hosts differ.

## Why the marker is read twice

`Translator._listeners` is a *set*, so the extension's hook may run before the
formatter or after it. Nothing here may depend on which: the hook cannot read
`Translation.formatting`, because it may not exist yet, and it cannot let a meta
be what arms the wait, because a meta runs inside the formatter. So the hook
reads the translation's own text, both for the marker and for whether a word was
written, and the metas only write.

What lands becomes part of the word's own translation, by growing its `english`
where the formatter has not run yet and by taking the word back and writing it
again where it has. So one `*` undoes the word and what waited for it together,
as it does in `stfst-board`.
"""
from plover.formatting import ATOM_RE
from plover.translation import Translation

HOLD = "{:stfst_hold:"

# The windowed dictionaries, in load order, so a marker can name one: a window
# has to be harvested against the artifact after the text arrives, and a meta's
# argument is text.
windows = []

# Every directive that writes nothing, by the shape of its first character. A
# word is what a hold waits for, so this only has to be right about whether
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


def split_hold(english):
    """A translation as its body and what its marker holds, or `None` for none.

    The argument is `kind:rest`, and a tail's rest is a translation of its own
    with its braces escaped, so the atom ends at the first brace that is not.
    """
    at = english.find(HOLD)
    if at < 0:
        return english, None
    i = at + len(HOLD)
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
    kind, _, rest = "".join(arg).partition(":")
    return english[:at] + english[i:], (kind, _unescape(rest))


def writes_text(english):
    """Whether a translation puts anything on screen.

    Not "is it non-empty": a command, a case directive and a held marker all
    write nothing, and a hold waits for a word rather than for a stroke.
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


def cap_pending(translator):
    """Whether a capital is waiting for the next word.

    A frame reads it, because the word is written before the frame is: at the
    start of a sentence the word has taken the capital already, and a frame with
    a word of its own has to take it back. The newest translation that has been
    formatted is what says, and a marker's own translation writes nothing, so it
    carries the answer through either order.
    """
    for translation in reversed(translator.get_state().translations):
        if translation.formatting:
            return "on" if translation.formatting[-1].next_case else "off"
    return "off"


class _Waiting:
    """What this engine is holding: the text to append to the next word.

    One hold, not a queue. A second before the first has landed replaces it, as
    it does in `stfst-board`.
    """

    def __init__(self):
        self.text = None
        # The translation a hold last landed on, and what it appended. What undo
        # needs: taking the word back leaves the hold waiting again, as the mark
        # does in `stfst-board`.
        self.landed = None

    def arm(self, text):
        self.text = text

    def clear(self):
        self.text = None


waiting = _Waiting()


def stfst_hold(ctx, arg):
    """The marker a dictionary answers with: write nothing.

    `Ahead` reads the argument off the translation, not from here, because a meta
    runs inside the formatter and the hook may have run already.
    """
    return ctx.copy_last_action()


def stfst_copy(ctx, arg):
    """`[COPY1]`: the host's nth bound word, spliced in.

    The word never reaches the transducer, which is what keeps the automaton
    finite: a dictionary that had to know it would need a path per word. `1` is
    the newest, as the registers are numbered.
    """
    action = ctx.new_action()
    # The registers are bound before the record writes, so a word this record has
    # already written is not one of them. Counting them back off the context is
    # what makes the index stable: each word written shifts the whole span by
    # one, so the same register is the same word all the way through.
    own = len("".join(a.text or "" for a in ctx.translated_actions).split())
    at = int(arg) + own
    tokens = [t.rstrip() for t in ctx.last_words(count=at)]
    action.text = tokens[-at] if len(tokens) >= at else ""
    return action


def _rewrite(ctx, dictionary, outline, tokens, cap="off"):
    """Take those words back and write what the window rule made of them.

    `prev_replace` is how Plover's own retro metas rewrite what is written, and
    the whitespace after the last word sits outside anything a rule may touch, so
    it goes back on the end.
    """
    action = ctx.copy_last_action()
    action.text = ""
    span = "".join(tokens)
    text = dictionary.window(outline.split("/"), span.strip(), cap)
    if text is None:
        return action
    action.prev_replace = span
    action.prev_attach = True
    action.text = text + span[len(span.rstrip()):]
    action.word = None
    return action


def stfst_retro(ctx, arg):
    """A window of text already written: rewrite it.

    The stroke needs nothing to wait for, because the words are behind the
    cursor. What it is shown is the last `words` of them, which is the dial the
    recipe set.
    """
    index, outline = arg.split(":", 1)
    dictionary = windows[int(index)]
    tokens = ctx.last_words(count=dictionary.window_words())
    if not tokens:
        return ctx.copy_last_action()
    return _rewrite(ctx, dictionary, outline, tokens)


def stfst_frame(ctx, arg):
    """A frame, now that the words it reads are written: rewrite them.

    `Ahead` appends this to the word's own translation, so the words are in the
    context. What the frame is shown is the words *this* translation wrote and no
    older text, so a frame cannot reach back over what was already there.
    """
    index, cap, outline = arg.split(":", 2)
    dictionary = windows[int(index)]
    own = len("".join(a.text or "" for a in ctx.translated_actions).split())
    if not own or own > dictionary.window_words():
        # More words than the frame declared is text it cannot be shown, so it
        # does not fire: showing the last few would frame the tail of a phrase.
        action = ctx.copy_last_action()
        action.text = ""
        return action
    tokens = ctx.last_words(count=own)
    if len(tokens) < own:
        action = ctx.copy_last_action()
        action.text = ""
        return action
    return _rewrite(ctx, dictionary, outline, tokens, cap)


class Ahead:
    """The extension: write what a held stroke was waiting for."""

    def __init__(self, engine):
        self._engine = engine

    def start(self):
        self._engine.hook_connect("translated", self._translated)

    def stop(self):
        self._engine.hook_disconnect("translated", self._translated)
        waiting.clear()

    def _translated(self, undo, do):
        for translation in undo:
            _, held = split_hold(translation.english or "")
            if held is not None:
                # The stroke that held it is gone, so nothing is waiting.
                waiting.clear()
            elif waiting.landed and translation is waiting.landed[0]:
                # The word a hold landed on is gone, so it waits again.
                waiting.arm(waiting.landed[1])
                waiting.landed = None
        for translation in do:
            body, held = split_hold(translation.english or "")
            # In this order, so a stroke that both lands a hold and starts one
            # does the two in the order it wrote them.
            if waiting.text is not None and writes_text(body):
                self._land(translation)
            if held is not None:
                waiting.arm(self._append_for(*held))

    def _append_for(self, kind, rest):
        """What the next word's translation gets, for each kind of hold.

        A tail is text the formatter can write where it stands. A frame has to
        read the word first, so what lands is a meta, and the capital it reads is
        the one pending *now*: the word will have spent it by then.
        """
        if kind == "tail":
            return rest
        index, outline = rest.split(":", 1)
        return "{:stfst_frame:%s:%s:%s}" % (
            index, cap_pending(self._engine.translator), outline)

    def _land(self, translation):
        """Write it with the word, as one translation.

        One translation and not a second after it, so that one `*` takes back
        both and so that a meta in it reads the word from the same pass.

        Two ways to write it, because this hook does not know whether the
        formatter has run: `formatting` is what says. Before it, the translation
        can simply grow its tail and be formatted once. After it, the word is on
        screen already and has to be taken back and written again, which costs a
        flicker and cannot be helped.
        """
        text = waiting.text
        # Cleared before anything is written, because writing it tells this hook
        # again and there is nothing left to wait for.
        waiting.clear()
        if not translation.formatting:
            translation.english += text
            waiting.landed = (translation, text)
            return
        translator = self._engine.translator
        translator.untranslate_translation(translation)
        landed = Translation(translation.strokes, translation.english + text)
        waiting.landed = (landed, text)
        translator.translate_translation(landed)
        # This hook runs inside the flush that told it, so what it wrote needs a
        # flush of its own or it waits in the queue until the next stroke.
        translator.flush()
