# Rings: what to build

The brief the rings were built from, kept as it was written. The keyboard has
moved on since: the modifiers left the rim for a row, the pinky keys became one
upgrade bit, and ring 2 is staggered rather than lined up. `README.md` is what
ships.

This replaces the comb. `docs/rings.html` is the working demo and the reference
for every rule below; it is a single file with the layout, the hit test, the take
rule and the corpus scoring in about 300 lines of JavaScript, and it replays
every chord through its own hit test. Read it first, then this.

## The rule

**Entering a live cell takes it.** Everything else on the board is ground.

A cell is live when steno order still allows its key. A cell is ground when you
already took it, when steno order has passed it, when it holds no key, or when
it is the hub and you have left it.

Nothing waits on time, distance or turning. There is no threshold to tune, and
the three rules the design started with are consequences of this one:

- sliding out to the next ring "erases" the ring behind, because steno order
  killed those keys and dead cells are ground;
- turning through a ring adds keys, because you cross them;
- the shortcut is the middle. Two cells far apart on one ring are joined by a
  straight chord through the hub, and the hub is ground.

A comb needs portals because it is flat. A ring has a middle, so **the portals,
the strides, the holes and the detached focus all go**.

## The layout

```
LEFT                                RIGHT
hub    · | S | ST | T               hub    · | E | EU | U     (tap only)
ring 1 · P H R W K                  ring 1 F R B G L P
ring 2 A O A O A O                  ring 2 T S T S T S
floats ^ # + ^ # +                  ring 3 D Z D Z D Z
                                    floats * @ * @ * @
```

Six slots to a ring, on both banks, with a gap of 13° between every pair. The
gaps line up between rings, so a straight run from hub to rim takes nothing.
That corridor is load-bearing: 12.4% of strokes cross the right ring 1 taking
nothing (`-ES`, `-ET`), 2.7% cross the left ring 1 taking nothing (`SA-`).

Four facts behind the shape, each measured against 340k strokes from
`typey-type-full.json`:

| | |
|---|---|
| the ring order is the steno board folded | the two rows joined at both ends. `T P H R W K` beats every other cyclic order of the left consonants (89.7% of chords in one motion, against 70.7% for steno order itself), and `F R B G L P` does the same on the right |
| the hub is a pie of complete answers | landing takes the whole choice, so the hub is ground from then on and you can leave at any angle. `S T` in the left hub is worth 3.6 points over `S` alone: 93.3%, and the ring drops to five cells at 72° |
| a key may hold several cells | floats and vowels repeat round their ring, because a float is never killed by steno order and a thumb that travels round the rim to find one takes every float on the way |
| the pinky folds radially | `-D` is straight out through `-T`, `-Z` straight out through `-S`. `-TS` is 2351 strokes and `-DZ` is 1466, while `-TZ` is 26 and `-SD` is 3, so two keys repeated everywhere beats four keys in four of six slots |

A float rim is turned half a slot, so its cells sit on the spokes: a float is a
straight run out, and overshooting the ring below lands in ground. Derive the
turn rather than configuring it — a ring whose keys are all floats is a rim.

## The geometry

One formula for the drawing and for the hit test. A bank is a rect; in its
normalised frame the rings are superellipses.

```kotlin
// u = (x - cx) / a, v = (y - cy) / b
r  = (|u|^p + |v|^p)^(1/p)                       // which ring, p = 3.5
θ  = atan2(v, u)                                  // where on it
// and back, for drawing and for a cell's centre:
k  = r / (|cos θ|^p + |sin θ|^p)^(1/p)
x  = cx + a*k*cos θ ;  y = cy + b*k*sin θ
```

`p` interpolates circle to rounded rectangle. At 3.5 the rings fill a
rectangular bank instead of wasting the corners, which is what portrait needs.

Radii are fractions of `r = 1`, outward: left `0 .31 .56 .78 1`, right
`0 .27 .47 .65 .83 1`. Model degrees run from straight up, in the bank's own
direction: the right bank's angles run the other way, so both thumbs sweep
mirrored arcs and "against the walk" is the same motion for each hand. Slot `i`
of a ring spans `rot + i*60 ± (30 - GAP/2)`.

That is the whole of `Ring.kt`. It has no Android in it and no dictionary, the
way `Comb` did not.

## The layout is text, still

Keep the comb's principle: a layout is one value per cell and everything else is
recovered from it. Per bank, the hub slices and then the rings outward.

```
hub   = "./S/ST/T"
rings = [".PHRWK", "AOAOAO", "^#+^#+"]
```

A dot is ground. A slice or a slot may hold more than one key (`ST`, `EU`).
Recovered from that text, not configured beside it:

| | |
|---|---|
| the walk | steno order over the keys the text names |
| a float | a key the bank's order does not rank |
| a rim | a ring whose keys are all floats, so it is turned 30° |
| a duplicate | the same key in two slots. Nothing special: a key is a set of cells |
| a spoke | where the gaps of every ring line up |
| the hub is tap-only | it is level 0; entering it from a ring takes nothing |

