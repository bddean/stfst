# The comb keyboard

An Android input method over the compiled dictionaries. Two thumbs, each
walking down its own honeycomb through its half of the steno order.

## What the gesture is

A chord is a rising run through the steno order, so one thumb tracing one bank
is a walk that only ever goes forward. The comb lays that walk out as fixed
cells and reads the thumb by **where it is**, never by where it went.

Touch down on the key you want. Then step, one cell at a time, down the chain.
Entering a cell takes what it yields; a cell steno order has passed is ground.
Lifting either thumb writes the stroke.

Nothing waits on time, distance or turning. A cell is a place, so a tap on one
is finished and there is no gate to tune.

## Why the keys stand still

The old keyboard read a slide by its direction, from a ring that reshaped
itself around the last key taken. That has a ceiling nothing can lift: twelve
keys follow the first of a bank, so those twelve directions share 360 degrees
and **30 degrees is the widest any of them can be**. Sharing the ring out
between the live keys reaches that ceiling exactly and cannot beat it.

A fixed cell pays in area instead of angle, and area the glass has plenty of.
What position reading needs in exchange is a rule with no gate in it, and
take-on-entry is that rule.

## The comb runs down

Flat-top hexes, with alternate columns hanging half a cell lower. So a column
is a run down the glass, and the chain of the steno order zigzags between two
of them: a key touches the next in the order diagonally, and the one after
that directly below.

Down, not across, for two reasons. A thumb sweeping toward its palm is the
short strong motion. And a comb that runs down can be full height, which is
what landscape wants.

## A layout is one value per cell

One character per cell, rows separated by a slash or a newline. A dot is open
ground, `o` is a hole, a space is off the board, and anything else is the key
of that name. The left bank ships as:

```
.#..
.^@.
.S+.
.KT.
.WP.
.RH.
.OA.
```

Every character touches the one above, below, left and right of it, plus one
diagonal, so the text is a picture of the keyboard. Edit it in the settings.

Everything else is recovered from that rectangle. There is no span, no size,
no order field and no portal table, because the grid already fixes each of
them:

| | |
|---|---|
| a melded button | a run of touching cells holding one value. That is all a big key is |
| two buttons, one key | cells holding one value that do not touch |
| the walk | the two sides facing keys steno order still allows |
| the portals | the other four sides, the ones holding passed keys included |
| a cell's ground | the field clipped by the bisector against every rival |

`Comb` answers all of it, and the drawing asks `Comb` rather than working the
shapes out a second way, so the picture cannot disagree with the decision.
`CombTest.a_tap_lands_inside_the_cell_it_is_answered_by` is that claim, over
three tilts.

## Four numbers, not fifty-two destinations

Steno order is enforced, so four of a cell's six sides can never be taken as
keys. All four are portals. If each carried a destination of its own there
would be about fifty-two of them to learn.

So a portal carries a **stride** instead, and the four strides are the same
for every key on the board. The `out` portal always means "jump three keys
ahead". Where that lands is derived:

- **Early in the order** there is open ground beside key `i+k` touching two or
  more keys still ahead. The focus lands there and takes nothing, and every one
  of those keys is then one hop away. Two hops, and a choice at the end of the
  first.
- **Late in the order** no ground is worth that. The jump lands on the key
  itself and takes it. One hop.

The sides take the strides in order of how far each turns from the way the walk
is heading, so a side means the same jump wherever the thumb is.

## The focus is not the thumb

A portal throws the point that reads the cells ahead of the thumb, and the
offset holds until the lift: the thumb goes on from where it is, the focus goes
on from where the jump put it. A hole does the same, throwing the focus
straight across the comb's middle.

Both are drawn. The focus is the filled cell; once the two have parted the
thumb is drawn too, faint, tied to the focus by a line. Holding still for
`Stall` brings the focus back, writing nothing.

## Landscape, and what Android will not give you

Each thumb takes a full-height side and the app keeps the whole screen between
them. The window covers the glass and the touchable region is cut back to the
two combs and the strip, so a tap in the middle reaches the app.

What is **not** available is a left or right inset. `onComputeInsets`
describes the app's room with a top edge alone, so the app lays out full width
and the combs cover its margins rather than pushing its content inward. Narrow
overlaying strips is what this buys, and it is the same trick the old floating
ring copy used. `Full-height halves in landscape` turns it off and returns to a
band at the bottom, which costs the comb most of its length.

## Tilt

A thumb sweeps along an arc, not a line, so the comb can be turned. `Tilt` is
degrees, portrait and landscape apart, and zero runs the columns straight down
the glass. It turns the lattice, which is one placement the drawing and the hit
test share; nothing else about the layout moves.

