"""Records and Plover translations, each read as the other.

Plover mixes text and directives in one band; a record separates a body from a
closed set of tags. This is that correspondence, both ways, in one module,
because the two directions have to agree and a second copy of either would
eventually not.

`Record.from_translation` is a second implementation of
`stfst-compile::translate`, and that is deliberate: the golden generators here
must not ask our own compiler what a translation means, or the file they write
would be a snapshot of our output instead of an oracle. `Record.translation` is
the direction nothing in the Rust has, because only a host whose dictionary API
speaks Plover's language needs it.

Anything unrecognised raises `Unsupported` rather than being guessed at. A
record this module cannot spell is a dictionary entry Plover does not get, which
is a loss it can report; a wrong spelling is a loss it cannot.
"""

from .window import CASELESS, DIGIT, LETTER, OTHER, SPACE, UPPER, sig_char

# The flag tags, in canonical tape order. `stfst_core::record::FLAG_TAGS`.
FLAG_TAGS = [
    "[CANCEL_SPACE]", "[RETRO_SPACE]", "[ATTACH_L]", "[ORTHO_L]", "[ATTACH_R]",
    "[GLUE]", "[CAP_NEXT]", "[LOWER_NEXT]", "[UPPER_NEXT]", "[CARRY_CAP]",
    "[WORD_END]",
]

# Modifier tags, in canonical order, valid only inside a `[KEY]` item. The name
# each one is written with in a Plover key combo.
MOD_TAGS = [
    ("[CTRL]", "control"),
    ("[SHIFT]", "shift"),
    ("[SUPER]", "super"),
    ("[ALT]", "alt"),
    ("[FN]", "fn"),
]

CLASS_TAGS = [LETTER, UPPER, CASELESS, DIGIT, OTHER, SPACE]

ESCAPE = "[U+]"
DROP = "[DROP]"
STATE_OPEN, STATE_CLOSE = "[STATE]", "[END]"

# `[AFTER]…[END]`: the record's tail, written after the unit that follows it.
AFTER = "[AFTER]"

# `[IF_NEXT]pattern[THEN]text[ELSE]text[END]`: Plover's `{=pattern/then/else}`.
IF_NEXT, THEN, ELSE = "[IF_NEXT]", "[THEN]", "[ELSE]"

# A fold record's tags. `plover_stfst.fold` reads one; nothing else may take one
# for an entry, because a fold answers only where nothing matched.
FOLD, STRIP, ADD = "[FOLD]", "[STRIP]", "[ADD]"

# `stfst_core::host`. A host implements a fixed set of operations, so the set is
# closed and a name outside it is an error rather than a request.
COMMANDS = [
    "mode.caps", "mode.title", "mode.lower", "mode.camel", "mode.snake",
    "mode.reset_case", "mode.set_space", "mode.reset_space", "mode.reset",
    "engine.undo", "engine.repeat_stroke", "engine.toggle_star",
    "plover.suspend", "plover.resume", "plover.toggle", "plover.add_translation",
    "plover.add_outline", "plover.lookup", "plover.suggestions", "plover.configure", "plover.focus",
    "plover.quit", "plover.set_config", "plover.steno_chords",
    "stack.toggle", "stack.solo", "stack.end_solo", "stack.priority",
]

METAS = ["retro.cap", "retro.upper", "retro.lower", "retro.currency", "infl"]

CASES = {"[CAP_NEXT]": "cap", "[LOWER_NEXT]": "lower", "[UPPER_NEXT]": "upper"}
SPACES = {"[CANCEL_SPACE]": "cancel", "[RETRO_SPACE]": "retro"}


class Unsupported(Exception):
    pass


def is_tag(symbol):
    return len(symbol) > 1 and symbol.startswith("[") and symbol.endswith("]")


def counted(symbol, prefix):
    """`[REP3]` -> 3 when `prefix` is `REP`."""
    if not is_tag(symbol) or not symbol[1:-1].startswith(prefix):
        return None
    rest = symbol[1 + len(prefix):-1]
    return int(rest) if rest.isdigit() else None


