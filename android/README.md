# The ring keyboard

An Android input method over the compiled dictionaries. Two thumbs, each
walking its own set of nested rings through its half of the steno order.

## The rule

**Entering a live cell takes it.** Everything else on the board is ground.

A cell is live while steno order still allows its key. It is ground once the
order has passed it, when it holds no key, and when it is the hub and you have
left it. A cell holding a key already in hand is the one exception, and it flips
its ring's modifier.

Touch down on the key you want, slide through the cells you want, and lift.
Lifting either thumb writes the stroke. Nothing waits on time, distance or
turning, so there is no threshold to tune, and the rules that look like extra
rules are consequences of the one:

- sliding out to the next ring "erases" the ring behind, because steno order
  killed those keys and dead cells are ground;
- turning through a ring adds keys, because you cross them;
- the shortcut is the middle. Two cells far apart on one ring are joined by a
  straight line through the hub, and the hub is ground.

A tap is aimed before the thumb lands, so one that falls short takes the key it
was aimed at rather than nothing. Only the corners outside the rings are bare,
which is what leaves a double tap and a long press somewhere to be made with no
key under them.

A flat board needs portals to reach a key many cells ahead in one motion. A
ring has a middle, so the portals, the strides, the holes and the detached
focus all went with the comb.

## Why the keys stand still

The oldest keyboard here read a slide by its direction, from a ring that
reshaped itself around the last key taken. That has a ceiling nothing can lift:
twelve keys follow the first of a bank, so those twelve directions share 360
degrees and **30 degrees is the widest any of them can be**.

A fixed cell pays in area instead of angle, and area the glass has plenty of.
What position reading needs in exchange is a rule with no gate in it, and
take-on-entry is that rule.

## The layout

```
LEFT                                RIGHT
hub    S | ST | T                   hub    E | EU | U        (tap only)
ring 1 P H R W K                    ring 1 F R B G L P
ring 2 + A O, five times            ring 2 dz T S, six times
row    ^ #                          row    * @ -D -Z
```

Ring 2 holds one group per key of ring 1, so **every key of ring 1 sees the same
three angles**: its stud straight out from it, then the group's two keys a third
and two thirds of a slot on round the ring. Wherever the thumb is on ring 1, the
motion to `-T` is the same, and `-S` is the same again.

The hub is a pie and fills its circle. A ring key is a body rather than a slice
of its ring: one width for every ring, drawn along the middle of its slot with
rounded ends, and everything around it is ground. That ground is what a slide
passes through on its way to a further key, and it is the width, the length and
the taper of the key that set how much of it there is. All three are settings.

Five facts about the shape, the first four measured against 340k strokes of
`typey-type-full.json`:

| | |
|---|---|
| the ring order is the steno board folded | the two rows joined at both ends. `T P H R W K` beats every other cyclic order of the left consonants, and `F R B G L P` does the same on the right |
| the hub is a pie of complete answers | landing takes the whole choice, so the hub is ground from then on and you can leave at any angle. `S T` in the left hub is worth 3.6 points over `S` alone, and the ring drops to five cells at 72° |
| a key may hold several cells | the vowels repeat round their ring, so one is always within reach of wherever the thumb left ring 1 |
| the pinky is a mode, not a ring | `-D` is `-T` upgraded and `-Z` is `-S` upgraded, and the pinky presses them one at a time. `-TS` is 2351 strokes and `-DZ` is 1466, while `-TZ` is 26 and `-SD` is 3, so one bit over the stroke buys nearly all of it, and the row carries a plain `-D` and `-Z` for the rest |
| a key no walk can spend is a stud | a circle rather than a body along the ring, standing a hair further out, one between each group of repeats. So `-T` and `-S` stay next to each other and the thumb never has to pass through the stud to get from one to the other |

## The modifiers stand in a row

Steno order cannot kill a modifier, so a modifier is a key no walk can spend —
and a key no walk can spend is a key no walk should have to reach. Most of them
are off the rings, in a row under them: `^ #` on the left, `* @ -D -Z` on the
right. A tap arms one for the next stroke; holding it puts it in the stroke in
hand, the way any cell does. `+` is the exception: it is a float, so it can sit
on ring 2 among keys it can never spend.

## A cell already taken flips its ring

