# The radial keyboard, and what it needed from the runtime

`android/README.md` describes the gesture. This is what building it changed here.

## A host is not a test harness

`stfst translate` held the whole host loop: retract what a longer match
supersedes, show a rewriting dictionary its window, bind registers, carry state,
and carry out the commands that reach the stroke buffer. The keyboard needs every
line of that and none of the printing.

So the loop is `stfst-board` now, and the CLI is a printer over the `Step` values
it returns. The translate fixtures pass unchanged, which is what makes the move
safe to have done at all.

One thing the move revealed: the board takes `Option<Arc<Morph>>`, not
`Arc<Morph>`. `Text` was always ready for an absent lexicon, and a phone that has
not built the morphology artifacts should start rather than refuse. The CLI passes
`Some`.

## The ring asks the runtime nothing

The first design had the ring drop any direction that could not extend a live
prefix, which needed a `Cursor::successors` primitive and a `Session::live_keys`
above it. Both were written, and both were then deleted.

Steno order alone is a better rule. A key that sits at a fixed angle whatever the
dictionary holds can be learned; a key that moves cannot, and the search space is
already cut by the order. So the ring is geometry over the key order, the
runtime is asked only to translate, and two layers of the stack went back to what
they were.

## The bug the draft found

The ring first re-centred part way back along each slide, to stop a six key chord
walking the thumb across the glass. The measuring origin and the drawn origin were
the same point, so moving it left a residue: after taking a key, the distance from
the new origin to the fingertip was still above the slide threshold, and the next
touch sample spent it on a second key. One flick, two keys.

There is no offset that fixes this. Drawing the ring anywhere but the measuring
point sends the thumb at an angle nothing measures, and leaving them together
means the origin has to be the fingertip. So the parameter is gone, the origin is
where the last key was taken, and drift is handled by the stall alone.

`ChorderTest.one_flick_takes_one_key` is the regression.

## Two more the phone found

A lift wrote whatever both thumbs held, so the second thumb's lift wrote its held
keys a second time and `Rr` was impossible to type. Holding keys is not asking for
a stroke. A lift writes only when a key has been taken since the last one.

The drawn radii were multipliers of the hit boundary, so the boundary was a number
the drawing did not use, and it sat far enough out that a thumb aiming at a
consonant and falling short got a vowel instead. After one consonant the near ring
holds only A and O, so this was most of the left bank being unreachable. The two
radii are the parameters now, the boundary is derived from them, and it is biased
toward the near ring.

## The vowels again, and what was actually wrong

Moving the ring boundary did not fix the unreachable consonants, because the
boundary was never the mechanism. A key was taken the moment its threshold was
crossed, and a thumb reaching outward crosses the near band on the way, so the
vowels fired before the thumb had gone anywhere near a consonant. No boundary can
fix that: the near band is on the path.

What decides it is when a flick is finished, not where the rings are. A far key has
nothing beyond it, so crossing its threshold is an answer. A near key might be a
thumb still travelling, so it waits.

Fixing that exposed the other half. Reaching outward clears the far threshold again
from every new mark, so one long drag took a second and a third key along the same
ray. A new key means a new direction, and the turn has to stay under the angle
between two slots or the neighbouring key becomes unreachable.

Both were found by a test that samples a drag every 8 ms, which is the thing to
write first next time: a single `move` per key never reaches either bug.

## One question was two

The touch-down was read as a slide from the field's home point, on the grounds
that the first key then needed no second layout. That is true and it was still
wrong: a tap has no direction to read, and reading one anyway put all thirteen
keys of a bank within a thumb's length of one point. The first key of a stroke was
the hardest one to hit.

A tap and a slide are different questions, so they get different layouts. The tap
layout is a honeycomb over the whole field, and its cell is the set of points
nearer that key than any other. The ring is for slides only. Positions on the
honeycomb stay fixed whatever the stroke holds, because a tap is aimed before the
thumb lands, and steno order is applied by the pick rather than by the layout.

## The picture was the problem

"Getting the right angle is hard" was not about the angles. `Snap limit` is 180, so
the nearest live slot always wins and a live key already owned every direction
closer to it than to any other live key: after one consonant each vowel owned half
the near ring. What was drawn was a small circle at the slot, so the generosity was
invisible and the ring read as eight targets to hit precisely.

So a key is drawn as the whole region that takes it, and the regions are asked of
the decision function rather than worked out from the slot angles. A drawing
derived from the same numbers can still disagree with the code; a drawing that
probes the code cannot. The earlier version of this bug is two paragraphs up: the
drawn radii were multipliers of a boundary the drawing never used.

`RingTest.a_region_is_where_a_slide_of_its_angle_goes` is the claim, over every
state both banks can be in.

## The stall had the same fault as the pivot

A stall moved the mark to the field's home point while the thumb was still down,
which is the deleted pivot bug wearing a different hat: the mark is the measuring
origin, so a mark where the thumb is not counts the gap between them as a slide,
and the next twitch spends it on a key. The stall brings the ring to the thumb
now. A thumb that is up has no position, so that one still goes home.

## What is not covered

`engine.undo`, `engine.repeat_stroke` and `engine.toggle_star` moved into
`stfst-board` untested. No dictionary in `recipe.toml` emits them, so no fixture
reaches them; the probe dictionary is where a case would have to go.