class Record:
    """A body of items and the closed tags that come after it.

    The tags come last because the keys that decide them come last; nothing here
    depends on that, because the parser takes them in any order.
    """

    def __init__(self, body=(), **flags):
        self.body = list(body)
        self.attach_l = flags.pop("attach_l", False)
        self.ortho_l = flags.pop("ortho_l", False)
        self.attach_r = flags.pop("attach_r", False)
        self.glue = flags.pop("glue", False)
        self.carry_cap = flags.pop("carry_cap", False)
        self.word_end = flags.pop("word_end", False)
        # One value per slot, so `[CAP_NEXT][LOWER_NEXT]` is not a state a record
        # can be in.
        self.case = flags.pop("case", None)
        self.space = flags.pop("space", None)
        self.repeat = flags.pop("repeat", 1)
        self.replace = flags.pop("replace", None)
        self.state = flags.pop("state", None)
        # The tail: a record of its own, written after the unit that follows this
        # one. `plover_stfst.hold` is what waits with it.
        self.after = flags.pop("after", None)
        if flags:
            raise TypeError("no such flag: %s" % ", ".join(sorted(flags)))

    def __eq__(self, other):
        return isinstance(other, Record) and self.symbols() == other.symbols()

    def __repr__(self):
        return "Record(%r)" % str(self)

    def __str__(self):
        return "".join(self.symbols())

    def rewrite(self, chars):
        """This record as text, against the window it was shown.

        A class glyph means "the next character of the window", in order, and a
        `[DROP]` means the next character is not written. So a rule accounts for
        every one of them and the host does the casing, which is why full Unicode
        casing needs no case table in the automaton.

        Only a window record reaches this, and a window record is text: anything
        that needs a gap of its own or a flag to place it is refused, because a
        rewrite has nowhere to put one.
        """
        # A record with the same body and no flags spells the same tape iff this
        # one has none either.
        if self.symbols() != Record(self.body).symbols():
            raise Unsupported("a flag on a window record")
        out, taken = [], 0
        for item in self.body:
            kind = item[0]
            if kind == "text":
                out.append(item[1])
            elif kind == "drop":
                taken += 1
            elif kind == "glyph":
                if taken >= len(chars):
                    raise Unsupported("a window of %d characters, and the record "
                                      "asks for %d" % (len(chars), taken + 1))
                c, taken = chars[taken], taken + 1
                out.append({LETTER: c.lower(), UPPER: c.upper()}
                           .get(item[1], c))
            else:
                raise Unsupported("%s in a window record" % kind)
        return "".join(out)

    def preview(self):
        """How this tail shows while it waits: the close, before the word inside.

        `Record::preview` in `stfst-core`, and the same rule: a tail joined
        orthographically wants a word nobody has written, so it shows as a hyphen
        and a suffix rather than joining the body, and a tail that only follows
        text shows as itself. A keystroke and a command are not text, so a
        preview does neither.
        """
        suffix = self.ortho_l or any(i[0] == "meta" for i in self.body)
        shown = {"meta": lambda i: ("text", i[2]), "if_next": lambda i: ("text", i[3])}
        body = [shown.get(i[0], lambda i: i)(i)
                for i in self.body if i[0] in ("text", "meta", "copy", "if_next")]
        if not suffix:
            return Record(body, **self._flags())
        flags = self._flags()
        flags["attach_l"] = flags["ortho_l"] = False
        return Record([("text", "-")] + body, **flags)

    def _flags(self):
        """Every flag of this record, as the constructor takes them."""
        return {name: getattr(self, name) for name in
                ("attach_l", "ortho_l", "attach_r", "glue", "carry_cap",
                 "word_end", "case", "space", "repeat", "replace", "state")}

    def translation(self):
        """This record as a Plover translation.

        Raises `Unsupported` for a record Plover has no way to hold: a carried
        state, a superseded unit, a bound word, a window glyph. Those are host
        contract items 5 and 6, and a dictionary that uses them is refused at
        load, so this is the second line rather than the first.
        """
        return _translation(self)

    @classmethod
    def from_translation(cls, text, extra=()):
        """A Plover translation as a record.

        `extra` are tags the caller already knows, which is how a rule that wraps
        a translation is expressed without wrapping the string: a glyph can be a
        brace or a backslash, so the concatenation is ambiguous in a way the flag
        is not.
        """
        return _from_translation(cls, text, extra)

    # ---- the tape

    @classmethod
    def parse(cls, symbols):
        """A tape as a record. Each symbol is a tag or one character of text."""
        symbols = list(symbols)
        # The tail is last and delimited, so it comes off first and what is left
        # is an ordinary record.
        after = None
        if AFTER in symbols:
            i = symbols.index(AFTER)
            if symbols[-1] != STATE_CLOSE:
                raise Unsupported("unterminated [AFTER]")
            after = cls.parse(symbols[i + 1:-1])
            if after.after is not None:
                raise Unsupported("an [AFTER] tail holds another one")
            symbols = symbols[:i]
        state = None
        # `[STATE]` cannot appear inside an item, so the last one opens the block
        # and the tape has to end with its `[END]`.
        opens = [i for i, s in enumerate(symbols) if s == STATE_OPEN]
        if opens:
            if symbols[-1] != STATE_CLOSE:
                raise Unsupported("unterminated [STATE]")
            state = "".join(symbols[opens[-1] + 1:-1])
            symbols = symbols[:opens[-1]]
        body, i = _parse_body(symbols)
        record = cls(body, state=state, after=after)
        record._parse_flags(symbols[i:])
        return record

    def _parse_flags(self, symbols):
        for s in symbols:
            if s in CASES:
                if self.case is not None:
                    raise Unsupported("a record carries one case at most")
                self.case = CASES[s]
            elif s in SPACES:
                if self.space is not None:
                    raise Unsupported("[CANCEL_SPACE] and [RETRO_SPACE] cannot both apply")
                self.space = SPACES[s]
            elif s in ("[ATTACH_L]", "[ORTHO_L]", "[ATTACH_R]", "[GLUE]",
                       "[CARRY_CAP]", "[WORD_END]"):
                name = {"[ATTACH_L]": "attach_l", "[ORTHO_L]": "ortho_l",
                        "[ATTACH_R]": "attach_r", "[GLUE]": "glue",
                        "[CARRY_CAP]": "carry_cap", "[WORD_END]": "word_end"}[s]
                if getattr(self, name):
                    raise Unsupported("%s appears twice" % s)
                setattr(self, name, True)
            elif counted(s, "REPLACE") is not None:
                self.replace = counted(s, "REPLACE")
            elif counted(s, "REP") is not None:
                self.repeat = counted(s, "REP")
            else:
                raise Unsupported("%s appears after a flag tag" % s)

    def symbols(self):
        """The canonical tape form."""
        out = []
        for item in self.body:
            kind = item[0]
            if kind == "text":
                out.extend(_text_symbols(item[1]))
            elif kind == "key":
                out.append("[KEY]")
                out.extend(item[1])
                out.extend(t for t, _ in MOD_TAGS if t in item[2])
                out.append("[END]")
            elif kind in ("cmd", "meta"):
                out.append("[%s=%s]" % ("CMD" if kind == "cmd" else "META", item[1]))
                out.extend(item[2])
                out.append("[END]")
            elif kind == "copy":
                out.append("[COPY%d]" % item[1])
            elif kind == "if_next":
                for tag, text in zip((IF_NEXT, THEN, ELSE), item[1:]):
                    out.append(tag)
                    out.extend(text)
                out.append("[END]")
            elif kind == "glyph":
                out.append(item[1])
            else:
                out.append(DROP)
        if self.space:
            out.append("[CANCEL_SPACE]" if self.space == "cancel" else "[RETRO_SPACE]")
        for flag, tag in (("attach_l", "[ATTACH_L]"), ("ortho_l", "[ORTHO_L]"),
                          ("attach_r", "[ATTACH_R]"), ("glue", "[GLUE]")):
            if getattr(self, flag):
                out.append(tag)
        if self.case:
            out.append({"cap": "[CAP_NEXT]", "lower": "[LOWER_NEXT]",
                        "upper": "[UPPER_NEXT]"}[self.case])
        if self.carry_cap:
            out.append("[CARRY_CAP]")
        if self.word_end:
            out.append("[WORD_END]")
        if self.repeat != 1:
            out.append("[REP%d]" % self.repeat)
        if self.replace is not None:
            out.append("[REPLACE%d]" % self.replace)
        if self.after is not None:
            out.append(AFTER)
            out.extend(self.after.symbols())
            out.append(STATE_CLOSE)
        if self.state is not None:
            out.append(STATE_OPEN)
            out.extend(self.state)
            out.append(STATE_CLOSE)
        return out