Entering a cell whose key is **already in hand** takes nothing, because a key
comes once. That entry is therefore free to mean something else, and what it
means is a modifier: each ring names one key, and entering a spent cell of that
ring flips it in or out of the stroke in hand.

```
ring 1  # on the left, @ on the right
ring 2  ^ on the left, * on the right
```

So a thumb out on ring 2 that turns back onto the vowel it just took flips `^`,
and one that pulls in onto the ring 1 key it started from flips `#`. Those are
the turn back and the pull in, and they are no longer gestures: nothing measures
how far the turn went or which way it came, because entering a cell is entering
a cell. A second entry flips it back, which is what the upgrade's stud already
did.

A cell **steno order merely passed** stays ground. A chord turns back over those
all the time to reach a key the ring keeps on the far side, and that route is
most of what the ring order buys. The cell a thumb has been through is the one
it can mean something by entering again.

The board says so: a spent cell is labelled with the key it flips rather than
with its own key struck out, and it is drawn held once that key is in hand.

Two older shortcuts remain. **Two quick taps** add the side's double-tap key, so
tap tap on `-T` releases as `-*T`. **Standing still** adds the side's long-press
key, which is off by default now that a spent cell reaches the same modifiers
without waiting.

## The upgrade

`dz` is not a key. It is a switch, and it stands in the studs of ring 2, one
between each `-T` and the `-S` before it. While it is on, a `-T` the thumb takes
writes `-D` and a `-S` writes `-Z`, and the board says so by relabelling those
cells. The pinky writes those keys one at a time, so a mode expresses it more
cheaply than a ring of its own.

Entering a stud turns it over, so what a key writes is the mode it was taken
under, and a stroke can hold one lifted key and one plain one:

| | |
|---|---|
| `dz`, `-T` | `-D` |
| `dz`, `-T`, `-S` | `-DZ` |
| `dz`, `-T`, `dz`, `-S` | `-DS` |

A tap on a stud alone has nothing to lift, so it writes both keys: `-DZ` is a
stroke worth a cell of its own. The row keeps a plain `-D` and `-Z` for the
strokes that want a key and its upgrade at once.

The switch is drawn as a ring inside the stud, because it is the one cell an
entry turns over rather than spends.

## A layout is one value per cell

The hub first, then the rings outward, separated by spaces. A slot is one
character, or several keys between slashes, and a dot is ground. The left bank
ships as:

```
S/ST/T PHRWK +AO+AO+AO+AO+AO
```

Edit it in the settings, and the row beside it. Everything else is recovered
from that text:

| | |
|---|---|
| the walk | steno order over the keys the text names |
| a float | a key steno order cannot kill, so no walk can spend it: the row's keys, and the ones `The floats` names |
| a duplicate | the same key in two slots. Nothing special: a key is a set of cells |
| a spoke | every angle a ring marks out, each slot's middle and each boundary's |
| the hub is tap-only | it is level 0. Landing on a slice takes the slice; entering it from a ring takes nothing |
| a spent cell | one holding a key already in hand. Entering it flips its ring's own key, which `The spent cells` names |
| a stud | a slot no walk can spend: a float, or the upgrade, which `>` names. Drawn as a circle a hair outside its band, so a thumb turning along the ring passes under it |
| the turn | a ring holding a whole number of groups per key of ring 1 takes ring 1's angles; one that does not divide is staggered half a slot instead |

## The geometry

One formula for the drawing and for the hit test. A bank is a square; in its
normalised frame the rings are superellipses.

```kotlin
// u = (x - cx) / a, v = (y - cy) / a
p  = pHub + (pRim - pHub) * r                    // 2.6 in the middle, 4.5 at the rim
r  = (|u|^p + |v|^p)^(1/p)                       // which ring
θ  = atan2(v, u)                                 // where on it
// and back, for drawing and for a cell's centre:
k  = r / (|cos θ|^p + |sin θ|^p)^(1/p)
x  = cx + a*k*cos θ ;  y = cy + a*k*sin θ
```

`p` interpolates circle to rounded rectangle, and it rises outward: the middle
stays round, where a thumb turns, and the outer rings take the corners of the
bank, where the room is. So a ring is wider the further out it sits, which is
what the outer keys need. The exponent depends on the answer, so reading a
touch back repeats the question until it lands on its own exponent.

