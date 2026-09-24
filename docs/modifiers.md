# Keystrokes, and the `@` namespace

`emily-modifiers.py` is the dictionary that turns the whole board into a keyboard:
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

The Python emits an unmodified printable keysym as text (`{^c^}`) and anything
else as a combo, because Plover cannot undo a combo. The reasoning survives the
port: a host can undo text and cannot undo a keystroke, so text is better wherever
both work, and with a modifier held the keystroke is the whole point.

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
plus 128 for `^` and 256 for `#`. xfst has no arithmetic, so this is either a
double-and-add cascade or a table of 512 rows, and the generator writes the table.

Enumerating a rule is usually the thing this format exists to avoid, so it is
worth being precise about why it is right here. The function is finite: nine bits,
512 pairs, and the whole of it fits in 15 kB of source. What the format exists to
avoid is enumerating a *product* that grows, which is §7's verb lexicon times its
subject map. Finiteness is the test, not whether the source looks like data.

The digit and F-key table is the same judgement, and it also removes three guards:
the low nibble is the digit, `T` with `P` makes it an F-key instead, `#` adds 16,
and every combination the guards reject simply has no row. That is the Python's
three `raise KeyError`s becoming the absence of a path.

## One glyph table, two dictionaries

Literal mode returns the glyph itself instead of the keysym that types it, and its
table is emily-symbols'. The Python shares it by importing `emily-symbols.py`; here
`emily-symbols-tables.xfst` is a file both grammars list, which is what the
recipe's multiple-source form is for. The `-tables` suffix marks transcribed data
with an upstream owner, and the grammars over it stay separate.

The sharing runs the other way for numbers. `emily-symbols` lists
`emily-modifiers-tables.xfst` for `LitNum`, so `SRUTZ` there is literal mode's
`@SR*U`: 9, glued. `O` appends a zero, which literal mode ignores.

## Where the Python stopped being the answer

`tests/golden/emily-modifiers.tsv` covers every table exhaustively: 39 keysym rows
in four variants, all 29 fingerspelling chords against all 32 modifier
combinations, all 128 payload chords as numbers, all 512 literal numbers. 3,238
cases. The expectations came from `emily-modifiers.py` while that dictionary was
still the definition, with two exceptions where it could not be.

**skhd.** When an `@` combo matches an skhd binding the Python shells out and
emits nothing, working around skhd not seeing the synthetic events Plover injects
on macOS. That is host-shaped, so skhd owns it, and the bindings were emptied
rather than letting a machine's `skhdrc` decide what a dictionary means.
`plover-skhd-relay` runs the command at the output seam instead.

**Plover's own escaping.** `_literal_symbol` builds its answer by concatenating
`{^}` onto the glyph, and two glyphs in the table are `\` and `{`, so
`\` + `{^}` reads back as a literal brace rather than a backslash and an attach.
The generator takes the glyph from the table and the three flag rules from the
function, which is the same rule without the ambiguity.

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