def _text_symbols(text):
    """Text as tape symbols, escaped where the tape cannot carry the character."""
    out = []
    for c in text:
        if c.isprintable() or c == " ":
            out.append(c)
        else:
            out.append(ESCAPE)
            out.extend("%02X" % ord(c))
            out.append("[END]")
    return out


def _parse_body(symbols):
    """The body, up to the first flag tag, and where the flags start."""
    body, i = [], 0
    while i < len(symbols) and not _is_flag(symbols[i]):
        s = symbols[i]
        if s == "[KEY]":
            name, mods, i = _take_key(symbols, i + 1)
            body.append(("key", name, mods))
        elif s == DROP:
            body.append(("drop",))
            i += 1
        elif s in CLASS_TAGS:
            body.append(("glyph", s))
            i += 1
        elif sig_char(s) is not None:
            # A character the grammar named reaches both tapes as itself, so on
            # this one it is text like any other.
            body.append(("text", sig_char(s)))
            i += 1
        elif counted(s, "COPY") is not None:
            body.append(("copy", counted(s, "COPY")))
            i += 1
        elif s == IF_NEXT:
            pattern, i = _take_until(symbols, i + 1, THEN)
            then, i = _take_until(symbols, i, ELSE)
            otherwise, i = _take_until(symbols, i, "[END]")
            body.append(("if_next", pattern, then, otherwise))
        elif _op(s, "CMD") or _op(s, "META"):
            kind = "cmd" if _op(s, "CMD") else "meta"
            name = _op(s, "CMD") or _op(s, "META")
            arg, i = _take_until(symbols, i + 1, "[END]")
            body.append((kind, name, arg))
        elif is_tag(s) and s != ESCAPE:
            raise Unsupported("unknown tag %s" % s)
        else:
            # Merge a run of characters into one text item. An escape is one of
            # those characters, so it joins the run rather than breaking it.
            text = ""
            while i < len(symbols):
                if symbols[i] == ESCAPE:
                    # Two hex digits and the terminator, so the whole item is a
                    # fixed four symbols.
                    if symbols[i + 3:i + 4] != ["[END]"]:
                        raise Unsupported("unterminated %s" % ESCAPE)
                    text += chr(int("".join(symbols[i + 1:i + 3]), 16))
                    i += 4
                elif not is_tag(symbols[i]):
                    text += symbols[i]
                    i += 1
                else:
                    break
            body.append(("text", text))
    return body, i


def _op(symbol, kind):
    """The name inside a `[CMD=…]` or `[META=…]` symbol, when the table has it."""
    if not is_tag(symbol):
        return None
    name = symbol[1:-1]
    if not name.startswith(kind + "="):
        return None
    name = name[len(kind) + 1:]
    return name if name in (COMMANDS if kind == "CMD" else METAS) else None


def _is_flag(symbol):
    return (symbol in FLAG_TAGS or counted(symbol, "REP") is not None
            or counted(symbol, "REPLACE") is not None)


def _take_key(symbols, i):
    """A keystroke item: the keysym, then its modifiers, then `[END]`."""
    name = ""
    while i < len(symbols) and not is_tag(symbols[i]):
        name += symbols[i]
        i += 1
    mods = []
    known = {t for t, _ in MOD_TAGS}
    while i < len(symbols):
        if symbols[i] == "[END]":
            return name, mods, i + 1
        if symbols[i] not in known:
            break
        mods.append(symbols[i])
        i += 1
    raise Unsupported("unterminated [KEY]")