The bank is square because a thumb sweeps an arc: a ring stretched to a tall
bank would answer the same sweep with different keys at different angles. The
keyboard therefore takes only the depth a square bank and its row need, which
is the depth the wider of the two banks can use.

A thumb's field is its share of the width, and the bank is a square inside it,
held against the thumb's own edge and against the floor. So an uneven split
leaves its slack between the two banks rather than round the wider one: each
thumb reaches the edge it starts from. `Ground round a bank` keeps ground
outside the square as well, and `Bank pulled off its edge` moves the bank in
towards the middle.

A key is the set of places within half its width of the arc through the middle
of its slot, which is a capsule, and a circle when the arc has no length. The
width is divided by the stretch at the key's own angle, so a key in a corner of
the bank is the same size as one on an axis rather than a stretched one.

Radii are fractions of `r = 1`, outward: `0 .34 .67 1` on both banks, and they
are settings, because the radial budget is the tightest thing here. Model
degrees run from straight up in the bank's own direction: the right bank's
angles run the other way, so both thumbs sweep mirrored arcs and "against the
walk" is the same motion for each hand.

`Ring` answers all of it, and the drawing asks `Ring` rather than working the
shapes out a second way, so the picture cannot disagree with the decision.
`RingTest.a_tap_lands_inside_the_cell_it_is_answered_by` is that claim.

## What a chord costs

A chord is **one motion** when every step is an arc along a ring or a run
straight out. It needs a **hub crossing** when two keys sit too far apart on
one ring to reach without taking a key you do not want, and a crossing is a
second motion, not a failure.

| | |
|---|---|
| left, `P H R W K`, `S T` in the hub | 89.7% |
| right ring 1, `F R B G L P` | 91.9% |
| right pinky combinations | 100%: the upgrade costs no motion at all |

Both shares are what the modifier on ring 1 costs. A cell holding a key in hand
flips rather than being ground, so a route may not go back over one, and that is
worth 3.6 points on the left and 5.3 on the right against a ring 1 that flips
nothing. Ring 2 is free, being the end of the walk. Set `The spent cells` to
`. . ^` to have the motions back and reach `#` through the row.

`RingTest.the_order_is_the_one_measured` recomputes those shares from the
checked-in corpus and fails if any other arrangement of the same keys round the
same ring writes more of it in one motion. The heaviest of the residual is `PR
TKPW KR KH KHR` on the left and `PBLG RPB RL RG FPB` on the right.

`Ring.path` is where the claim lives: the stops a thumb makes to write a chord,
found by searching the spokes for a route that crosses nothing live the chord
does not want. `RingTest.every_chord_is_a_path_a_thumb_can_make` walks every
one of them through `Chorder` as thumb motion, sampling finely enough that no
cell is stepped over, and fails where the keys that come out are not the chord.

## Three kinds of key

The board has no hue. It is white and near-white, close enough that a dither
laid on it reads as a tone rather than as noise, and what a key is shows in the
texture of its face: a fine blue-noise dither for the vowels, a coarse one for
a modifier, a plain face for the consonants, which are the many. Which is which
is read off the glyph, so a key that spells nothing is a modifier and a layout
that moves one does not have to say so twice. The row has no dither, because
being the row already says what its keys are. The two spacings are settings,
and the dot follows the spacing, because a dither whose dots outgrow their gaps
is a fill.

`Theme` is the two colours: light, dark, or whatever the phone is set to.

The dither thins out at the cell's rim and around its label, because a texture
running under a letter is what makes the letter hard to read. What the fade
changes is how many dots there are, not how big they are.

Across that, every cell is in one of three states, because they are three
different answers:

| | |
|---|---|
| in the chord | the face and the ink swap, and the dither with them, so the kind of key still reads |
| still takeable | the plain face, and a thin outline |
| spent by steno order | the face sinks towards the ground and the label is struck through |

A key armed for the next stroke is drawn as one in the chord, with its label
underlined. `Show cells already passed` hides the spent ones.

## What the stroke would write

The keys in hand are printed above each bank, and under them **what they would
write if a thumb lifted now**.

