# Window-manager control as a Plover dictionary.
#
# A stroke runs a WM command as a side effect of lookup and produces no text,
# so it never goes through synthesized keystrokes. That sidesteps the macOS
# event-tap problem: Plover injects key events at the session tap, but skhd
# listens at the HID tap (upstream), so it never sees Plover's Alt_L+key --
# the key just reaches the app as a literal glyph. Talking to the WM directly
# is both more reliable and faster.
#
# Scheme (memorable + consistent):
#   NAVIGATE  -- everything under the Stenotype Extended ^ key (left index),
#               operand on the right hand. The vowel picks the verb: U =
#               focus/go-to, E = send the active window there. (EU is left
#               free for a future third verb.) ^ strokes can collide with
#               nothing lexical, and the # forms this replaces mostly never
#               fired: the number bar digit-converts strokes (#H*U arrived
#               as "4*U"). Frees # for emily-symbols.
#     ^U/^E + -RBGS   space 1..10, binary emily-modifiers style:
#                     R=1 B=2 G=4 S=8  (space 5 focus = ^URG)
#     ^U/^E + -FPLT   window west south north east: vim hjkl transplanted
#                     onto the right top row in finger order
#     ^U/^E + -D      display (D = display)
#
#   ACT  -- distinctive phonetic stroke + *. EU = the alt variant where one
#           exists (gaps: toggle vs zero).
#     PW*S  bsp      STKA* stack     SPHR*EUT toggle-split    PWA*L balance
#     TP*ULG fullscreen   PA*D gaps-toggle   PA*EUD gaps-off
#     KHRO*ES close-window     STA*RGT restart-yabai
#
#   LAUNCH -- OS/machine apps, independent of the WM (swap these per machine).
#     T*ERPL terminal   KWAO*UT browser   KWAO*EUT browser-work
#     PR*EFS system-prefs   SHR*URP colorslurp   TP*EULT color-filters
#
# Keys must be canonical rtfcre -- Plover passes machine strokes verbatim, and
# in canonical order * sits between the vowel banks (STKA*, not STAK*). A
# mnemonic spelling never matches; the guard below the table enforces this.
#
# To target a different WM later, implement a Backend subclass and set BACKEND.
# Launchers live outside the backend because they are OS-specific, not WM-specific.

import os
import subprocess
import time

LONGEST_KEY = 1


class Backend:
    """Window-manager operations, each returning a shell command string.

    Returning a string (not argv) keeps the yabai `||` fallbacks and `;`
    sequencing readable and lets one backend express what another can't.
    """

    def focus_space(self, n):          raise NotImplementedError
    def carry_to_space(self, n):       raise NotImplementedError
    def focus(self, direction):        raise NotImplementedError  # west/east/north/south
    def swap(self, direction):         raise NotImplementedError
    def focus_display_next(self):      raise NotImplementedError
    def carry_to_display_next(self):   raise NotImplementedError
    def layout(self, name):            raise NotImplementedError  # bsp/stack
    def toggle_split(self):            raise NotImplementedError
    def balance(self):                 raise NotImplementedError
    def toggle_fullscreen(self):       raise NotImplementedError
    def gaps(self, off=False):         raise NotImplementedError
    def close_window(self):            raise NotImplementedError
    def restart(self):                 raise NotImplementedError


class Yabai(Backend):
    # Up/down focus prefers the current stack, then the geometric neighbour --
    # matching the skhd bindings this replaces.
    _STACK = {"north": "prev", "south": "next"}

    def focus_space(self, n):        return f"yabai -m space --focus {n}"
    def carry_to_space(self, n):     return f"yabai -m window --space {n}"

    def focus(self, d):
        s = self._STACK.get(d)
        return (f"yabai -m window --focus stack.{s} || yabai -m window --focus {d}"
                if s else f"yabai -m window --focus {d}")

    def swap(self, d):
        s = self._STACK.get(d)
        return (f"yabai -m window --swap stack.{s} || yabai -m window --swap {d}"
                if s else f"yabai -m window --swap {d}")

    def focus_display_next(self):
        return "yabai -m display --focus next || yabai -m display --focus first"

    def carry_to_display_next(self):
        return ("yabai -m window --display next || yabai -m window --display first ; "
                + self.focus_display_next())

    def layout(self, name):          return f"yabai -m space --layout {name}"
    def toggle_split(self):          return "yabai -m window --toggle split"
    def balance(self):               return "yabai -m space --balance"
    def toggle_fullscreen(self):     return "~/.config/scripts/yabai-toggle-fullscreen"
    def gaps(self, off=False):       return "yabai-pad 0" if off else "~/.config/scripts/yabai-pad"
    def close_window(self):          return "~/.config/scripts/skhd-close-window"
    def restart(self):
        return "launchctl stop org.nixos.yabai ; launchctl start org.nixos.yabai"


