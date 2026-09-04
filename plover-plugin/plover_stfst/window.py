"""Text a host has written, as the tape carries it.

`stfst_core::window`, in Python. A window dictionary is shown a span of text
after its keys, and the span does not reach the tape as characters: each one
arrives as the *class* it belongs to, unless the grammar named the character, in
which case it arrives as itself. So a rule that recases a letter never learns
which letter it was, and the host applies its own casing when it substitutes the
answer back.

`chars` is kept beside the glyphs because that substitution needs it: a class
glyph coming back means "the next character of this span".
"""

WINDOW_OPEN, WINDOW_CLOSE = "[W]", "[/W]"
DROP = "[DROP]"

LETTER, UPPER, CASELESS = "[LETTER]", "[UPPER]", "[CASELESS]"
DIGIT, OTHER, SPACE = "[DIGIT]", "[OTHER]", "[SP]"


def class_of(c):
    """Which class a character reaches the tape as."""
    if c.isspace():
        return SPACE
    if c.isnumeric():
        return DIGIT
    if not c.isalpha():
        return OTHER
    # A letter with no case at all: every script that does not have one, and the
    # reason a recase can skip a word rather than mangle it.
    if c.lower() == c.upper():
        return CASELESS
    return LETTER if c.lower() == c else UPPER


def sig_symbol(c):
    """A named character as a tape symbol.

    `[=-]` and not `-`, because `-` is how a key spells itself and the compiler
    has to be able to read the named set straight out of the alphabet.
    """
    return "[=%s]" % c


def encode(span, significant):
    """A span as `(symbols, chars)`: the whole window including its delimiters,
    and the characters the class glyphs stand for, in order.

    A named character is not among the characters: it is literal on both tapes,
    so it never takes a substitution's turn.
    """
    symbols, chars = [WINDOW_OPEN], []
    for c in span:
        if c in significant:
            symbols.append(sig_symbol(c))
            continue
        symbols.append(class_of(c))
        chars.append(c)
    symbols.append(WINDOW_CLOSE)
    return symbols, chars
