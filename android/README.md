# The radial keyboard

An Android input method over the compiled dictionaries. Two thumbs, each sliding
through its own half of the steno order.

## What the gesture is

A chord is a rising run through the steno order, so one thumb tracing one bank is
a walk that only ever goes forward. Two things follow, and they are the whole
design:

- The key just taken can never come again, so the point under the thumb is always
  free. That is what the ring is drawn around.
- Only keys later in the order can come next, so the ring drops the rest.

The dictionary is not asked. A key sits at a fixed angle on a fixed ring whatever
is loaded, because a layout that moved with the dictionary could not be learned.

## One ring, read by direction

A slide is read by its direction. How far it went decides nothing beyond whether
it happened at all, which `Minimum slide` sets.

There were two rings, and how far you slid chose between them. Metering a distance
under a thumb without looking is the weak channel: the answer to "how far" was
wrong far more often than the answer to "which way", and two rings asked for it on
every flick. Every rule that went wrong on this keyboard came from that one choice.
`Settle` exists because a thumb reaching outward crosses the near ring on the way,
and the vowel it collected there was the bug that took three attempts to find.

Nothing was lost by dropping it. **The narrowest target a key can have is set by
how many keys still follow it, not by the layout.** Twelve keys follow the first of
a bank, so 30 degrees is the ceiling for any arrangement of them, and a single
shared ring reaches that ceiling in every state:

| after | keys still live | each holds |
|---|---|---|
| S | 8 | 45° |
| T | 7 | 51° |
| K | 6 | 60° |
| P | 5 | 72° |
| W | 4 | 90° |
| H | 3 | 120° |
| R | 2 | 180° |

The second ring bought room by adding a channel that cost more than the room was
worth. Put keys in a near string and the short slide comes back, with `Settle` and
the ring boundary.

The ring is drawn a second time in the clear, where the thumb is not covering it.
Same keys, same angles, smaller. See "Where the copy goes" below.

```
   ⌫      S      T        far ring, long slide:  S T K P W H R
       +                  near ring, short slide: A O # @ ^ +
R     ^   ●   A     K     ● is the thumb
       @  #  O            slots run clockwise in steno order
   H      W      P        from each ring's start angle
```

## The live keys share each ring out between them

A ring is divided evenly between the keys still live on it: the earliest sits at
the start angle and the rest follow clockwise, so the order round the ring is the
order of the chord. The dictionary is still not asked, and the layout still follows
from the keys taken and nothing else.

The average region is 360 degrees over the count however the slots are placed, so
what this buys is the worst one. With fixed slots a key with a live neighbour on
each side is squeezed to the gap between them: after E, the right near ring holds
U, d and z, and d gets 45 degrees against 157 for each of its neighbours. Shared
out, all three hold 120. `RingTest.sharing_the_ring_out_widens_the_smallest_region`
is that claim, and `a_slide_to_where_a_key_is_drawn_takes_that_key` is the contract
it rests on, over every state both banks can be in.

The cost is that a key's direction depends on what the stroke has taken. The thing
to learn is "the second key still available is up-right", not "K is at 0". `Share
each ring out evenly` turns it off and restores fixed slots.

The left near ring starts on the diagonal, not at 0. After any consonant it holds A
and O alone, so their boundary is a quarter turn from each: at 0 that boundary is
straight up and straight down, which is where the consonants are, and an undershot
reach upward would pick a vowel by coin toss.

## A boundary is sticky

A thumb resting between two keys is nearer first one and then the other on every
touch sample, so the highlight and the buzz would swap back and forth and neither
would say anything. The key already chosen keeps the answer until a rival beats it
by `Hold on to a key`, six degrees by default.

## Two layouts, because a tap is not a slide

A touch-down has a whole field to aim at and no direction to read. A slide has a
direction and one point to measure from. Reading the touch-down as a slide from
the field's home point made these one question, and it crowded thirteen keys
around a single point: the first key of a stroke was the hardest one to hit.