BACKEND = Yabai()


# --- bindings ---------------------------------------------------------------
# An action is a function (Backend) -> shell command string. `wm` dispatches to
# the active backend; `sh` is a backend-independent OS command.

def wm(method, *args):
    return lambda b: getattr(b, method)(*args)

def sh(command):
    return lambda b: command


# U = focus/go-to, E = send the active window there. EU is reserved.
_FOCUS, _SEND = "U", "E"

# vim hjkl in finger order on the right top row, under the ^ marker.
_DIRS = {"F": "west", "P": "south", "L": "north", "T": "east"}

# space number in binary on the right bottom row, emily-modifiers style
_SPACE_BITS = (("R", 1), ("B", 2), ("G", 4), ("S", 8))

def _space_chord(n):
    return "".join(key for key, bit in _SPACE_BITS if n & bit)

BINDINGS = {
    # displays
    "^" + _FOCUS + "D": wm("focus_display_next"),
    "^" + _SEND + "D":  wm("carry_to_display_next"),
    # space / window actions
    "PW*S":      wm("layout", "bsp"),
    "STKA*":     wm("layout", "stack"),
    "SPHR*EUT":  wm("toggle_split"),
    "PWA*L":     wm("balance"),
    "TP*ULG":    wm("toggle_fullscreen"),
    "PA*D":      wm("gaps"),
    "PA*EUD":    wm("gaps", True),
    "KHRO*ES":   wm("close_window"),
    "STA*RGT":   wm("restart"),
    # launchers (OS-specific, not WM)
    "T*ERPL":    sh('open -n "/Applications/Ghostty.app"'),
    "KWAO*UT":   sh("~/.config/scripts/qb_in_instance"),
    "KWAO*EUT":  sh("~/.config/scripts/qb_in_instance work"),
    "PR*EFS":    sh('open "x-apple.systempreferences:"'),
    "SHR*URP":   sh("/Applications/ColorSlurp.app/Contents/MacOS/ColorSlurp"),
    "TP*EULT":   sh("~/.config/scripts/toggle-color-filters"),
}

for _key, _dir in _DIRS.items():
    BINDINGS["^" + _FOCUS + _key] = wm("focus", _dir)
    BINDINGS["^" + _SEND + _key] = wm("swap", _dir)

for _n in range(1, 11):
    BINDINGS["^" + _FOCUS + _space_chord(_n)] = wm("focus_space", _n)
    BINDINGS["^" + _SEND + _space_chord(_n)] = wm("carry_to_space", _n)

# A non-canonical key would never match a machine stroke; fail loudly at load.
try:
    from plover.steno import normalize_steno
    for _k in BINDINGS:
        try:
            _norm = normalize_steno(_k)
        except ValueError:
            continue  # not strokable in the active system (^ outside Extended)
        if _norm != (_k,):
            raise ValueError(f"non-canonical steno key: {_k!r} -> {_norm}")
except ImportError:
    pass  # running outside Plover


# --- execution --------------------------------------------------------------
# Finder-launched Plover has a bare PATH; give commands the dirs they need.
_ENV = {**os.environ, "PATH": ":".join([
    "/run/current-system/sw/bin", "/opt/homebrew/bin", "/usr/local/bin",
    "/usr/bin", "/bin", "/usr/sbin", "/sbin", os.environ.get("PATH", ""),
])}

# Plover may resolve a stroke more than once around a single edit; toggles
# (fullscreen, gaps) would fire twice. Drop an identical stroke seen within a
# beat. Idempotent commands (focus, layout) don't care either way.
_DEBOUNCE_S = 0.2
_last = {"stroke": None, "t": 0.0}


def _run(command):
    subprocess.Popen(["/bin/bash", "-c", command], env=_ENV,
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                     start_new_session=True)


def lookup(key):
    stroke = key[0]
    action = BINDINGS.get(stroke)
    if action is None:
        raise KeyError
    now = time.monotonic()
    if not (stroke == _last["stroke"] and now - _last["t"] < _DEBOUNCE_S):
        _run(action(BACKEND))
        _last["stroke"], _last["t"] = stroke, now
    # Empty combo = no output. Not "": the formatter tests `if t.english:`,
    # so a falsy translation falls into the raw-steno branch and types "^UF".
    return "{#}"
