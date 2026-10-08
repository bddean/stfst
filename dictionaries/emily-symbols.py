# Emily's Symbol Dictionary, mirrored to the left hand.
#
# Modified from https://github.com/EPLHREU/emily-symbols so the symbol chord
# is written with the LEFT hand, consistent with emily-modifiers (which
# fingerspells on the left and reserves the right hand for modifiers).
# Differences from upstream:
#   - the symbol pattern is stroked on STKPWHR instead of FRPBLG, using the
#     same left-hand chords as emily-modifiers' symbol table
#   - the unique identifier is # + U (number bar + right thumb) instead of
#     the SKWH starter. Rejected alternatives: ^ sits on the left-side
#     asterisk (fights the index over H/R), + belongs to the phrasing engine, #EU
#     collides with typey's reversed two-digit numbers (23EU -> 32).
#   - roles move off the identifier keys: variants on A/O (matching
#     emily-modifiers' symbol variants), attachments on -R (left) / -B
#     (right), capitalisation on -G (was *). -TS (repetition) is as upstream.
#     * is unused and rejected.
#   - four rows emily-modifiers lacks (media, volume, case, arrow glyphs) get
#     mirror-derived left chords: KWHR, WHR, TH, TPWH
#   - rows upstream lacks entirely, on free S+ chords: STR (Return et al.,
#     mirrored into emily-modifiers so shift-enter works) and four bs
#     block-sigil rows SPW/STK/SR/SKPR
# Symbols vs modifiers: this dict needs #+U and never L/Z; emily-modifiers
# needs the -LTZ ender and never #. The spaces are disjoint.
# The # key digit-converts number-mapped keys (#+TP arrives as "23"), so
# strokes are normalised back to letter form before parsing, as upstream does.
# Sits at the TOP of the stack; the one accepted shadow is typey's
# 40U -> {:}40 (' + O-variant). scripts/validate-emily.py tracks the list.
import re

attachmentMethod = "space"

LONGEST_KEY = 1

# variant format = ['', 'A', 'O', 'AO']
# if no variants exist, then a single string can be used for the symbol and the variant specifier keys will be valid but ignored
symbols = {
    # more computer function-y symbols
    "TR"    : ["{#Tab}", "{#BackSpace}", "{#Delete}", "{#Escape}"],
    # S extends TR's key family; Return is the headliner (shift-enter etc.
    # come from the mirrored row in emily-modifiers)
    "STR"   : ["{#Return}", "{#Insert}", "{#Menu}", "{#Print}"],
    "KPWR"  : ["{#Up}", "{#Left}", "{#Right}", "{#Down}"],
    "KPWHR" : ["{#Page_Up}", "{#Home}", "{#End}", "{#Page_Down}"],
    "KWHR"  : ["{#AudioPlay}", "{#AudioPrev}", "{#AudioNext}", "{#AudioStop}"],
    "WHR"   : ["{#AudioMute}", "{#AudioLowerVolume}", "{#AudioRaiseVolume}", "{#Eject}"],
    ""      : ["", "{*!}", "{*?}", "{#space}"],
    "TH"    : ["{*-|}", "{*<}", "{<}", "{*>}"],

    # typable symbols
    "HR"     : ["!", "¬", "↦", "¡"],
    "PH"     : ["\"", "“", "”", "„"],
    "TKHR"   : ["#", "©", "®", "™"],
    "KPWH"   : ["$", "¥", "€", "£"],
    "PWHR"   : ["%", "‰", "‱", "φ"],
    "SKP"    : ["&", "∩", "∧", "∈"],
    "H"      : ["'", "‘", "’", "‚"],
    "TPH"    : ["(", "[", "<", "\\{"],
    "KWR"    : [")", "]", ">", "\\}"],
    "T"      : ["*", "∏", "§", "×"],
    "K"      : ["+", "∑", "¶", "±"],
    "W"      : [",", "∪", "∨", "∉"],
    "TP"     : ["-", "−", "–", "—"],
    "R"      : [".", "•", "·", "…"],
    "WH"     : ["/", "⇒", "⇔", "÷"],
    "TK"     : [":", "∋", "∵", "∴"],
    "WR"     : [";", "∀", "∃", "∄"],
    "TKPW"   : ["=", "≡", "≈", "≠"],
    "TPW"    : ["?", "¿", "∝", "‽"],
    "TKPWHR" : ["@", "⊕", "⊗", "∅"],
    # a single literal backslash: Plover only unescapes \{ and \}, never \\,
    # so a two-char "\\\\" would type both characters (upstream bug)
    "PR"     : ["\\", "Δ", "√", "∞"],
    "KPR"    : ["^", "«", "»", "°"],
    "KW"     : ["_", "≤", "≥", "µ"],
    "P"      : ["`", "⊂", "⊃", "π"],
    "PW"     : ["|", "⊤", "⊥", "¦"],
    "TPWR"   : ["~", "⊆", "⊇", "˜"],
    "TPWH"   : ["↑", "←", "→", "↓"],

    # bs (block-sigil) glyphs on free S+ chords: S + b(ox), S + d(one),
    # S + the bullet chord R, S + the guillemet chord KPR
    "SPW"    : ["☐", "◧", "◫", "⊟"],
    "STK"    : ["☑", "☒", "✓", "✗"],
    "SR"     : ["◆", "◷", "↻", "◇"],
    "SKPR"   : ["◦", "‹", "›", "‣"]
}