def _take_until(symbols, i, terminator):
    text = ""
    while i < len(symbols):
        if symbols[i] == terminator:
            return text, i + 1
        text += symbols[i]
        i += 1
    raise Unsupported("unterminated argument")


# ---- a record as a Plover translation

# What a Plover translation calls each command. `{PLOVER:…}` for the engine, and
# a name of its own for the three that reach the stroke buffer, which Plover
# spells as macros.
MACROS = {
    "engine.undo": "=undo",
    "engine.repeat_stroke": "{*+}",
    "engine.toggle_star": "{*}",
}
PLUGIN_COMMANDS = {
    "stack.toggle": "toggle_dict",
    "stack.solo": "solo_dict",
    "stack.end_solo": "end_solo_dict",
    "stack.priority": "priority_dict",
}
RETRO_METAS = {"retro.cap": "{*-|}", "retro.upper": "{*<}", "retro.lower": "{*>}"}

# Plover reads a directive's opening characters before it reads the carets, so a
# payload that starts with one of these is that meta: `{&^}` is glue on a caret
# and not an ampersand joined rightward. A leading caret settles it the other way,
# which is what makes `{^&^}` an ampersand. `~|` is not here because Plover's
# carry-capitalisation peels the carets itself.
SIGILS = ("&", "#", ":", "=")


def _escape(text):
    """Text as a Plover atom. Only a brace escapes, and only with a backslash."""
    if "\\{" in text or "\\}" in text:
        # `\\{` reads as a backslash and then the start of a directive, so the
        # two characters cannot both be literal.
        raise Unsupported("a backslash before a brace has no spelling")
    return text.replace("{", "\\{").replace("}", "\\}")


def _spell(text, left, right):
    """One word-writing atom, joined as asked.

    `left` is "ortho", "plain" or None. Attachment is a caret at the edge of a
    directive and Plover peels exactly one from each end, so a payload with a
    caret of its own survives inside the brackets and only a bare text atom has
    to worry about it.
    """
    inner = _escape(text)
    if left == "ortho":
        if not text:
            raise Unsupported("an orthographic join with nothing to join")
        # Orthography joins a *suffix* to a word, and a suffix is letters: no rule
        # can fire on anything else, so a join over it is plain concatenation. A
        # tape merges the runs an atom boundary once separated, and this is what
        # recovers it: `ed?` is `ed` joined orthographically and then `?` stuck on.
        head = text[:_letters(text)]
        rest = text[_letters(text):]
        out = "{^%s}" % _escape(head) if head else ""
        out += "{^%s}" % _escape(rest) if rest else ""
        return out + ("{^}" if right else "")
    if left == "plain":
        if right:
            return "{^%s^}" % inner
        if not text or text == text.strip():
            return "{^^}%s" % inner
        if not text[:1].isalpha():
            # A bare atom would lose the edge whitespace, and the bracketed form
            # is an orthographic join no rule can fire on.
            return "{^%s}" % inner
        raise Unsupported("attaching leftward around %r" % text)
    if right:
        # `{x^}` keeps the payload's own spacing. A payload that opens with a caret
        # or a sigil reads as something else inside the braces, so it stands
        # outside them and the caret becomes an atom of its own.
        if text and not text.startswith(("^",) + SIGILS):
            return "{%s^}" % inner
        return "%s{^}" % inner
    # Plover trims a text atom's edges and supplies the word gap itself, which is
    # the gap this record spells.
    return inner


# What can appear inside a word, and so inside a suffix. Plover's orthography
# rules are written over these; a character outside them ends the suffix.
WORD_CHARS = "'-"


def _letters(text):
    """How much of the text could be a suffix."""
    n = 0
    while n < len(text) and (text[n].isalpha() or text[n] in WORD_CHARS):
        n += 1
    return n


def _merged(body):
    """Adjacent text runs as one item. A tape cannot tell two of them apart, so a
    record read back from one has them merged already."""
    out = []
    for item in body:
        if item[0] == "text" and out and out[-1][0] == "text":
            out[-1] = ("text", out[-1][1] + item[1])
        else:
            out.append(item)
    return out


def _key_combo(name, mods):
    """`[KEY]c[CTRL][SHIFT][END]` as `control(shift(c))`."""
    held = [plover for tag, plover in MOD_TAGS if tag in mods]
    return "(".join(held + [name]) + ")" * len(held)


def _command(name, arg):
    if name in MACROS:
        if arg:
            raise Unsupported("%s takes no argument" % name)
        return MACROS[name]
    if name.startswith("mode."):
        return "{mode:%s%s}" % (name[5:].upper(), ":" + arg if arg else "")
    plover = PLUGIN_COMMANDS.get(name, name[len("plover."):] if
                                 name.startswith("plover.") else None)
    if plover is None:
        raise Unsupported("no Plover spelling for the command %s" % name)
    return "{PLOVER:%s%s}" % (plover.upper(), ":" + arg if arg else "")


def _escape_meta(text):
    """A translation as the argument of a meta.

    Plover reads an atom up to the first unescaped brace, so a translation nested
    inside one has to escape its own. `\\{` and `\\}` are Plover's own escapes,
    which `_unescape_atom` undoes before a plugin sees the argument.
    """
    return text.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}")


def _meta(name, arg):
    if name in RETRO_METAS:
        if arg:
            raise Unsupported("%s takes no argument" % name)
        return RETRO_METAS[name]
    if name == "retro.currency":
        return "{*(%s)}" % arg
    if name == "infl":
        return "{:infl:%s}" % arg
    raise Unsupported("no Plover spelling for the meta %s" % name)


