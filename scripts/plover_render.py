"""One translation through Plover's own formatter, between "run" and "y".

Both boundaries show, so a difference in attachment or in spacing is visible. The
checkers judge by what lands in the document and not by the spelling that got it
there, and deciding that is Plover's job rather than ours.

Needs Plover's own python, which is what makes it the oracle.
"""


def renderer(combo=None, command=None):
    """A function from a Plover translation to what it writes.

    `combo` and `command` say how a keystroke and an engine command appear.
    Absent, they appear as nothing, which is right when both sides of a
    comparison write the same one and only the text is in question.
    """
    import plover.formatting as f
    from plover.steno import Stroke
    from plover.translation import Translation

    def render(text):
        buf = [""]

        def add(s):
            buf[0] += s

        fm = f.Formatter()
        fm.set_output(type("O", (), {
            "send_backspaces": lambda s, n: buf.__setitem__(0, buf[0][:len(buf[0]) - n]),
            "send_string": lambda s, x: add(x),
            "send_key_combination": lambda s, x: combo and add(combo(x)),
            "send_engine_command": lambda s, x: command and add(command(x)),
        })())
        seen = []
        try:
            for t in ["run", text, "y"]:
                tr = Translation([Stroke.from_steno("TEFT")], t)
                fm.format([], [tr], seen)
                seen.append(tr)
        except Exception as e:
            # A translation Plover cannot format is one it cannot write either, so
            # the failure is the answer and both sides have to reach the same one.
            return "!! %s" % e
        return buf[0]

    return render
