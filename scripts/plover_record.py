"""Plover translation strings as records, for the golden generators.

A generator must not ask our own compiler what a translation means, or the golden
file it writes would be a snapshot of our output instead of an oracle. So this is
a second, deliberately small implementation of the part of
`stfst-compile::translate` that the hand-written dictionaries need.

It covers what emily-symbols.py and emily-modifiers.py emit and nothing else. An
unknown directive raises, because a silent guess in an oracle is worse than a
missing case.
"""

FLAG_ORDER = [
    "[CANCEL_SPACE]", "[RETRO_SPACE]", "[ATTACH_L]", "[ORTHO_L]", "[ATTACH_R]",
    "[GLUE]", "[CAP_NEXT]", "[LOWER_NEXT]", "[UPPER_NEXT]", "[CARRY_CAP]",
    "[WORD_END]",
]

FLAGS = {
    "*!": "[CANCEL_SPACE]",
    "*?": "[RETRO_SPACE]",
    "-|": "[CAP_NEXT]",
    ">": "[LOWER_NEXT]",
    "<": "[UPPER_NEXT]",
    "$": "[WORD_END]",
}

# Retrospective case operations edit text already written, so they are declared
# host operations rather than text. The name is a symbol, so a host checks it at
# load.
METAS = {
    "*-|": "[META=retro.cap][END]",
    "*<": "[META=retro.upper][END]",
    "*>": "[META=retro.lower][END]",
}

MODS = {
    "control": "[CTRL]", "ctrl": "[CTRL]",
    "shift": "[SHIFT]",
    "super": "[SUPER]", "command": "[SUPER]", "cmd": "[SUPER]",
    "alt": "[ALT]", "option": "[ALT]", "meta": "[ALT]",
    "fn": "[FN]",
}
MOD_ORDER = ["[CTRL]", "[SHIFT]", "[SUPER]", "[ALT]", "[FN]"]


class Unsupported(Exception):
    pass


def pieces(text):
    """A translation as ('text', s) and ('dir', inner) pieces, unescaped.

    A brace can appear as a glyph, and Plover spells that `\\{`, inside a
    directive as well as outside it: `{^\\}^}` is the attached text `}`. So the
    escape has to be honoured while scanning for the closing brace, not after.

    Only a brace escapes. A backslash before anything else is a backslash, which
    matters because `\\` is itself a glyph in this dictionary.
    """
    out, run, i, depth = [], "", 0, 0
    while i < len(text):
        c = text[i]
        if c == "\\" and text[i + 1:i + 2] in ("{", "}"):
            run += text[i + 1]
            i += 2
            continue
        if c == "{" and depth == 0:
            if run:
                out.append(("text", run))
            run, depth = "", 1
        elif c == "}" and depth == 1:
            out.append(("dir", run))
            run, depth = "", 0
        else:
            run += c
        i += 1
    if run:
        out.append(("text", run) if depth == 0 else ("dir", run))
    return out


def keystroke(token, mods=()):
    """`shift(control(c))` as `[KEY]c[CTRL][SHIFT][END]` items.

    A modifier wrapper is a flag on the item. Anything else a wrapper holds is
    that key pressed and then the rest, so it becomes two items.
    """
    head, paren, rest = token.partition("(")
    if not paren:
        held = [m for m in MOD_ORDER if m in mods]
        return ["[KEY]" + token + "".join(held) + "[END]"]
    inner = rest[:-1] if rest.endswith(")") else rest
    name = head.removesuffix("_L").removesuffix("_R").lower()
    if name in MODS:
        return keystroke(inner, tuple(mods) + (MODS[name],))
    return keystroke(head, mods) + keystroke(inner, mods)


def record(translation, extra=()):
    """A Plover translation as a record in canonical order.

    `extra` are flags the caller already knows, which is how a rule that wraps a
    translation is expressed without wrapping the string: emily-modifiers' literal
    mode concatenates `{^}` onto a glyph, and a glyph can be a brace or a
    backslash, so the concatenation is ambiguous in a way the flag is not.
    """
    body, flags = [], list(extra)
    # One case per record. A flag the translation sets beats the same slot from
    # `extra`, because the caller's flag came from a key and the translation's came
    # from the glyph: `{<}` cases the whole word and the key only its first letter.
    CASE = ("[CAP_NEXT]", "[LOWER_NEXT]", "[UPPER_NEXT]")

    def flag(tag):
        if tag in CASE:
            flags[:] = [f for f in flags if f not in CASE]
        if tag not in flags:
            flags.append(tag)

    for kind, value in pieces(translation):
        if kind == "text":
            body.append(value)
            continue
        if value in METAS:
            body.append(METAS[value])
        elif value in FLAGS:
            flag(FLAGS[value])
        elif value.startswith("#"):
            for token in value[1:].split():
                body.extend(keystroke(token))
        elif value.startswith("&"):
            flag("[GLUE]")
            body.append(value[1:])
        elif value == "^":
            flag("[ATTACH_R]" if body else "[ATTACH_L]")
        elif value.startswith("^") or value.endswith("^"):
            # A caret with text after it and none before is a suffix, so the join
            # applies the system's orthography. An infix goes inside a word, so it
            # does not.
            left, right = value.startswith("^"), value.endswith("^")
            core = value[1 if left else None:-1 if right else None]
            if left:
                flag("[ORTHO_L]" if core and not right else "[ATTACH_L]")
            if right:
                flag("[ATTACH_R]")
            if core:
                body.append(core)
        elif value == "":
            pass
        else:
            raise Unsupported(translation)
    return "".join(body) + "".join(f for f in FLAG_ORDER if f in flags)