So a touch-down reads a honeycomb over the whole field, in steno order, three
columns wide. Thirteen keys fall into rows of 3 2 3 2 3, and a cell is a target
about a finger across. Positions are fixed whatever the stroke has taken already,
because a tap has to be aimed before the thumb lands; steno order still applies,
and a tap will not answer with a key the stroke has passed.

Steno order fills the middle row first, then outward. An early key is where the
sliding starts, since most of the stroke is still ahead of it, and a thumb at the
edge of the glass has nowhere to slide to. By row and not by cell: ranking every
cell by its distance from the middle gives the earliest keys the most central
cells and leaves the layout scattered, and thirteen keys nobody can find are worse
than thirteen keys with less room.

`Cell width` and `Cell height` size the lattice against a honeycomb that fills the
field, one being that honeycomb. Less packs the keys within reach of a thumb, and
the cells at the edge then grow to cover the field the lattice no longer reaches:
a cell is the ground nearer that key than any other, and shrinking the lattice does
not change what a tap out there means. Row and column separately, because a field
taller than it is wide gives cells taller than they are wide.

Which is why a cell is drawn as the field clipped by the bisector against every
rival, rather than as a hexagon from the lattice spacing. That is the definition of
the cell and not a picture of it, so it stays right for any lattice and covers the
whole field for every setting. `RingTest.a_tap_lands_inside_the_cell_it_is_answered_by`
is the claim, over a shrunken lattice as well as a full one.

Every slide after that reads the rings.

## Every key is drawn as the region that takes it

Not as a circle at its slot. A live key owns every direction closer to its slot
than to any other live slot, and that is usually far wider than the slot: after
one consonant the two vowels own half the near ring each, and four live consonants
own about ninety degrees apiece. Drawn as circles, that generosity was invisible
and the ring looked like eight small targets to be hit precisely.

The regions are asked of the decision function rather than worked out from the
slot angles, so the picture cannot disagree with what a slide will do. Same for
the honeycomb: a cell is the set of points nearer that key than any other, which
is what `entryAt` answers with.

Three more things are drawn for the same reason. The fingertip, live, so a slide
shows before it is read. A line from the mark to the fingertip, which is the slide
being measured. And the region under the fingertip, filled, which is the key a
lift or a pause would take.

The copy in the clear shows all of that too, at its own scale, so a thumb covering
the real ring costs nothing.

## Where the copy goes

The copy has to sit somewhere the thumb is not, and the field stopped being that
place once the honeycomb filled it. `Copy of the ring` has four answers:

- **Floating over the app**, the default. The keyboard's window reaches above the
  keys, and `onComputeInsets` tells the app to lay out underneath, so the strip
  takes no room from it. The strip is transparent and holds nothing while both
  thumbs are up, and it is outside the touchable region, so a tap there reaches the
  app. It only ever covers what is being read while a slide is happening.
- **In a strip above the keys**, which takes the room instead and pushes the app up.
- **In the thumb field**, which is where it used to be.
- **Off**.

The strip is measured from the ring it has to hold rather than set by a parameter,
and the keys decide the scale, so a copy that would not fit cannot be asked for.

The bar sits under the keys because of this. The inset that lets the app lay out
underneath is a single line, so anything drawn above the strip would have app
content hidden behind it, and the bar is opaque.

## Writing a stroke

Touch down on the key you want, then flick once per key. The direction of the flick
is the whole of what it says.

A key is taken as soon as the flick clears `Minimum slide`, but only if the slide
has turned by `Turn needed` since the last key. Going further along the same ray is
the same reach, not another one, or a single long drag would take a second and a
third key along it.

`Settle` applies to a near ring, so it does nothing until you configure one.

Lifting either thumb writes the stroke, and **the thumb still down keeps its
keys**. Spelling a run of words that share a right-hand shape therefore costs one
right-hand motion, not one per word. `Write on the first lift` turns this off and
waits for both.

