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

The ring is drawn a second time above the thumb, where the thumb is not covering
it. Same keys, same angles, smaller.

```
   ⌫      S      T        far ring, long slide:  S T K P W H R
       +                  near ring, short slide: A O # @ ^ +
R     ^   ●   A     K     ● is the thumb
       @  #  O            slots run clockwise in steno order
   H      W      P        from each ring's start angle
```

Sixteen slots against thirteen keys a bank, so every key still available always
has a slot of its own. `RingTest.live_keys_never_share_a_slot` is that claim.

## Writing a stroke

Touch down: where the thumb lands relative to the field's home point is already a
slide, so the first key needs no second layout. Then flick per key. Angle chooses
the key, distance chooses the ring.

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

Drift is handled by holding still. After `Stall`, the ring goes back to the home
point with nothing written.

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