def lookup(chord):

    stroke = chord[0]
    if len(chord) != 1:
        raise KeyError
    assert len(chord) <= LONGEST_KEY

    # normalise stroke from embedded numbers back to preceding-hash letter form
    if any(k in stroke for k in "0123456789"):
        numbers = ["O", "S", "T", "P", "H", "A", "F", "P", "L", "T"]
        stroke = "#" + "".join(
            numbers[int(k)] if k.isnumeric() else k for k in stroke)

    # the regex decomposes a stroke into the following groups/variables:
    # identifier              #  ...............  U
    # pattern                     STKPWHR
    # variants                             AO
    # attachments                                     RB
    # capitalisation                                        G
    # repetitions                                                TS
    #                                      (unused: ^ * E FPL DZ)
    match = re.fullmatch(r'(#?)([STKPWHR]*)([AO]*)(U?)([RB]*)(G?)([TS]*)', stroke)

    if match is None:
        raise KeyError
    (hashmark, pattern, variants, vowel, attachments, capitalisation, repetitions) = match.groups()

    # both parts of the identifier must be present
    if hashmark != "#" or vowel != "U":
        raise KeyError

    # calculate the attachment method, and remove attachment specifier keys
    attach = [(attachmentMethod == "space") ^ ('R' in attachments),
              (attachmentMethod == "space") ^ ('B' in attachments)]

    # detect if capitalisation is required, and remove specifier key
    capital = capitalisation == 'G'

    # calculate the variant number, and remove variant specifier keys
    variant = 0
    if 'A' in variants:
        variant = variant + 1
    if 'O' in variants:
        variant = variant + 2

    # calculate the repetition, and remove repetition specifier keys
    repeat = 1
    if 'S' in repetitions:
        repeat = repeat + 1
    if 'T' in repetitions:
        repeat = repeat + 2

    if pattern not in symbols:
        raise KeyError

    # repeating the bare empty entry is meaningless; rejecting it keeps the
    # "U9"-shaped strokes ("" + -T rep) clear of wm.py's focus-space 6-9
    if pattern == "" and variant == 0 and repeat > 1:
        raise KeyError

    # extract symbol entry from the 'symbols' dictionary, with variant specification if available
    selection = symbols[pattern]
    if type(selection) == list:
        selection = selection[variant]
    output = selection

    # repeat the symbol the specified number of times
    output = output * repeat

    # attachment space to either end of the symbol string to avoid escapement,
    # but prevent doing this for retrospective add/delete spaces, since it'll
    # mess with these macros
    if selection not in ["{*!}", "{*?}"]:
        output = " " + output + " "

    # add appropriate attachment as specified (again, prevent doing this
    # for retrospective add/delete spaces)
    if selection not in ["{*!}", "{*?}"]:
        if attach[0]:
            output = "{^}" + output
        if attach[1]:
            output = output + "{^}"

    # cancel out some formatting when using space attachment
    if attachmentMethod == "space":
        if not attach[0]:
            output = "{}" + output
        if not attach[1]:
            output = output + "{}"

    # apply capitalisation
    if capital:
        output = output + "{-|}"

    # all done :D
    return output