## Writing a stroke

Lifting either thumb writes, and **the thumb still down keeps its keys**.
Spelling a run of words that share a right-hand shape therefore costs one
right-hand motion, not one per word. `Write on the first lift` turns this off
and waits for both.

A lift writes only when a key has been taken since the last stroke. Keeping
keys and asking for a stroke are different things, and only the second is what
a lift can mean: otherwise the second thumb's own lift writes its held keys
again, and `Rr` comes out as `Rr` and then `r`.

A lift that took only the **armed keys** of its bank arms them for the next
stroke instead of writing; lifting on them again sends them alone. Two quick
taps add the side's double-tap key on top of the key under them, so tap tap on
`-T` releases as `-*T`.

## The buzzes

A buzz when a key is taken says a key was taken, which is a thing you can
already see. The buzz that matters is on the **change of cell under the
focus**, because that is what turns a walk into something to feel instead of
something to read.

The two thumbs get buzzes that feel different, and this is where the hardware
sets the limit. A phone has one vibration motor, so there is no left channel
and no right channel. What there is, in the order the code asks for it:

1. more than one motor, on the few devices that have them, addressed separately
2. composition primitives, where `PRIMITIVE_LOW_TICK` against `PRIMITIVE_TICK`
   feels like two pitches from the same motor
3. amplitude control, so weak against strong
4. none of those, so one pulse against two, because rhythm always survives

The settings screen prints which one this device got, under the switch that
asks for it.

## The bar

The steno keys write text and can ask for nothing else, so the bar carries what
the editor owns, right to left in reach order:

| | |
|---|---|
| the field's action | Go, Search, Send, Next, Done, or whatever the app calls it. A field with no action of its own gets a plain Enter |
| `⌫` | one character, by code point. Hold it and it repeats |
| `↶` | undo one stroke, which is not the same thing |
| `⌄` | put the keyboard away |
| `⚙` | the settings |

Any edit made through the bar gives up the tracked buffer. What reaches the
editor is the difference between the runtime's buffer and what was last sent,
so an edit made another way would leave that difference describing text nobody
has. The next stroke starts from the field as it now is.

## The hints bar

After every stroke the runtime reads the trailing one to four words backwards
through every dictionary that carries a `reverse.hfstol`, phrases included.
When an outline writes the same text in fewer strokes, `text=OUTLINE` joins a
rolling row between the keys and the bar: eight entries, oldest out first, gone
after a quiet minute and while it has nothing to say. The lookups run off the
stroke's thread, so the editor never waits on them.

## What the touch panel reports

`Draw the touched area` draws every sample of the contact at the size and shape
the driver gives it, and `Print the touch numbers` prints pressure, size and
the two contact axes. Whether a hard press can be told from a light one is a
question about the hardware, so it goes on the screen rather than being
guessed at. Both are off by default.

## Building

```sh
stfst compile recipe.toml     # the dictionaries
scripts/build-morph.sh        # optional; without it a suffix join is a plain join
android/sync-assets.sh        # copy both into app/src/main/assets
android/build-native.sh       # cross-compile libstfst.so
cd android && gradle :app:testDebugUnitTest :app:assembleRelease
```

The APK is debug-signed on purpose: it is a sideloaded prototype, and a real
signing key would have to live somewhere.

## Releasing

```sh
android/release.sh 0.13 "one line of what changed" [notes.md]
```

The GitHub release is the deployment: the phone installs from the release page,
so a build that only lands in `app/build/outputs` has not shipped. The script
tests, builds, and publishes tag `radial-v<version>` with the APK attached, and
refuses a dirty or unpushed tree.

`build-native.sh` wants an NDK, named by `ANDROID_NDK` or found at
`~/Library/Android/sdk/ndk/26.3.11579264`. The cross std comes from the flake,
which is why cargo runs inside `nix develop`.

## What runs where

| | |
|---|---|
| `Comb` | the layout, the lattice, and where a portal lands. No Android, no dictionary |
| `Chorder` | two thumbs, and the rule that turns them into strokes. No Android |
| `CombView` | drawing and touch. Decides nothing |
| `StenoService` | the editor, the insets, and the native handle |
| `stfst-jni` | opens the stack, hands strokes to `stfst-board`, returns the buffer |

`Comb` and `Chorder` are plain Kotlin so they can be tested without a device,
and the tests in `app/src/test` are the ones worth reading first.

## Not carried over

The long-press modifier ring went with the radial layout: it read a direction
from a mark, and there is no mark any more. A modifier picker that fits a comb
is its own piece of work.