def _if_next(pattern, then, otherwise):
    r"""`{=pattern/then/else}`. Plover reads `\\` and `\/` as a backslash and a
    slash in every field, and has no empty one."""
    if not (pattern and then and otherwise):
        raise Unsupported("an empty field in {=…}")
    fields = [f.replace("\\", "\\\\").replace("/", "\\/")
              for f in (pattern, then, otherwise)]
    return "{=%s}" % "/".join(_escape(f) for f in fields)


def _if_next_fields(arg, d):
    """`stfst_core::translate::if_next`: an unescaped slash ends the pattern and
    then `then`, and `else` is the rest. Plover drops an empty field and misreads
    any other backslash before `else`, so neither is read here."""
    fields, i = [""], 0
    while i < len(arg):
        c, last = arg[i], len(fields) == 3
        if c == "\\":
            nxt = arg[i + 1:i + 2]
            if nxt in ("\\", "/"):
                fields[-1] += nxt
            elif last:
                fields[-1] += c + nxt
            else:
                raise Unsupported("unsupported directive {%s}" % d)
            i += 2
            continue
        if c == "/" and not last:
            fields.append("")
        else:
            fields[-1] += c
        i += 1
    if len(fields) != 3 or not all(fields):
        raise Unsupported("unsupported directive {%s}" % d)
    return tuple(fields)


def _translation(self):
    if self.state is not None:
        raise Unsupported("a carried state")
    if self.replace is not None:
        raise Unsupported("[REPLACE%d]" % self.replace)
    # The tail is not part of what this translation writes. It is a translation
    # of its own, held by `plover_stfst.hold` until a unit writes a word, and the
    # marker is how the body carries it there.
    tail = ""
    if self.after is not None:
        tail = "{:stfst_hold:tail:%s}" % _escape_meta(self.after.translation())

    # Every atom, and whether it puts a word on screen. Only a word takes a gap
    # beside it, so only a word decides where the joins go.
    atoms = []
    for item in _merged(self.body * self.repeat):
        kind = item[0]
        if kind == "text":
            atoms.append([True, item[1]])
        elif kind == "key":
            atoms.append([False, "{#%s}" % _key_combo(item[1], item[2])])
        elif kind == "cmd":
            atoms.append([False, _command(item[1], item[2])])
        elif kind == "meta":
            atoms.append([False, _meta(item[1], item[2])])
        elif kind == "copy":
            # A bound word is a word, but the record's own space is what separates
            # it from what came before, so it takes no join of its own and does
            # not count as one of the words the gap rules read.
            atoms.append([False, "{:stfst_copy:%d}" % item[1]])
        elif kind == "if_next":
            # A word, but one Plover spells as a meta, so like a bound word it
            # takes the record's own spacing and no join of its own.
            atoms.append([False, _if_next(*item[1:])])
        else:
            raise Unsupported("%s, which needs a window" % kind)

    words = [a for a in atoms if a[0]]
    for n, atom in enumerate(words):
        # A gap inside the record is one the record does not have: two text runs
        # with an item between them are one word. So every word but the first
        # closes the gap before it, and only the edges carry the record's own
        # attachment.
        left = "plain" if n else ("ortho" if self.ortho_l else
                                  "plain" if self.attach_l else None)
        right = self.attach_r and n == len(words) - 1
        if self.glue:
            # Glue is a core of its own, so it composes with attachment only by
            # standing between two directives that say the joins.
            if self.carry_cap:
                raise Unsupported("a glued word that also carries a capital")
            atom[1] = "{&%s}" % _escape(atom[1])
            if left:
                atom[1] = "{^^}" + atom[1]
            if right:
                atom[1] += "{^}"
            continue
        text = "~|" + atom[1] if self.carry_cap else atom[1]
        if self.carry_cap and left is None and not right:
            atom[1] = "{~|%s}" % _escape(atom[1])
        else:
            atom[1] = _spell(text, left, right)

    out = []
    if self.space:
        out.append("{*!}" if self.space == "cancel" else "{*?}")
    if not words and self.ortho_l:
        raise Unsupported("an orthographic join with nothing to join")
    if not words and self.carry_cap:
        out.append("{~|}")
    out.extend(a[1] for a in atoms)
    # A record that writes no word has one attachment Plover can hold: whether the
    # next word joins. Nothing of its own is there for a left join to close, and
    # `stfst-text` reads it the same way.
    if not words and self.attach_r:
        out.append("{^}")
    if self.case:
        # After the body, because the case is for the next word and Plover would
        # otherwise spend it on this record's own.
        out.append({"cap": "{-|}", "lower": "{>}", "upper": "{<}"}[self.case])
    if self.word_end:
        out.append("{$}")
    out.append(tail)
    # An empty translation is an untranslate in Plover, and an untranslate writes
    # the raw steno. A record that writes nothing has to say so.
    return "".join(out) or "{}"


# ---- a Plover translation as a record
#
# A second implementation of `stfst-compile::translate`. Plover mixes text and
# directives in one band, so text and directives share a namespace and every
# consumer reimplements an escaping grammar. This is that grammar.

CASE_NAMES = {"cap_first_word": "cap", "upper_first_word": "upper",
              "lower_first_char": "lower"}