The runtime is one mutable board, so the only way to ask is to make the stroke
and take it back. Nothing reaches the editor and the buffer ends where it
started. A stroke that means undo is answered by name instead, because asking
what an undo writes would perform one. A match that supersedes what earlier
strokes wrote says so with a `⌫`.

It runs off the drawing's thread, on the same one the reverse lookups use, and
the answer is kept with the chord it is about. So the last chord's text is
never left sitting over this one.

## One thumb, both banks

One hand writes a whole stroke, and `Crossing the middle` is how.

**Swap** turns the board round rather than making the thumb travel. A touch that
crosses the middle ends there: it writes nothing, the keys it took stay in hand,
and the two halves of the glass change places, so the next touch — in the same
half, where the thumb already is — walks the other bank. Writing the stroke puts
the halves back. What a stroke costs one hand is therefore two touches and a
flick past the middle, not a reach across the glass.

**Carry** hands the touch itself over, so the thumb crosses into the other
field and keeps sliding, and the bank it leaves keeps what it took.

Either way it happens only while the other thumb is up, so two hands never meet
it.

## The preview over the app

Shrink the keys and a thumb crosses cells in less travel, which is faster. What
it costs is legibility, and `Preview over the app` buys that back: a copy of
both banks, drawn above the keys, over the app.

`onComputeInsets` is what makes that work. The keys are what the app lays out
around and what the window takes touches for; the band above them is neither.
So the app keeps its room and its touches up there, and the copy floats on top.

It fades in as a thumb goes down and out as it lifts, because a band that
arrives at full strength reads as a flash over whatever the app was showing.
The thumb is drawn in the band as well: it is a place on the rings rather than
a place on the glass, so the band reads it in its own field. The settings
preview gets a band too, which is where to look at one without an app under it.

## Landscape, and what Android will not give you

A deep band, with a bank hugging each edge. The gap between them holds the bar
and the record. The app keeps everything above the band and nothing covers it.

Full-height halves with the app between them is what the keyboard wants, and
Android will not give it. `onComputeInsets` describes the app's room with a
**top edge alone**: there is no left or right inset, so no keyboard can ask an
app to lay out narrower. Halves that reach the foot of the glass can only be
drawn over the app's own margins, and covering the app is the thing worth
avoiding. So the keyboard takes its room off the bottom, as any keyboard does,
and spends its width on putting the two banks where the thumbs are.
`Landscape keyboard height` sets how deep the band goes, and
`Banks on the edges in landscape` puts the two side by side in the middle
instead.

## Tilt

`Tilt` turns the rings, in degrees, and the right bank turns the other way
because the two thumbs sweep mirrored arcs. A thumb sweeps an arc and the rings
are already arcs, so this may buy nothing; it is one rotation of θ, and it
costs nothing to keep while that is undecided.

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
stroke instead of writing; lifting on them again sends them alone. `#` and `@`
are those keys. `*` and `+` are not: a bare `*` is the undo stroke, and arming
a key is not undoing anything, so a tap on `*` has to write it.

Two quick taps add the side's double-tap key on top of the key under them, so
tap tap on `-T` releases as `-*T`, and a touch that stands still adds the
side's long-press key. Both are shortcuts past the row, and both still carry
`*` and `+`. A key armed while its own thumb is still down is held rather than
armed, so it stays over a run of taps by the other hand.

`The wait starts` says which clock a long press keeps. From the touch going
down, a press is a tap held and moving off the spot gives it up. From the last
movement it is a stall: the thumb may walk as far as it likes and then stand
still, wherever it has arrived.

## The buzzes

A buzz when a key is taken says a key was taken, which is a thing you can
already see. The buzz that matters is on the **change of cell**, because that
is what turns a slide into something to feel instead of something to read.

Three occasions have something to say: the change of cell, a key taken, and a
stroke written. Each one carries a **voice per thumb** and how hard to say it.

A voice is a name for one short thing a motor can do. Where the phone has
composition primitives it is one of those; where it has none the same name
comes out as a pulse of that length, that strength and that count, because
rhythm and amplitude are what survive when pitch does not. So a `low tick` is
`PRIMITIVE_LOW_TICK` on a phone that has it and a softer pulse on a phone that
does not, and a layout of voices means something either way.

