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

## A picture that was right, and a claim about it that was not

I told the user the labels sat at their slot angles, so aiming at a key meant
aiming near the edge of its region. The labels were already drawn at the middle of
each region, because that fell out of drawing the regions at all. The report was
wrong and the code was right.

What the report was reaching for turned out to be real, and it is about size rather
than position. Fixed slots leave the average region at 360 over the live count and
the worst one much smaller: a key with a live neighbour on each side gets only the
gap between them, 45 degrees against 157 for each neighbour. Sharing the ring out
evenly between the live keys raises the floor to the average. Steno order still
decides the order round the ring, so nothing here reaches for the dictionary.

## What the drawing cost, once it was probed

The regions are probed from the decision function, so drawing a ring asks it a few
hundred questions a frame, and each question asks about every live key. Sharing the
ring out made a slot angle depend on the live set, so every one of those questions
rebuilt a list. That is thousands of allocations a frame for a picture that changes
only when a key is taken.

The live sets are worked out once per state now and the ring a key sits on once per
key. Cheap, and the reason to mention it is that the probe pattern is what made an
O(1) lookup worth having: a derived drawing asks far more questions than a drawing
that is told the answer.

## Is there a stationary layout that does better?

Asked whether the keys could stand still and every chord still be one flick per key,
with hexagons, holes, or a key repeated at several places. The answer is no, and the
reason is a counting one rather than a geometric one.

A slide from key i is read as a direction, so every key after i in the steno order
needs a direction of its own. Twelve keys follow the first of a bank, so those
twelve directions share 360 degrees: **30 degrees is the ceiling for any layout**.
Repeats and holes change which directions are free and cannot change how many are
needed, so neither lowers the ceiling.

Stationary positions cannot even reach it. From key i the successors want to be
evenly spread, and that has to hold from every key at once. It does not: three keys
after i want their directions 120 degrees apart, which for the next key along means
it sits between the two after it on a line, and a point off that line sees three
collinear points inside a half turn, never 240 degrees of it. Four keys already
cannot do it. A hill climb over thirteen keys gets the worst target to 28 degrees,
and that is the worst target in every state, not only the crowded ones.

Sharing the ring out between the live keys hits 360 over the count exactly, in every
state: 45 degrees after S, 60 after K, 90 after W. Which is the answer to why the
layout moves. Stationary keys are worth having, but not through a direction; they
need the finger read by where it is rather than by where it went, and that is a
different gesture from this one.

## What is not covered

`engine.undo`, `engine.repeat_stroke` and `engine.toggle_star` moved into
`stfst-board` untested. No dictionary in `recipe.toml` emits them, so no fixture
reaches them; the probe dictionary is where a case would have to go.

## The comb replaced all of it

The section above answers "is there a stationary layout that does better" with
no, and gives the reason: from key i, every key after i needs a direction of
its own, twelve keys follow the first of a bank, so thirty degrees is the
ceiling for any arrangement and stationary positions cannot even reach it.

That answer stands. What it also said, in its last line, is the way out:
stationary keys "need the finger read by where it is rather than by where it
went, and that is a different gesture from this one." The comb is that
gesture, and it is the keyboard now. `android/README.md` describes it.

Three things carried over unchanged, and they are the ones worth naming
because each cost a bug to learn:

- **Draw the region that takes the key, and ask the decision function for it.**
  A drawing derived from the same numbers can still disagree with the code; a
  drawing that probes the code cannot. On the comb the region is the cell, so
  this is `Comb.region` and the picture uses nothing else.
- **The measuring point and the drawn point are one point.** Any offset between
  them leaves distance already travelled sitting above a threshold. The comb
  has no threshold to trip, but the focus and the thumb are still one point
  until something deliberately parts them, and a stall brings them back
  together rather than to a home point.
- **Read every touch sample, not one per frame.** Take-on-entry reads the path
  itself, and a fast sweep crosses a cell between frames. The test that samples
  a drag every 8 ms found two bugs on the ring; the comb reads
  `getHistorical*` for the same reason.

The first of those three came back to be paid a second time. The portals are
drawn as well, and the drawing of a *jump* was not probed: it asked where a
jump lands, then worked out on its own whether the jump could be ridden on. The
two answers parted at once. The picture drew a ride out of `S` that ran into
`-H`, where a thumb crossing that side takes `-H` instead of riding past it.

The shape of the fix is the same as the region's. A jump is asked about the one
side being crossed, the whole run of a slide is one function, and the picture
calls it rather than deriving it. What is new is the test:
`ChorderTest.every_drawn_slide_is_a_slide_a_thumb_can_make` walks every stop
the picture draws, on every key of both banks, as real thumb motion through
`Chorder`, and fails where the focus does not arrive as drawn. A probe makes
the two agree by construction; replaying the picture catches it when they do
not.

What the comb dropped is the whole apparatus the direction read needed:
`Minimum slide`, `Turn needed`, `Hold on to a key`, `Settle`, the two rings,
the shared-out slot angles, and the sector probe. None of them has an analogue,
because each was answering "which way, and is the flick finished yet". A cell
is a place, so neither question exists.
