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

## What is not covered

`engine.undo`, `engine.repeat_stroke` and `engine.toggle_star` moved into
`stfst-board` untested. No dictionary in `recipe.toml` emits them, so no fixture
reaches them; the probe dictionary is where a case would have to go.
