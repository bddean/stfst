# The @-key keysym namespace. Built on emily-modifiers' spelling/symbol tables
# (https://github.com/EPLHREU/emily-modifiers) but retriggered onto the single
# left key @ (physical #2), replacing emily's # + -S / -LTZ enders. Spec and
# rationale in MODIFIERS-DESIGN.md.
#
# Left hand = payload; right top row = modifiers (F=ctrl P=shift L=super T=alt);
# right thumb E/U + * / -Z = mode. Bare vowel chords (E EU U) stay letters, so a
# layer engages only when its marker carries a payload:
#   @E + consonant chord -> symbol keysym      @U + digit bits -> number 0-9
#   @U + TP flag (+ #)   -> F-key F1-F24       @ + * or -Z      -> literal mode
# Combos matching an skhd binding run its command instead (see _combo).
import os
import re
import subprocess
import time
import importlib.util

LONGEST_KEY = 1

# fingerspelling: chord -> letter
spelling = {
        "A"     : "a",
        "PW"    : "b",
        "KR"    : "c",
        "TK"    : "d",
        "E"     : "e",
        "TP"    : "f",
        "TKPW"  : "g",
        "H"     : "h",
        "EU"    : "i",
        "AOEU"    : "i", # magnum
        "SKWR"  : "j",
        "SKWRAEU" : "j", # magnum
        "K"     : "k",
        "HR"    : "l",
        "PH"    : "m",
        "TPH"   : "n",
        "O"     : "o",
        "P"     : "p",
        "KW"    : "q",
        "R"     : "r",
        "S"     : "s",
        "T"     : "t",
        "U"     : "u",
        "SR"    : "v",
        "W"     : "w",
        "KP"    : "x",
        "KWR"   : "y",
        "STKPW" : "z",
        "STKPWHR" : "z", # magnum
        }

# symbol keysym names (compose with modifiers, unlike glyphs); variants on A/O
symbols = {
        "TR"    : ["Tab", "Delete", "BackSpace", "Escape"],
        "STR"   : ["Return", "Insert", "Menu", "Print"],
        "KPWR"  : ["Up", "Left", "Right", "Down"],
        "KPWHR" : ["Page_Up", "Home", "End", "Page_Down"],

        "HR"     : ["exclam", "", "notsign", "exclamdown"],
        "PH"     : ["quotedbl", "", "", ""],
        "TKHR"   : ["numbersign", "registered", "copyright", ""],
        "KPWH"   : ["dollar", "EuroSign", "yen", "sterling"],
        "PWHR"   : ["percent", "", "", ""],
        "SKP"    : ["ampersand", "", "", ""],
        "H"      : ["apostrophe", "", "", ""],
        "TPH"    : ["parenleft", "less", "bracketleft", "braceleft"],
        "KWR"    : ["parenright", "greater", "bracketright", "braceright"],
        "T"      : ["asterisk", "section", "", "multiply"],
        "K"      : ["plus", "paragraph", "", "plusminus"],
        "W"      : ["comma", "", "", ""],
        "TP"     : ["minus", "", "", ""],
        "R"      : ["period", "periodcentered", "", ""],
        "WH"     : ["slash", "", "", "division"],
        "TK"     : ["colon", "", "", ""],
        "WR"     : ["semicolon", "", "", ""],
        "TKPW"   : ["equal", "", "", ""],
        "TPW"    : ["question", "", "questiondown", ""],
        "TKPWHR" : ["at", "", "", ""],
        "PR"     : ["backslash", "", "", ""],
        "KPR"    : ["asciicircum", "guillemotleft", "guillemotright", "degree"],
        "KW"     : ["underscore", "", "", "mu"],
        "P"      : ["grave", "", "", ""],
        "PW"     : ["bar", "", "", "brokenbar"],
        "TPWR"   : ["asciitilde", "", "", ""],
        }

# glyph table for literal mode, shared with emily-symbols.py rather than copied
try:
    _es_path = os.path.join(os.path.dirname(__file__), "emily-symbols.py")
    _spec = importlib.util.spec_from_file_location("_emily_symbols_shared", _es_path)
    _es = importlib.util.module_from_spec(_spec)
    _spec.loader.exec_module(_es)
    glyphs = _es.symbols
except Exception:
    glyphs = {}

# left-hand binary, pinky-heavy per row (low nibble RWKS, high nibble HPT+)
BITS = {"R": 1, "W": 2, "K": 4, "S": 8, "H": 16, "P": 32, "T": 64}

# --- skhd interop ----------------------------------------------------------
# skhd binds shortcuts by intercepting the raw keyboard; it never sees the
# synthetic events Plover's {#...} combos emit. So whenever an @-combo matches
# an skhd binding, run that binding's command directly instead of emitting the
# (invisible-to-skhd) keystroke.
SKHD_PATH = os.path.expanduser("~/.config/skhd/skhdrc")