MODIFIERS = {"control": "[CTRL]", "ctrl": "[CTRL]", "shift": "[SHIFT]",
             "super": "[SUPER]", "command": "[SUPER]", "cmd": "[SUPER]",
             "alt": "[ALT]", "option": "[ALT]", "meta": "[ALT]", "fn": "[FN]"}


class _Join:
    """How one directive joins, which is the only thing about it that depends on
    where it sits.

    Plover's attachment is per atom and a record's is per record, so the two only
    line up at the edges: `co{^}` is "co" joined to what follows and not to what
    precedes, which is why `x co{^} y` writes "x coy".
    """

    def __init__(self, left=False, ortho=False, right=False):
        self.left, self.ortho, self.right = left, ortho, right

    def speaks(self):
        return self.left or self.ortho or self.right


def _split_pieces(s):
    out, text, i = [], "", 0
    while i < len(s):
        c = s[i]
        if c == "\\":
            # Plover only unescapes braces, so a lone backslash is literal.
            nxt = s[i + 1:i + 2]
            text += nxt if nxt in ("{", "}") else "\\" + nxt
            i += 2
            continue
        if c == "{":
            if text:
                out.append(("text", text))
                text = ""
            inner, closed, i = "", False, i + 1
            while i < len(s):
                if s[i] == "\\":
                    nxt = s[i + 1:i + 2]
                    inner += nxt if nxt in ("{", "}") else "\\" + nxt
                    i += 2
                    continue
                if s[i] == "}":
                    closed, i = True, i + 1
                    break
                inner += s[i]
                i += 1
            if not closed:
                raise Unsupported("unbalanced brace")
            out.append(("dir", inner))
            continue
        if c == "}":
            raise Unsupported("unbalanced brace")
        text += c
        i += 1
    if text:
        out.append(("text", text))
    return out


def _held_keys(tokens):
    """Split at whitespace outside any wrapper: the space in `Alt_L(Tab Tab)` is
    inside one."""
    out, depth, start = [], 0, None
    for i, c in enumerate(tokens):
        if c == "(":
            depth += 1
        elif c == ")":
            depth = max(0, depth - 1)
        elif c.isspace() and depth == 0:
            if start is not None:
                out.append(tokens[start:i])
                start = None
            continue
        if start is None:
            start = i
    if start is not None:
        out.append(tokens[start:])
    return out


def _keystrokes(token, mods, out):
    """Plover writes a held key as a wrapper: `Control_L(Left)` is ctrl and Left,
    and `Alt_L(Tab(Tab))` is alt held over two presses of Tab. A modifier wrapper
    sets a flag; any other wrapper is that key pressed and then the rest."""
    held = _held_keys(token)
    if len(held) > 1:
        for word in held:
            _keystrokes(word, mods, out)
        return
    head, paren, rest = token.partition("(")
    if not paren:
        out.append(("key", token, list(mods)))
        return
    if not rest.endswith(")"):
        raise Unsupported("unbalanced key combo %r" % token)
    inner = rest[:-1]
    # A side is not a distinction any host here can act on.
    name = head
    for side in ("_L", "_R"):
        if name.endswith(side):
            name = name[:-2]
    tag = MODIFIERS.get(name.lower())
    if tag is None:
        out.append(("key", head, list(mods)))
        _keystrokes(inner, mods, out)
        return
    if tag in mods:
        raise Unsupported("%s twice in one key combo" % tag)
    _keystrokes(inner, list(mods) + [tag], out)


def _writes_word(items):
    """Whether these items put a word on screen. A keystroke and a command do
    not, so they take no space beside them and decide no boundary."""
    return any(i[0] in ("text", "copy", "glyph", "if_next") for i in items)


def _recase(text, case):
    """A pending capital applied to the first letter of some text."""
    out, done = "", False
    for c in text:
        if not done and c.isalpha():
            done = True
            out += c.lower() if case == "lower" else c.upper()
        else:
            out += c
    return out


def _peel_attach(d):
    """Strip the leading and trailing `^` that mark attachment."""
    # `{^}` joins across an empty record, so its one caret means both sides.
    if d == "^":
        return "", True, True
    left = d.startswith("^")
    if not left and d.startswith(SIGILS):
        return d, False, False
    if left:
        d = d[1:]
    right = d.endswith("^")
    if right:
        d = d[:-1]
    return d, left, right