| | |
|---|---|
| `pulse`, `soft pulse`, `two pulses` | no primitive at all, so they are the same on every phone |
| `low tick`, `tick`, `click` | 20ms each, and what a tick can be given |
| `two ticks` | a take can afford 60ms; a tick cannot |
| `rise`, `fall`, `spin` | 100 to 150ms. A stroke is made a few times a second at most |

The settings offer only the voices this phone can make, and only the ones short
enough for the occasion. Every chip plays itself when it is tapped.

A phone has one vibration motor, so a voice per thumb is not a left channel and
a right channel: it is two buzzes from the same motor, which is why the two
have to differ in something the motor has. More than one motor is the one case
where a buzz can be put on a side, and the few devices with two get one each.

## The bar

The steno keys write text and can ask for nothing else, so the bar carries what
the editor owns, right to left in reach order:

| | |
|---|---|
| the field's action | Go, Search, Send, Next, Done, or whatever the app calls it. A field with no action of its own gets a plain Enter |
| `⌫` | one character back, as a key event. Hold it and it repeats |
| `↶` | undo one stroke, which is not the same thing |
| `⌄` | put the keyboard away |
| `⚙` | the settings |

`⌫` sends a key rather than deleting the surrounding text. Plenty of fields
answer only the key: a web view, a canvas, a terminal. They report nothing
about what a delete did either, so there is no failure to fall back from, and
the one every field understands is the one to send.

Any edit made through the bar gives up the tracked buffer. What reaches the
editor is the difference between the runtime's buffer and what was last sent,
so an edit made another way would leave that difference describing text nobody
has. The next stroke starts from the field as it now is.

## The record

After every stroke the runtime reads the trailing one to four words backwards
through every dictionary that carries a `reverse.hfstol`, phrases included.
When an outline writes the same text in fewer strokes, that is a **brief**. The
lookups run off the stroke's thread, so the editor never waits on them.

Two facts arrive per stroke and at different times: the outline and the text it
wrote, together; a brief for that same text, later. The second is about the
first, so a brief hangs under the stroke that provoked it rather than living in
a bar of its own.

What reaches the screen is **one line, and the briefs alone**. A stroke you
have just made is not news, and a shorter way to make it is. Anything taller
than a line is the app's room rather than this keyboard's, so the panel never
grows into it. A quiet spell clears the line, and how many briefs fit and how
long the spell is are settings.

## Defining an outline

An outline that wrote the wrong thing is defined where it was made. `TKUPT` is
`{PLOVER:ADD_OUTLINE}`, and it opens a panel over the keys with the outline of
what was just written in one field and nothing in the other;
`{PLOVER:ADD_TRANSLATION}` opens the same panel with both fields empty.

**Steno fills both fields.** The lit one is where the next stroke goes: the
outline field takes the chord as it is written, and the translation field takes
what the chord *writes*, which is the runtime's answer and not a second way of
spelling one. So the keyboard writes into a second buffer while the panel is
open, and what it had written is waiting when the panel closes. `⌫` and `↶` in
the bar reach the lit field.

`−` and `+` take the outline from one stroke further back, up to four. Save
defines it and makes those strokes again, so what they wrote becomes what they
now mean. **An empty translation field deletes the entry**, and the button says
Delete when it is empty.

The entry goes in `stfst-user.json`, in the app's external files directory, next
to the desktop's `user.json`. Both are read as files rather than as artifacts,
because the phone has no HFST and a compiled dictionary cannot be added to. What
the directory holds and where each file sits in the stack is `editable.toml`,
which rides in the APK.

`make install` syncs them: it pushes `user.json` in the APK's assets and pulls
`stfst-user.json` off the phone, unions it with `~/.config/plover/stfst-user.json`
and pushes the result back, so a definition made on the phone reaches Plover and
the other way round. `android/sync-user.sh` is that step on its own, and a
network sync can replace it without anything else changing.

## What the touch panel reports

`Draw the touched area` draws every sample of the contact at the size and shape
the driver gives it, and `Print the touch numbers` prints pressure, size and
the two contact axes. Whether a hard press can be told from a light one is a
question about the hardware, so it goes on the screen rather than being
guessed at. Both are off by default.

## A steno machine