# Plover's .app has a bare PATH; give shelled-out commands the dirs the
# skhdrc actually needs (nix, homebrew, the user's scripts).
_SHELL_PATH = ":".join([
    "/run/current-system/sw/bin", "/opt/homebrew/bin", "/usr/local/bin",
    os.path.expanduser("~/.config/scripts"), os.environ.get("PATH", ""),
])

# skhd modifier vocabulary -> the five we model; l/r sides collapse together.
_MOD_ALIASES = {
    "cmd": "cmd", "lcmd": "cmd", "rcmd": "cmd",
    "alt": "alt", "lalt": "alt", "ralt": "alt", "opt": "alt",
    "ctrl": "ctrl", "lctrl": "ctrl", "rctrl": "ctrl", "control": "ctrl",
    "shift": "shift", "lshift": "shift", "rshift": "shift",
    "fn": "fn",
}
_MOD_GROUPS = {
    "hyper": {"cmd", "alt", "ctrl", "shift"},
    "meh": {"ctrl", "alt", "shift"},
}


def _norm_mods(tokens):
    out = set()
    for t in tokens:
        t = t.strip().lower()
        if not t:
            continue
        if t in _MOD_GROUPS:
            out |= _MOD_GROUPS[t]
        elif t in _MOD_ALIASES:
            out.add(_MOD_ALIASES[t])
        else:
            return None                 # unknown token: skip the binding
    return out


def _parse_skhd(text):
    bindings = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line[0] in "#.:":     # comment, blacklist, or mode decl
            continue
        if ":" not in line:
            continue
        bind, cmd = line.split(":", 1)        # first colon; commands may hold ':'
        bind, cmd = bind.strip(), cmd.strip()
        if not cmd or "-" not in bind:
            continue
        left, key = bind.rsplit("-", 1)       # key is the token after the last '-'
        key = key.strip().lower()
        mods = _norm_mods(left.split("+")) if left.strip() else set()
        if mods is None or not key:
            continue
        bindings[(frozenset(mods), key)] = cmd
    return bindings


# reparse only when the file changes, so editing skhdrc needs no Plover restart
_skhd_cache = {"mtime": None, "bindings": {}}


def _skhd_bindings():
    try:
        mtime = os.stat(SKHD_PATH).st_mtime
    except OSError:
        return {}
    if _skhd_cache["mtime"] != mtime:
        try:
            with open(SKHD_PATH) as f:
                _skhd_cache["bindings"] = _parse_skhd(f.read())
            _skhd_cache["mtime"] = mtime
        except OSError:
            return {}
    return _skhd_cache["bindings"]


# suppress a duplicate fire of the same command within one debounce window,
# in case the engine looks the stroke up more than once (non-idempotent
# commands like `open -n` or launchctl restarts must not double-run)
_last_fire = {"cmd": None, "t": 0.0}


def _spawn(cmd):
    now = time.monotonic()
    if _last_fire["cmd"] == cmd and now - _last_fire["t"] < 0.15:
        return
    _last_fire["cmd"], _last_fire["t"] = cmd, now
    env = dict(os.environ, PATH=_SHELL_PATH)
    subprocess.Popen(
        ["/bin/bash", "-c", cmd], env=env, cwd=os.path.expanduser("~"),
        stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL, start_new_session=True)


_PLOVER_TO_SKHD = {"control": "ctrl", "super": "cmd"}   # shift/alt/fn unchanged

# printable characters for the keysym names above; single-character keysyms
# (letters, digits) are their own character. Names absent here (Tab, Return,
# the arrows, F-keys) have no text form and stay key combos.
_KEYSYM_CHARS = {
    "exclam": "!", "quotedbl": '"', "numbersign": "#", "dollar": "$",
    "percent": "%", "ampersand": "&", "apostrophe": "'", "parenleft": "(",
    "parenright": ")", "asterisk": "*", "plus": "+", "comma": ",",
    "minus": "-", "period": ".", "slash": "/", "colon": ":",
    "semicolon": ";", "less": "<", "equal": "=", "greater": ">",
    "question": "?", "at": "@", "bracketleft": "[", "backslash": "\\",
    "bracketright": "]", "asciicircum": "^", "underscore": "_",
    "grave": "`", "braceleft": "{", "bar": "|", "braceright": "}",
    "asciitilde": "~",
    "exclamdown": "¡", "sterling": "£", "yen": "¥", "brokenbar": "¦",
    "section": "§", "copyright": "©", "guillemotleft": "«", "notsign": "¬",
    "registered": "®", "degree": "°", "plusminus": "±", "mu": "µ",
    "paragraph": "¶", "periodcentered": "·", "guillemotright": "»",
    "questiondown": "¿", "multiply": "×", "division": "÷", "EuroSign": "€",
}