def _from_translation(cls, text, extra):
    record = cls()
    # Each atom, with how it joins and which kind it is. A record concatenates its
    # items with no gaps and Plover's atoms are separate actions with a space
    # between them, so the gaps have to be put in here.
    atoms = []
    case_at = None
    for kind, value in _split_pieces(text):
        if kind == "text":
            # A text atom that is only whitespace writes nothing: Plover splits an
            # atom into words and a blank one has none. Whitespace *inside* an atom
            # survives, and so does a directive whose core is a space.
            if value.strip():
                atoms.append(([("text", value)], _Join(), "text"))
            continue
        had_case = record.case is not None
        items, join = _directive(value, record)
        if not had_case and record.case is not None:
            case_at = len(atoms)
        # `{}` is Plover's empty text atom: it writes nothing and closes the gap,
        # where a keystroke or a case meta beside it leaves the gap as it found it.
        atoms.append((items, join, "text" if value == "" else "directive"))

    # A pending capital applies to the next *word*, and that word can be in this
    # same record: `{.}so` is ". So". The record language has one case flag for the
    # whole record, so where the word is here the capital is spent here.
    if case_at is not None:
        for items, _, _ in atoms[case_at + 1:]:
            if not _writes_word(items):
                continue
            for n, item in enumerate(items):
                if item[0] == "text":
                    items[n] = ("text", _recase(item[1], record.case))
                    break
            # Spent even where there was no letter to spend it on, which is what
            # Plover does: `{!})` leaves the word after it lowercase.
            record.case = None
            break

    # Attachment is the one positional property. The record's left edge is the
    # first atom's and its right edge is the last atom's, where "atom" means one
    # that writes a word or says something about joining.
    speaking = [(i, j) for i, j, kind in atoms
                if _writes_word(i) or j.speaks() or kind == "text"]
    if speaking:
        record.attach_l = speaking[0][1].left
        record.ortho_l = speaking[0][1].ortho
        record.attach_r = speaking[-1][1].right

    # A word gap at either end of a text atom is the record's business and not the
    # text's, so Plover's word splitting drops it and the gaps below put back the
    # ones that belong between atoms.
    for items, _, kind in atoms:
        if kind == "text" and items and items[0][0] == "text":
            items[0] = ("text", items[0][1].strip())

    # Two atoms that both write and neither of which attaches have a word boundary
    # between them, and the record has to spell it: `{,}and` is "run, and".
    body, gap_owed = [], False
    for items, join, _ in atoms:
        if not _writes_word(items):
            # A keystroke's attachment still speaks for the gap it sits in:
            # `{,}{^}and` closes that one.
            if join.right:
                gap_owed = False
            body.extend(items)
            continue
        if gap_owed and not join.left and not join.ortho:
            body.append(("text", " "))
        gap_owed = not join.right
        body.extend(items)
    # One item per atom, deliberately unmerged: merging them printed the same
    # record and meant a different one, because an orthographic join applies to the
    # first word and so the first word has to be its own item.
    record.body = [i for i in body if i != ("text", "")]
    if extra:
        _apply_extra(record, extra)
    return record


def _apply_extra(record, extra):
    """Flags the caller already knows, folded in after the translation.

    The caller's flags come from keys the stroke spells after the glyph, so a case
    in `extra` beats the glyph's the way a second one does in a translation: Plover
    spends the last. Everything else is a statement about the same record from two
    places, so it is an or.
    """
    also = Record()
    also._parse_flags(list(extra))
    for name in ("attach_l", "ortho_l", "attach_r", "glue", "carry_cap", "word_end"):
        if getattr(also, name):
            setattr(record, name, True)
    for name in ("case", "space"):
        if getattr(also, name) is not None:
            setattr(record, name, getattr(also, name))


def _directive(d, record):
    # Attachment wraps everything else, so peel it first and read the core alone.
    core, attach_l, attach_r = _peel_attach(d)
    # `{^ing}` joins *and* applies orthography; `{^}`, `{^^}` and `{^a^}` only
    # close the gap. So the caret means the stronger thing exactly when it has text
    # and that text is a suffix rather than an infix.
    plain = core == "" or attach_r
    join = _Join(left=attach_l and plain, ortho=attach_l and not plain, right=attach_r)
    # With attachment peeled, the core is text. Plover matches its whole-atom metas
    # only on an atom that has no carets, so `{^*^}` is an asterisk and not the star
    # command. The exception is the carry-capitalisation prefix, which does compose
    # with attachment: it is how `{^~|'^}` writes an apostrophe inside a word.
    if (attach_l or attach_r) and core and not core.startswith("~|"):
        return [("text", core)], join
    return _core_items(d, core, record, join), join


def _set_case(record, want):
    """The last case a translation names is the one Plover spends."""
    record.case = want


def _set_space(record, want):
    if record.space is not None and record.space != want:
        raise Unsupported("spacing twice in one translation")
    record.space = want


def _punctuation(mark, stops, record, join):
    """A punctuation mark: joined to the left, and capitalising what follows when
    it ends a sentence."""
    join.left = True
    if stops:
        _set_case(record, "cap")
    return [("text", mark)]