A machine on USB writes through the keyboard as the keys do, and the keys hide
while one is connected; the `Machine` tab can keep them. It reads three
protocols: Plover HID, with `plover-hid-timing`'s chord boundary, hold key and
hold sounds, and Gemini PR and Stentura over any serial port
`usb-serial-for-android` has a driver for. A device with both a Plover HID
interface and a serial port is read as Plover HID.

The options and the keymaps are the desktop's. `sync-assets.sh` copies
`~/.config/plover/plover.cfg` into the APK, and the keyboard reads each
machine's section and the current system's `keymap[...]` from it. So `X1` is
`🦐-` here because it is there. The HID machine follows `[Plover HID Timing]`
unless `machine_type` is stock `Plover HID`. A serial port speaks what
`machine_type` names, or Gemini PR when that names neither; the `Machine` tab
can choose instead.

Android asks before an app may open a USB device. Picking the keyboard with
"always" when a machine is plugged in makes the answer stick; otherwise it is
asked again at every plug. A HID device that is not a boot keyboard or mouse is
asked about too, because only its report descriptor says whether it is a
machine, and reading it needs the permission.

What a machine sends means nothing to the Kotlin side. `stfst-machine` has the
wire formats, the chorder, the keymap and the sounds, so a desktop client can
share them; `Machines` owns the transport and a thread per device, and hands
every read across.

## The settings

Seven tabs, a live keyboard under them, and the rest follows from wanting to see
the answer rather than read about it.

A change lands in the keyboard at the foot of the screen as it is made,
mid-drag. What a bank cannot show is drawn instead: the two screens at a
glance, with the room the keys take off the app and where the split leaves the
banks. A buzz setting plays itself, left voice then right.

**A number on the screen that is not the number in force is worse than no
number at all.** Square banks cap the keyboard's depth, and the split moves
that cap, so the height prints what it came out as beside what was asked for,
`0.46 → 0.26`, and shades the run of its slider that cannot be reached. Every
knob of the open tab is recomputed whenever any of them moves.

**A flag owns the knobs under it.** Double taps, long presses, the dither, the
labels, the briefs, the modifier row, the band over the app and each of the
three buzzes are a switch with its own numbers indented beneath it, and turning
one off greys what it governs rather than taking it off the screen: where a
number stood is part of how the screen is read, and what it said is what
turning the flag back on restores.

The key a gesture or a tap takes is chosen from the keys no walk can spend,
because those are the whole of the answer. A voice is chosen the same way, from
what the motor can do.

The prose is behind a `?`: a paragraph is worth reading once and is in the way
every time after.

## Building

`make install` from the repo root does all of this, pushes the APK to the phone
over adb, and syncs `stfst-user.json` both ways. It leaves the dictionaries
alone; `make dicts` rebuilds those.

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
| `Ring` | the layout, the geometry, and the path a chord takes. No Android, no dictionary |
| `Chorder` | two thumbs, and the rule that turns them into strokes. No Android |
| `Notes` | the record of what was written, and how much of it fits. No Android |
| `Motor` | what a thumb motion costs, for judging layouts. No Android, no layout; see `docs/motor.md` |
| `DefinePanel` | the two fields of an entry being defined. Decides nothing |
| `RingView` | drawing and touch. Decides nothing |
| `StenoService` | the editor, the insets, and the native handle |
| `Machines` | steno machines on USB: permission, the transport, a reader thread each |
| `stfst-jni` | opens the stack, hands strokes to `stfst-board`, returns the buffer |
| `stfst-machine` | what a machine's bytes and timing mean, and the keymap. No I/O |

`Ring`, `Chorder` and `Notes` are plain Kotlin so they can be tested without a
device, and the tests in `app/src/test` are the ones worth reading first.
`PreviewTest` draws the banks into `app/build/preview-*.png`, idle and mid
chord, which is how the drawing is looked at without a phone.
`docs/rings.html` is the working demo the layout was measured in.

## Not carried over

The comb went whole: the hexagonal lattice, the portals and their strides, the
ride, the holes, and the focus that a jump threw off the thumb. The middle of a
ring does what all of them were for.

The five-way drag ring for modifiers is out too. `out` / `out-far` /
`out-back` is three thresholds, and those are the knobs this design deletes.
The rim is cells instead, reached along a corridor: a sector is a place you can
see, a depth is a gate you have to feel.