A lift writes only when a key has been taken since the last stroke. Keeping keys
and asking for a stroke are different things, and only the second is what a lift
can mean: otherwise the second thumb's own lift writes its held keys again, and
`Rr` comes out as `Rr` and then `r`.

The measuring origin is the fingertip where the last key was taken, and the ring
is drawn there too. One point for both, which is not a detail: any offset between
them leaves the distance already travelled sitting above the slide threshold, and
the next touch sample spends it on a key nobody asked for.

Drift is handled by holding still. After `Stall`, the ring comes to the thumb with
nothing written. To the thumb and not to the field's home point, for the reason
above: a mark left where the thumb is not counts the gap between them as a slide,
and the next twitch spends it.

## The buzzes

A buzz when a key is taken says a key was taken, which is a thing you can already
see. The buzz that matters is on the **change of key under the thumb**, because
that is what turns the aim of a slide into something to feel instead of something
to read.

The two thumbs get buzzes that feel different, and this is where the hardware sets
the limit. A phone has one vibration motor, so there is no left channel and no
right channel, and no frequency to set. What there is, in the order the code asks
for it:

1. more than one motor, on the few devices that have them, addressed separately
2. composition primitives, where `PRIMITIVE_LOW_TICK` against `PRIMITIVE_TICK`
   feels like two pitches from the same motor
3. amplitude control, so weak against strong
4. none of those, so one pulse against two, because rhythm always survives

The settings screen prints which one this device got, under the switch that asks
for it. Guessing at hardware is what the touch readout exists to avoid.

## What the touch panel reports

Every sample of the contact is drawn, faint, at the size and shape the driver
gives it, and the numbers behind it are printed: pressure, size, and the two axes
of the contact ellipse. Whether a hard press can be told from a light one is a
question about the hardware, so it is on the screen rather than guessed at. Most
panels report a constant pressure and a real contact area, which means a firm
press shows up as a bigger ellipse and not as more pressure.

## The bar

The steno keys write text and can ask for nothing else, so a field with a submit
button of its own was a dead end. The bar above the keyboard carries what the
editor owns, right to left in reach order:

| | |
|---|---|
| the field's action | Go, Search, Send, Next, Done, or whatever the app calls it. A field with no action of its own gets a plain Enter |
| `⌫` | one character, by code point. Hold it and it repeats |
| `↶` | undo one stroke, which is not the same thing |
| `⌄` | put the keyboard away |
| `⚙` | the settings |

The action key is renamed for whatever field is in front of it, out of the
`EditorInfo` the app supplies.

Any edit made through the bar gives up the tracked buffer. What reaches the editor
is the difference between the runtime's buffer and what was last sent, so an edit
made another way would leave that difference describing text nobody has. The next
stroke starts from the field as it now is.

## Long press

Off by default. On, a hold opens a modifier ring: the near ring toggles a sticky
modifier and the far ring sends a key with whatever is sticky, so `⌃` then `←` is
one key event.

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
android/release.sh 0.12 "one line of what changed" [notes.md]
```

The GitHub release is the deployment: the phone installs from the release
page, so a build that only lands in `app/build/outputs` has not shipped.
The script tests, builds, and publishes tag `radial-v<version>` with the
APK attached, and refuses a dirty or unpushed tree. Without a notes file it
lets GitHub generate the notes; the hand-written ones read better.

`build-native.sh` wants an NDK, named by `ANDROID_NDK` or found at
`~/Library/Android/sdk/ndk/26.3.11579264`. The cross std comes from the flake,
which is why cargo runs inside `nix develop`.

## What runs where

| | |
|---|---|
| `Ring` | which thumb owns a key, and where the key sits. No Android, no dictionary |
| `Chorder` | two thumbs, and the rule that turns them into strokes. No Android |
| `RadialView` | drawing and touch. Decides nothing |
| `StenoService` | the editor and the native handle |
| `stfst-jni` | opens the stack, hands strokes to `stfst-board`, returns the buffer |

`Ring` and `Chorder` are plain Kotlin so they can be tested without a device, and
the tests in `app/src/test` are the ones worth reading first.