def _core_items(d, core, record, join):
    if core == "":
        return []
    if core in ("-|", ">", "<"):
        _set_case(record, {"-|": "cap", ">": "lower", "<": "upper"}[core])
        return []
    if core in ("*!", "*?"):
        _set_space(record, "cancel" if core == "*!" else "retro")
        return []
    if core == "$":
        record.word_end = True
        return []
    # The punctuation short forms. Each one is an ordinary record: the mark, joined
    # to the left, and a capital after the ones that end a sentence.
    if core in (",", ":", ";"):
        return _punctuation(core, False, record, join)
    if core in (".", "?", "!"):
        return _punctuation(core, True, record, join)
    # The retro operations Plover spells with a leading star: the three the record
    # language cannot say any other way, because they edit a word already written.
    if core in ("*-|", "*<", "*>"):
        return [("meta", {"*-|": "retro.cap", "*<": "retro.upper",
                          "*>": "retro.lower"}[core], "")]
    # Operations on the stroke buffer, which Plover spells as macros.
    if core == "*+":
        return [("cmd", "engine.repeat_stroke", "")]
    if core == "*":
        return [("cmd", "engine.toggle_star", "")]

    # `{mode:…}` is persistent host state, so it is a command and not a flag.
    # Case-insensitive because Plover's meta names are, and `commands.json` writes
    # `{MODE:CAPS}`.
    if core[:5].lower() == "mode:":
        name, _, arg = core[5:].partition(":")
        name = "mode." + name.lower()
        if name not in COMMANDS:
            raise Unsupported("unsupported directive {%s}" % d)
        return [("cmd", name, arg)]
    # `{*($c)}`: the previous number as currency.
    if core.startswith("*(") and core.endswith(")"):
        return [("meta", "retro.currency", core[2:-1])]
    if core.startswith("="):
        return [("if_next",) + _if_next_fields(core[1:], d)]
    if core.startswith("&"):
        record.glue = True
        return [("text", core[1:])]
    if core.startswith("~|"):
        record.carry_cap = True
        return [("text", core[2:])] if core[2:] else []
    if core.startswith("#"):
        out = []
        for token in _held_keys(core[1:]):
            _keystrokes(token, [], out)
        return out
    if core[:7].lower() == "plover:":
        name, _, arg = core[7:].partition(":")
        name = _plugin_command(name.lower())
        if name not in COMMANDS:
            raise Unsupported("unsupported directive {%s}" % d)
        return [("cmd", name, arg)]
    # `{:name:arg}`, where a name starts with a letter. Without that guard,
    # typey-type's `{^:55}` reads as a meta called 55 rather than as text.
    if core.startswith(":") and core[1:2].isascii() and (core[1:2].isalpha()
                                                         or core[1:2] == "_"):
        name, _, arg = core[1:].partition(":")
        return _long_form(d, name, arg, record, join)
    # Anything left is a brace atom naming no operation, and Plover writes nothing
    # at all for one. Reading the core as text would be this module guessing, and
    # reproducing Plover's silence would be the other kind of guess.
    raise Unsupported("unsupported directive {%s}" % d)


def _plugin_command(name):
    """The dictionary-stack commands come from `plover-dict-commands` rather than
    from Plover, and they are about the stack, so they are named for what they do."""
    for ours, theirs in PLUGIN_COMMANDS.items():
        if theirs == name:
            return ours
    return "plover." + name


def _long_form(d, name, arg, record, join):
    """`{:name:arg}`, Plover's spelled-out form for most of the punctuation above."""
    if name == "attach":
        if not arg:
            join.left = join.right = True
            return []
        items, inner = _directive(arg, record)
        join.left, join.ortho, join.right = inner.left, inner.ortho, inner.right
        return items
    if name == "glue":
        record.glue = True
        return [("text", arg)]
    if name == "carry_capitalize":
        record.carry_cap = True
        return [("text", arg)] if arg else []
    if name == "word_end" and not arg:
        record.word_end = True
        return []
    if name in ("key_combo", "command", "mode"):
        prefix = {"key_combo": "#", "command": "PLOVER:", "mode": "mode:"}[name]
        items, inner = _directive(prefix + arg, record)
        join.left, join.ortho, join.right = inner.left, inner.ortho, inner.right
        return items
    if name == "case":
        if arg not in CASE_NAMES:
            raise Unsupported("unsupported directive {%s}" % d)
        _set_case(record, CASE_NAMES[arg])
        return []
    if name == "retro_case":
        if arg not in CASE_NAMES:
            raise Unsupported("unsupported directive {%s}" % d)
        return [("meta", {"cap": "retro.cap", "upper": "retro.upper",
                          "lower": "retro.lower"}[CASE_NAMES[arg]], "")]
    # `plover-semantic-suffixes`: inflect the previous word. Its clause rewrites,
    # `{:retro:past}` and the rest, are deliberately not here.
    if name == "infl":
        return [("meta", "infl", arg)]
    if name == "retro_currency":
        return [("meta", "retro.currency", arg)]
    # Both of these write a fixed punctuation mark, and the difference is only
    # whether a capital follows.
    if name in ("comma", "stop"):
        join.left = True
        if name == "stop":
            _set_case(record, "cap")
        return [("text", arg)]
    raise Unsupported("unsupported directive {%s}" % d)


class Alternative:
    """One way to read a fold's stroke: before, the keys off and on, after.

    `before` and `after` are Plover translations, and an empty one is "" rather
    than `{}`, because it writes nothing and is not a unit of its own.
    """

    def __init__(self, before, strip, add, after):
        self.before = before
        self.strip = frozenset(strip)
        self.add = frozenset(add)
        self.after = after

    def base(self, keys):
        """The keys to look up in place of a stroke's `keys`, or None."""
        keys = set(keys)
        if not self.strip <= keys:
            return None
        return frozenset((keys - self.strip) | self.add) or None


def parse_fold(symbols, names):
    """A fold record's alternatives, in order. `names` maps a tape symbol to the
    key it stands for."""
    if not symbols or symbols[0] != FOLD:
        raise Unsupported("a fold record starts with %s" % FOLD)
    alts, current = [], []
    for s in list(symbols[1:]) + [FOLD]:
        if s != FOLD:
            current.append(s)
            continue
        if STRIP not in current:
            raise Unsupported("a fold alternative has no %s" % STRIP)
        at = current.index(STRIP)
        end = current.index("[END]", at)
        keys = current[at + 1:end]
        strip, add = keys, []
        if ADD in keys:
            i = keys.index(ADD)
            strip, add = keys[:i], keys[i + 1:]
        try:
            strip = [names[k] for k in strip]
            add = [names[k] for k in add]
        except KeyError as e:
            raise Unsupported("%s is not a key" % e) from None
        alts.append(Alternative(_side(current[:at]), strip, add, _side(current[end + 1:])))
        current = []
    return alts


def _side(symbols):
    return Record.parse(symbols).translation() if symbols else ""