def _text(keysym):
    char = keysym if len(keysym) == 1 else _KEYSYM_CHARS.get(keysym)
    if char is None:
        return None
    char = char.replace("{", "\\{").replace("}", "\\}")
    return "{^" + char + "^}"       # attached both sides: a keypress, undoable


def _combo(keysym, mods):
    norm = frozenset(_PLOVER_TO_SKHD.get(m, m) for m in mods)
    cmd = _skhd_bindings().get((norm, keysym.lower()))
    if cmd is not None:
        _spawn(cmd)
        return "{#}"                          # skhd handles it; emit nothing
    # Plover cannot undo a {#...} combo, so emit unmodified printable keysyms
    # as text instead; with a modifier held the combo is the whole point.
    if not mods:
        text = _text(keysym)
        if text is not None:
            return text
    for m in mods:
        keysym = m + "(" + keysym + ")"
    return "{#" + keysym + "}"


def _literal_symbol(cons, variant, modkeys):
    # * / -Z takes the top row off modifier duty: F/P attach, L capitalises
    entry = glyphs.get(cons)
    glyph = entry[variant] if isinstance(entry, list) else entry
    if not glyph:
        raise KeyError
    if "F" in modkeys:
        glyph = "{^}" + glyph
    if "P" in modkeys:
        glyph = glyph + "{^}"
    if "L" in modkeys:
        glyph = glyph + "{-|}"
    return glyph


def lookup(chord):
    if len(chord) != 1:
        raise KeyError
    stroke = chord[0]

    # a # in the stroke digit-converts number-mapped keys; restore letter form
    if any(k in stroke for k in "0123456789"):
        numbers = ["O", "S", "T", "P", "H", "A", "F", "P", "L", "T"]
        stroke = "#" + "".join(
            numbers[int(k)] if k.isnumeric() else k for k in stroke)

    # `+` is the phrasing namespace (PHRASING-DESIGN.md): a stroke holding it is never
    # ours, which is what lets `@` mean emphatic do inside a +chord.
    m = re.fullmatch(
        r'(#?)@(\^?)([STKPWHR]*)([AO]*)([*-]*)([EU]*)([FPLTD]*)(Z?)', stroke)
    if m is None:
        raise KeyError
    hashmark, caret, cons, ao, starhyph, eu, modkeys, zed = m.groups()
    literal = "*" in starhyph or zed == "Z"
    # `^` has exactly one role here: the 128 bit of a literal number.
    if caret and not (literal and "U" in eu and "E" not in eu):
        raise KeyError

    mods = []
    if "F" in modkeys: mods.append("control")
    if "P" in modkeys: mods.append("shift")
    if "L" in modkeys: mods.append("super")
    if "T" in modkeys: mods.append("alt")
    if "D" in modkeys: mods.append("fn")   # honored only if the OS synthesises fn

    variant = ("A" in ao) + 2 * ("O" in ao)

    # symbols: @E + consonant chord
    if "E" in eu and "U" not in eu and cons:
        if literal:
            return _literal_symbol(cons, variant, modkeys)
        entry = symbols.get(cons)
        if entry is None:
            raise KeyError
        name = entry[variant] if isinstance(entry, list) else entry
        if not name:
            raise KeyError
        return _combo(name, mods)

    # numbers / function keys: @U + payload
    if "U" in eu and "E" not in eu:
        if literal:
            value = sum(v for k, v in BITS.items() if k in cons)
            value += 128 if caret else 0
            value += 256 if hashmark else 0
            return str(value)
        bits = sum(v for k, v in BITS.items() if k in cons and v <= 8)
        if "T" in cons and "P" in cons:              # F-key flag
            n = bits + (16 if hashmark else 0)
            if not 1 <= n <= 24:
                raise KeyError
            return _combo("F%d" % n, mods)
        if bits:
            if bits > 9:
                raise KeyError
            return _combo(str(bits), mods)
        if "O" in ao:                                # explicit zero (O mnemonic)
            return _combo("0", mods)
        # bare @U keeps no payload -> falls through to the letter u, so @U + a
        # modifier is ctrl+u etc.; zero must stay on @OU to leave u reachable

    # commands: @EU + payload -- reserved (MODIFIERS-DESIGN.md)
    if "E" in eu and "U" in eu and (cons or ao):
        raise KeyError

    # letters: fingerspell, including the bare E / EU / U vowels
    letter = spelling.get(cons + ao + eu)
    if not letter:
        raise KeyError
    return _combo(letter, mods)
