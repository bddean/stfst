# The ring keyboard

An Android input method over the compiled dictionaries. Two thumbs, each
walking its own set of nested rings through its half of the steno order.

## The rule

**Entering a live cell takes it.** Everything else on the board is ground.

A cell is live while steno order still allows its key. It is ground once you
have taken it, once the order has passed it, when it holds no key, and when it
is the hub and you have left it.

Touch down on the key you want, slide through the cells you want, and lift.
Lifting either thumb writes the stroke. Nothing waits on time, distance or
turning, so there is no threshold to tune, and the rules that look like extra
rules are consequences of the one:

- sliding out to the next ring "erases" the ring behind, because steno order
  killed those keys and dead cells are ground;
- turning through a ring adds keys, because you cross them;
- the shortcut is the middle. Two cells far apart on one ring are joined by a
  straight line through the hub, and the hub is ground.

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
hub    · | S | ST | T               hub    · | E | EU | U     (tap only)
ring 1 · P H R W K                  ring 1 F R B G L P
ring 2 A O A O A O                  ring 2 T S T S T S
rim    ^ # + ^ # +                  ring 3 D Z D Z D Z
                                    rim    * @ * @ * @
```

Six slots to a ring on both banks, with a gap of 13° between every pair. The
gaps line up between rings, so a straight run from the hub to the rim takes
nothing. That corridor is load-bearing: 12.4% of strokes cross the right ring 1
taking nothing (`-ES`, `-ET`), 2.7% cross the left ring 1 taking nothing
(`SA-`).

Four facts about the shape, each measured against 340k strokes of
`typey-type-full.json`:

| | |
|---|---|
| the ring order is the steno board folded | the two rows joined at both ends. `T P H R W K` beats every other cyclic order of the left consonants, and `F R B G L P` does the same on the right |
| the hub is a pie of complete answers | landing takes the whole choice, so the hub is ground from then on and you can leave at any angle. `S T` in the left hub is worth 3.6 points over `S` alone, and the ring drops to five cells at 72° |
| a key may hold several cells | floats and vowels repeat round their ring, because a float is never killed by steno order and a thumb that travels round the rim to find one takes every float on the way |
| the pinky folds radially | `-D` is straight out through `-T`, `-Z` straight out through `-S`. `-TS` is 2351 strokes and `-DZ` is 1466, while `-TZ` is 26 and `-SD` is 3, so two keys repeated everywhere beats four keys in four of six slots |

## A layout is one value per cell

The hub first, then the rings outward, separated by spaces. A slot is one
character, or several keys between slashes, and a dot is ground. The left bank
ships as:

```
./S/ST/T .PHRWK AOAOAO ^#+^#+
```

Edit it in the settings. Everything else is recovered from that text:

| | |
|---|---|
| the walk | steno order over the keys the text names |
| the rim | the outermost ring. Its keys are the floats, which is why they repeat round it |
| a float | a key steno order cannot kill: takeable however far the thumb has gone, and crossing back inward leaves the rings as they were |
| a duplicate | the same key in two slots. Nothing special: a key is a set of cells |
| a spoke | where the gaps of every ring line up |
| the hub is tap-only | it is level 0. Landing on a slice takes the slice; entering it from a ring takes nothing |

The hub and the rim are turned half a slot: the rim so its cells sit on the
spokes, which makes a float a run straight out, and the hub so its cuts fall on
the bank's own axes.

## The geometry

One formula for the drawing and for the hit test. A bank is a rect; in its
normalised frame the rings are superellipses.

```kotlin
// u = (x - cx) / a, v = (y - cy) / b
r  = (|u|^p + |v|^p)^(1/p)                       // which ring, p = 3.5
θ  = atan2(v, u)                                 // where on it
// and back, for drawing and for a cell's centre:
k  = r / (|cos θ|^p + |sin θ|^p)^(1/p)
x  = cx + a*k*cos θ ;  y = cy + b*k*sin θ
```

`p` interpolates circle to rounded rectangle. At 3.5 the rings fill a
rectangular bank instead of wasting the corners, which is what portrait needs.

Radii are fractions of `r = 1`, outward: left `0 .31 .56 .78 1`, right
`0 .27 .47 .65 .83 1`, and both are settings, because the radial budget in
portrait is the tightest thing here. Model degrees run from straight up in the
bank's own direction: the right bank's angles run the other way, so both thumbs
sweep mirrored arcs and "against the walk" is the same motion for each hand.

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
| left, `· P H R W K`, `S T` in the hub | 93.3% |
| left, steno order round the ring | 70.7% |
| right ring 1, `F R B G L P` | 97.2% |
| right pinky combinations | 100% |

`RingTest.the_order_is_the_one_measured` recomputes those shares from the
checked-in corpus and fails if any other arrangement of the same keys round the
same ring writes more of it in one motion. The residual is `PR KR KH KHR TKR
TPR` on the left and `FL RG RL` on the right.

`Ring.path` is where the claim lives: the stops a thumb makes to write a chord,
found by searching the spokes for a route that crosses nothing live the chord
does not want. `RingTest.every_chord_is_a_path_a_thumb_can_make` walks every
one of them through `Chorder` as thumb motion, sampling finely enough that no
cell is stepped over, and fails where the keys that come out are not the chord.

## Three kinds of key

The vowels and the modifiers stand on their own ground: a green tint for
`AOEU`, a violet one for the modifiers, plain for the rest. Which is which is
read off the glyph, so a key that spells nothing is a modifier and a layout
that moves one does not have to say so twice.

Across that, every cell is in one of three states, because they are three
different answers: **in the chord** (the tint at full strength, a hot rim and a
hot letter), **still takeable**, and **spent by steno order**. A spent key
gives up its hue as well as most of its strength, so the state reads the same
whatever kind of key it is: kind is a question about live keys.
`Show cells already passed` hides the spent ones.

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

A touch that arrives in the other bank's field is handed over to it, and the
bank it leaves keeps what it took. So one hand can write a whole stroke,
slowly, by walking one bank and then the other. It happens only while the other
thumb is up, so two hands never meet it. `One thumb walks both banks` turns it
off.

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
tap tap on `-T` releases as `-*T`. That is a different mechanism and it still
carries `*` and `+`.

## The buzzes

A buzz when a key is taken says a key was taken, which is a thing you can
already see. The buzz that matters is on the **change of cell**, because that
is what turns a slide into something to feel instead of something to read.

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
grows into it. A quiet minute clears the line.

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
| `Ring` | the layout, the geometry, and the path a chord takes. No Android, no dictionary |
| `Chorder` | two thumbs, and the rule that turns them into strokes. No Android |
| `Notes` | the record of what was written, and how much of it fits. No Android |
| `Motor` | what a thumb motion costs, for judging layouts. No Android, no layout; see `docs/motor.md` |
| `RingView` | drawing and touch. Decides nothing |
| `StenoService` | the editor, the insets, and the native handle |
| `stfst-jni` | opens the stack, hands strokes to `stfst-board`, returns the buffer |

`Ring`, `Chorder` and `Notes` are plain Kotlin so they can be tested without a
device, and the tests in `app/src/test` are the ones worth reading first.
`docs/rings.html` is the working demo the layout was measured in.

## Not carried over

The comb went whole: the hexagonal lattice, the portals and their strides, the
ride, the holes, and the focus that a jump threw off the thumb. The middle of a
ring does what all of them were for.

The five-way drag ring for modifiers is out too. `out` / `out-far` /
`out-back` is three thresholds, and those are the knobs this design deletes.
The rim is cells instead, reached along a corridor: a sector is a place you can
see, a depth is a gate you have to feel.
