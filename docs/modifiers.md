# Keystrokes, and the `@` namespace

`emily-modifiers` is the dictionary that turns the whole board into a keyboard:
`@` opens the namespace and the rest of the keys are a payload, read as a letter,
a symbol keysym, a digit, an F-key, a glyph or a binary number. Porting it needed
one change to the record language, and that change is the interesting part.

## A keystroke has modifiers, and the record language did not

§3's tag table has `[KEY]…[END]` for "a keystroke, not text", which is enough for
`Tab` and not enough for `ctrl+c`. The obvious fix is to put the modifiers in the
keysym text, the way Plover does (`{#control(shift(c))}`), and it is the wrong
one for two reasons.

The first is §3's own rule: open-ended values delimit, closed and small values
enumerate. There are five modifiers. The second is that a keysym can be any
character, `+` and `(` included, so any in-band syntax for modifiers needs an
escaping grammar for keysyms, which is the thing the record language exists to
remove.

So modifiers are five tags inside the item, in canonical order:

```
[KEY]c[CTRL][END]          [KEY]+[CTRL][ALT][END]
```

The order is steno order for the keys that set them (`-F -P -L -T -D`), so the
dictionary emits each one where it reads it, which is the same rule that put the
record's flags last.

The JSON importer gained this too, and it is not cosmetic there: 42 keystroke
tokens in `typey-type-full.json` are wrappers like `Control_L(Left)`, which used
to import as a keysym named `Control_L(Left)`. `Alt_L(Tab(Tab))` shows why a
wrapper is not always a modifier: a held non-modifier is that key pressed and
then the rest, so it becomes two items.

## Text or keystroke is a preference, not a case

An unmodified printable keysym is text, attached on both sides, and anything
else is a keystroke. A host can undo text and cannot undo a keystroke, so text
is better wherever both work, and with a modifier held the keystroke is the
whole point.

That makes it a priority union, and one define serves all three layers that need
it:

```
define Press(Name, Char)
    PU([ Char Say("[ATTACH_L]") Say("[ATTACH_R]") ],
       [ Say("[KEY]") Name [ ModTag* ] Say("[END]") ]) ;
```

`Char` is defined only where the keysym has a printable form, and only over a
selector with no modifier tags after it, so the subtraction inside `PU` says
"text where it is available and nothing is held" without a test for either.

## Enumerating a rule, where the rule is finite

The payload's binary reading is arithmetic: `R` is 1, `W` is 2, up to `T` at 64,
plus 128 for `^`, which only emily-symbols reads. xfst has no arithmetic, so
this is either a double-and-add cascade or a table of 256 rows, and the source
is the table. Here, with `^` or `+` held, `@` is `S`, a fold onto the stroke
with `S` in its place (`^@TP` is `^STP`), and that stroke's own folds apply.
`#` is phrasing's and is no bit.

Enumerating a rule is usually the thing this format exists to avoid, so it is
worth being precise about why it is right here. The function is finite: eight bits
and 256 pairs. What the format exists to avoid is enumerating a *product* that
grows, which is §7's verb lexicon times its subject map. Finiteness is the test, not whether the source looks like data.

The digit and F-key table is the same judgement, and its guards are absences:
the low nibble is the digit, `T` with `P` makes it an F-key instead, and a
combination the guards reject has no row.

## One glyph table, two dictionaries

Literal mode returns the glyph itself instead of the keysym that types it, and its
table is emily-symbols'. `emily-symbols-tables.xfst` is a file both grammars
list, which is what the recipe's multiple-source form is for. The `-tables`
suffix marks transcribed data with an upstream owner, and the grammars over it
stay separate.

The letters are shared the same way. `alphabet.xfst` holds each consonant's
chord, and fingerspelling, Shrimple's starters, the two-syllable stages and
the theory's generators all read it, so a letter that moves, moves everywhere.

The sharing runs the other way for numbers. `emily-symbols` lists
`emily-modifiers-tables.xfst` for `LitNum`, so `SRUTZ` there is literal mode's
`@SR*U`: 9, glued. `O` appends a zero, which literal mode ignores.

## Tests

`tests/golden/emily-modifiers.tsv` covers every table exhaustively: 39 keysym rows
in four variants, all 29 fingerspelling chords against all 32 modifier
combinations, all 128 payload chords as numbers, all 128 literal numbers. 2,607
cases.

A combo that skhd binds is still a keystroke. skhd does not see the events
Plover injects on macOS, so `plover-skhd-relay` runs the binding at the output
seam, and a machine's `skhdrc` does not decide what a dictionary means.

## Measured

| | |
|---|---|
| Artifact | 63,011 bytes forward, 59,635 reverse |
| Lookup | 3.8 µs median, 7.0 µs p99 |
| Frontier | 12.5 paths per step, 70 at the widest |

The frontier is wider than the other hand-written dictionaries because the five
layers overlap on their first keys: `@` and a left-bank chord could still become a
letter, a keysym, a glyph or a number, and the mode key that decides comes late.
Nothing is guessed about the *output*, though, which is what the wide-frontier
warning in `docs/latency.md` is about.