## What to keep

`Chorder` is mostly about two thumbs rather than about combs, and that part
survives unchanged:

- lift writes, and the thumb still down keeps its keys;
- a lift writes only when a key has been taken since the last stroke;
- a lift that took only the armed keys arms them instead of writing (`#`, `@`,
  and not `*` or `+`);
- two quick taps add the side's double-tap key;
- one thumb may walk both banks while the other is up;
- the buzz is on the change of cell, not on the take;
- `Notes`, `StenoService`, `stfst-jni`, `Motor` are untouched.

## What to delete

| | |
|---|---|
| `Comb.jump`, the strides, the ride, the landing search | the middle does this |
| the hole and `Teleport through a hole` | same |
| `Thumb.offX/offY`, `aim`, the drawn line to a parted focus | the focus is the thumb again |
| `Focus reach` (`gain`) | it existed to shrink a comb inside a thumb's sweep; a ring is already inside one. Reconsider only if portrait measures badly |
| the honeycomb tap layout | a tap is a cell like any other, because the layout no longer moves |
| the lattice, `Tilt` as a lattice rotation | `Tilt` may come back as a rotation of θ, which is one line |
| `Show where the portals go`, `Show cells already passed` | the first has nothing to show; the second can stay if it is still wanted |

`Motor` stays as it is and gets a new caller: a ring path is gates along an arc
or along a chord, which is what it already prices.

## Tests

The comb's tests earned their place by catching real bugs; these are their
equivalents.

| | |
|---|---|
| `RingTest.a_tap_lands_inside_the_cell_it_is_answered_by` | every cell centre hits its own cell, over both banks and every ring. The demo runs this and it is worth one line in Kotlin |
| `RingTest.every_chord_is_a_path_a_thumb_can_make` | for every chord in the corpus, walk the path the drawing claims, sampling every few millimetres, and assert the keys that come out are the chord. This is the one that matters: it is `every_drawn_slide_is_a_slide_a_thumb_can_make` again, and the demo already passes it for every chord it lists |
| `RingTest.every_key_is_reachable` | from every state a bank can be in, every key steno order still allows can be entered. Nothing should need a second stroke |
| `RingTest.the_corridor_is_open` | a straight run from hub to rim takes nothing, on both banks |
| `RingTest.the_order_is_the_one_measured` | the shipped ring orders score what this document claims. Recompute from the corpus rather than asserting the number |

The corpus counts are small enough to check in: about 2 kB of chord → count,
already extracted for the demo.

## The numbers, for regression

Share of chord occurrences writable in one motion, no hub crossing:

| | |
|---|---|
| left, `· P H R W K`, `S T` in the hub | 93.3% |
| left, `T P H R W K`, `S` alone in the hub | 89.7% |
| left, steno order round the ring | 70.7% |
| right ring 1, `F R B G L P` | 97.2% |
| right pinky combinations | 100%, bar 0.11% that need a diagonal |

The residual is a hub crossing, which is two motions, not a failure. It is
`PR KR KH KHR TKR TPR` on the left and `FL RG RL` on the right.

## Settled while designing, so it does not get relitigated

- **The five-way drag ring is out.** `out` / `out-far` / `out-back` is three
  thresholds — a distance, a reversal, and where the second distance ends — and
  they are the knobs the comb deleted. The rim is cells. What survives costs
  nothing: a float advances no steno order, so crossing back inward leaves the
  rings as they were and the stroke goes on.
- **A second label on a cell is out.** Entering a cell against the walk could
  yield a different key, and both candidates lose. Under steno order the
  backward face of an early key is unreachable, so the pair chord (`T→TK`) buys
  nothing; the opposite cell (`T↔R`) buys `PR` and `KH` and costs `PW`, `TK`,
  `KWR`, taking the left bank from 89.7% to 72.3%. The reason is worth keeping:
  the backward side of a cell is already how you cross the dead zone.
- **A voiced mode for `-D -Z` is out**, because the band beyond is the same idea
  with no mechanism: nothing rewrites anything and radial still means later in
  steno order.
- **A sticky bank is not needed.** Every key of both banks is reachable in one
  stroke. Keep the idea in the drawer for a layout that is not.

## Open, and in this order

1. **The radial budget in portrait.** Four bands across a 35 mm bank, five on
   the right, is the tightest thing here. Build it in paper or in the settings
   preview before the IME, and let the radii move.
2. **What the rim holds.** `^ # +` and `* @` fill it now. The editor's own
   actions — ⌫, undo, the field's action — may belong there rather than in a bar.
3. **Tilt.** A thumb sweeps an arc; the rings are already arcs, so tilt may be
   unnecessary, or may be one rotation of θ.
4. **Landscape.** Same topology, twice the radii, no concerns.
5. `android/README.md` describes the comb. Rewrite it when this lands, not
   before.
