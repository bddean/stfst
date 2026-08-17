# The motor model

`android/.../Motor.kt`, plain Kotlin beside `Comb` and `Chorder`: no Android,
no hexes, no dictionary. It answers one question: what does a thumb motion
cost? The answer is three readings, seconds, a miss probability, and comfort,
and they come off one synthesised trajectory rather than from three models.

## The report

`docs/motor-report.html` compares five boards under this model, interactively:
the shipped comb, swype on it, swype's own weave, duplicates with negatives,
and rays. It carries a JS port of `Motor.kt`; the Kotlin is the source of
truth. The published copy is
<https://claude.ai/code/artifact/bfcdbf35-598d-4229-ad12-96bf0939df45>, and
republishing from this file keeps that URL.

## Why cell distance was the wrong number

The comb's design so far minimises how many cells a thumb crosses. But a
thumb is not a rook. A single long line or a gentle curve is easier than a
short run of precise squiggles, because what a squiggle spends is not
distance: it is corners, each a fresh muscle burst, and tolerance, which
caps speed. The model prices exactly that, and its first test is the claim:
a 19 mm squiggle costs more time than a 45 mm line.

## One mechanism

A motion is a path with lateral tolerances: a list of `Gate(x, y, slack)`,
in millimetres, where slack is the half-width that still passes. The model
resamples the path and gives every point the lowest of four speed ceilings:

| ceiling | form | the law it recovers |
|---|---|---|
| top speed | `vMax`, reached under `aMax` | ballistic sweeps |
| curvature | `v = bend / cbrt(k)` | the two-thirds power law |
| tolerance | `v` such that `z` sigmas fit in the slack | the steering law |
| homing | `v <= (distance left + slack) / home` | Fitts' law, by integration |

A forward and a backward pass under `aMax` make the caps reachable, and time
is the integral along the result.

Scatter is one line, `sigma = jitter + noise * v + drift * sqrt(glide)`, and
it is what ties speed to accuracy: the same channel is missed more when it
is crossed faster. The drift term is the glass itself. A physical key gives
the finger an edge to feel; a slide on glass feels nothing, so it runs open
loop and wanders like a random walk, and only a touch-down re-anchors it.
That is the standing tax on long glides, and it is why a stroke of taps and
a stroke of one swipe are priced by different physics. Each gate is scored
against the sigma the profile has there, so the miss probability is not a
separate theory. Speed and accuracy are one dial; comfort is the only
separate reading.

A sharp corner is not on the profile. It is paid as `snap` seconds scaled by
how far past 30 degrees the path bends, because a reversal is an
agonist-antagonist switch and its cost is time-like, not distance-like.

## The API

| | |
|---|---|
| `slide(gates)` | tracing a channel on the glass, from rest |
| `flight(from, to)` | repositioning in the air and landing in a gate |
| `stroke(slides, from)` | a thumb's whole production: fly, trace, lift, repeat |
| `Cost.alongside` | two thumbs at once: the slower sets the pace |
| `confusion(a, b)` | how separable two paths are to a soft recogniser |
| `Cost.effort(weights)` | one comfort number from the components |

`Cost` keeps the comfort components apart, travel, turn, reach, radial,
snaps, because their exchange rate is taste where seconds are not. `Hand`
is an anchor and an easy band of reach; `reach` weights millimetres by how
far outside the band they were walked, and `radial` counts extension against
sweep, which thumbs price differently.

## What the defaults produce

| motion | time | miss |
|---|---|---|
| 45 mm line, slack 5 | 393 ms | 0.061 |
| 19 mm squiggle, slack 5 | 519 ms | 0.066 |
| flight 30 mm to slack 3 | 373 ms | 0.002 |
| flight 30 mm to slack 1.5 | 469 ms | 0.114 |
| tap 25 mm away, slack 4 | 409 ms | 0.000 |
| a four-cell comb walk | 637 ms | 0.125 |

Plausible magnitudes: a tap rate near 150 per minute, Fitts-shaped flight
times, and an 11 percent miss on a 1.5 mm target. `MotorTest` holds the
shapes as inequalities, not the numbers, so retuning cannot break a test
without breaking the physics.

## How the next ideas plug in

- **Swype for steno.** Accuracy for a soft recogniser is `confusion`, not
  gates: score a candidate word-path against its rivals' templates, with
  the scatter the profile itself predicts. Speed and comfort read off the
  same `slide` with wide slacks.
- **Duplicates and negatives.** The model never sees them. A duplicate is a
  second candidate path for the same chord; a negative is a straighter
  channel that passes through a key and unpresses it. Both show up as
  cheaper paths, which is the point of having them.
- **Evaluating a layout.** A layout is a function from chord to candidate
  paths. Score a corpus by the cheapest production of each chord,
  `left.alongside(right)`, and sum. What slack a gate gets is the layout's
  question: the room to the nearest cell whose key the stroke must not take.

## Calibration

Every parameter is measurable from recorded traces: `vMax`, `aMax` and
`bend` from speed against curvature, `jitter` and `noise` from lateral
deviation against speed, `drift` from how that deviation grows along a
glide, `home` from landing profiles, `touch` and `snap`
from tap and reversal timings. Until then the defaults are literature-shaped
and the tests are inequalities on purpose.

## What it does not model

Practice, so a familiar chord is not cheaper than a strange one. Visual
search, so a key you must look for costs what a key you know costs. Finger
occlusion, grip shift, and the interleaving `Chorder` allows between thumbs
mid-stroke: `alongside` assumes the two productions are independent. Each is
a refinement the three readings can absorb later without changing callers.
