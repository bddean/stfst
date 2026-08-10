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

Each ring's keys are drawn where a slide has to reach to take them, and the
boundary between them is derived from those two numbers rather than set on its
own. It sits about a third of the way out from the near ring, because
undershooting is what a thumb does, and after one consonant the near ring holds
only the vowels: a boundary too far out turns every short flick into A or O.

The ring is drawn a second time in the clear, where the thumb is not covering it.
Same keys, same angles, smaller. It is fixed in the field, so a thumb that has
slid to the edge cannot drag the copy off the screen with it.

```
   ⌫      S      T        far ring, long slide:  S T K P W H R
       +                  near ring, short slide: A O # @ ^ +
R     ^   ●   A     K     ● is the thumb
       @  #  O            slots run clockwise in steno order
   H      W      P        from each ring's start angle
```

Sixteen slots against thirteen keys a bank, so every key still available always
has a slot of its own. `RingTest.live_keys_never_share_a_slot` is that claim.

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

A cell is as small as thirteen keys tiling a field can be, and three columns is
already the squarest packing for a field this shape. To make the cells smaller,
make the field smaller with `Keyboard height`.

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

## Writing a stroke

Touch down on the key you want. Then flick per key: angle chooses the key,
distance chooses the ring.

Two rules decide when a flick has finished, and both exist because reaching for one
key passes through the answer to another:

- A **far** key is taken at once, since nothing lies beyond it, but only if the
  slide has turned by `Turn needed` since the last key. Going further along the
  same ray is the same reach, not another one.
- A **near** key waits `Settle`. A thumb on its way out to a consonant crosses the
  near ring, and after one consonant the near ring holds only the vowels, so taking
  it on the way through is how every reach became A or O.

The key a thumb would take if it stopped now is drawn in the accent colour, so a
wait looks like a wait rather than like nothing happening.

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

## What the touch panel reports

Every sample of the contact is drawn, faint, at the size and shape the driver
gives it, and the numbers behind it are printed: pressure, size, and the two axes
of the contact ellipse. Whether a hard press can be told from a light one is a
question about the hardware, so it is on the screen rather than guessed at. Most
panels report a constant pressure and a real contact area, which means a firm
press shows up as a bigger ellipse and not as more pressure.

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
