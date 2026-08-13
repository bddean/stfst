# The comb keyboard

An Android input method over the compiled dictionaries. Two thumbs, each
walking down its own honeycomb through its half of the steno order.

## What the gesture is

A chord is a rising run through the steno order, so one thumb tracing one bank
is a walk that only ever goes forward. The comb lays that walk out as fixed
cells and reads the thumb by **where it is**, never by where it went.

Touch down on the key you want. Then step, one cell at a time, down the chain.
Entering a cell takes what it yields; a cell steno order has passed is ground.
Lifting either thumb writes the stroke.

Nothing waits on time, distance or turning. A cell is a place, so a tap on one
is finished and there is no gate to tune.

## Why the keys stand still

The old keyboard read a slide by its direction, from a ring that reshaped
itself around the last key taken. That has a ceiling nothing can lift: twelve
keys follow the first of a bank, so those twelve directions share 360 degrees
and **30 degrees is the widest any of them can be**. Sharing the ring out
between the live keys reaches that ceiling exactly and cannot beat it.

A fixed cell pays in area instead of angle, and area the glass has plenty of.
What position reading needs in exchange is a rule with no gate in it, and
take-on-entry is that rule.

## The comb runs down

Flat-top hexes, with alternate columns hanging half a cell lower. So a column
is a run down the glass, and the chain of the steno order zigzags between two
of them: a key touches the next in the order diagonally, and the one after
that directly below.

Down, not across, for two reasons. A thumb sweeping toward its palm is the
short strong motion. And a comb that runs down can be full height, which is
what landscape wants.

## A layout is one value per cell

One character per cell, rows separated by a slash or a newline. A dot is open
ground, `o` is a hole, a space is off the board, and anything else is the key
of that name. The left bank ships as:

```
 ....
 .^S.
 .TK.
 .PW.
 .HR.
  AO.
  o+.
  #..
```

Every character touches the one above, below, left and right of it, plus one
diagonal, so the text is a picture of the keyboard. Edit it in the settings.

Everything else is recovered from that rectangle. There is no span, no size,
no order field, no portal table and no float flag, because the grid already
fixes each of them:

| | |
|---|---|
| a melded button | a run of touching cells holding one value. That is all a big key is |
| two buttons, one key | cells holding one value that do not touch |
| the walk | the two sides facing keys steno order still allows |
| the portals | the other sides, the ones holding passed keys included |
| a float | a key that does not touch the next key of the walk |
| a slide | the run of landings one motion of the thumb reaches: the jump, then the ride, until a crossing cannot be made |
| a cell's ground | the field clipped by the bisector against every rival, and by its own six lattice neighbours |
| where a side points | the step it names, read off the lattice. A hexagon's corners sit 30 degrees off its sides, and a corner is not a side |

## The floats are the modifiers

`#` and `+` on the left, `@` and `*` on the right. Nothing joins them to the
walk, so the walk cannot pass them: a modifier is takeable however far down
the comb the thumb has already gone.

That is the old keyboard's floating arc, and it costs no mechanism. Read the
order backwards and the walk builds itself: the last key of a bank is on it,
and a key joins when it touches the next key already on. What cannot join is a
float. Reading the successor rather than every neighbour is what lets a float
sit against the end cluster and still be a float.

## The end cluster closes a ring

The last two keys of each bank and both of its floats close a ring around a
hole. A hole takes nothing and a portal can land in one, which makes it the
best landing site on the comb: one jump reaches the end of the order and every
key of the cluster is then a single hop away, so you still choose which vowel,
and both modifiers are there too.

Closed, with no way in but a portal. Every key of the order has a portal that
lands in the hole, so nothing ever needs to walk through it, and a ring of
keys is worth more than a gap.

`Teleport through a hole` is the hole's other trick, off by default. It throws
a focus that *walks* into a hole across the comb's middle, which the shipped
layouts give nothing to walk in with.

`Comb` answers all of it, and the drawing asks `Comb` rather than working the
shapes out a second way, so the picture cannot disagree with the decision.
`CombTest.a_tap_lands_inside_the_cell_it_is_answered_by` is that claim, over
three tilts.

## Four numbers, not fifty-two destinations

Steno order is enforced, so four of a cell's six sides can never be taken as
keys. All four are portals. If each carried a destination of its own there
would be about fifty-two of them to learn.

So a portal carries a **stride** instead, and the four strides are the same
for every key on the board: **+3 +4 +7 +10**. The `out` portal always means
"jump three keys ahead". Where that lands is derived:

- **Open ground beside key `i+k`**, adding two or more keys the thumb does not
  already have. The focus rests there and takes nothing, and every one of those
  keys is then one hop away. Two hops, and a choice at the end of the first.
- **No ground worth the hop.** The jump lands on the key itself and takes it.
  One hop.

Worth is what the jump **buys**. A key the thumb can already step onto counts
for nothing, so a landing that shares its neighbours with the cell being left is
a side of the comb spent on ground the thumb already has.

The sides take the strides in order of how far each turns from the way the walk
is heading, so a side means the same jump wherever the thumb is. A side with no
cell beyond it is never crossed, so it carries nothing, and neither does a
stride that aims where an earlier stride already aims.

### The four are one allocation

Two portals that come to rest on the same cell, or on touching cells, spend two
sides of the comb on one place. Answering each stride on its own let that
happen: the greedy pick for stride 3 took the only ground stride 5 had.

So the four are chosen together, and what they are worth together is **the keys
they put within reach, counted once**. Adding up what each is worth on its own
is a different number and the wrong one, because it pays twice for a key two
landings share. Every arrangement is tried and the best is kept, with no two
resting places closer than one cell apart. A handful of strides over a handful
of cells each is a few hundred arrangements, so the exact answer costs less than
a rule for guessing well. A landing that takes a key is exempt, because a key
names itself.

### The stride is not spent

Keep sliding the way the jump went and it goes again. A side is therefore a
**run** of landings, not one: `+4` ridden twice is eight of the order ahead,
inside a single motion of the thumb. That is the whole point of it. A long
stride has to be one longer slide, or it costs as much as two short ones.

So the ride fires on **the side the thumb came in by, or either side next to
it**. A thumb sweeps an arc rather than a line, and holding it to one exact
side of six would be holding it to something hands do not do. A key on the way
out is taken rather than ridden past, and a side with no cell beyond it is
never crossed.

Turning right round out of a landing puts the focus back on foot. That keeps
every motion the board had before the ride existed.

### Two hops, and why the numbers are fixed

The target is that any key a thumb still wants is **two hops away**: one into a
resting place, one onto the key. Whether that is possible is a fact about the
grid, so it is worth measuring rather than asserting.

Across both banks a stroke can want 155 keys in all. The most any four resting
places could ever reach in two hops is 153: `-E` cannot see `-T` and `U` cannot
see `-S`, because the grid has no landing site that sees enough. **+3 +4 +7 +10
reaches all 153.** The strides this keyboard shipped with before, +3 +5 +7 +9,
reach 138.

Riding closes the last two. `-E` rides `+4` twice to reach rank eight, which is
`-T`, and `U` rides `+4` twice to reach `-S`. Held to one exact side the ride
reaches 153; allowed the arc, it reaches all 155. So **every key a thumb still wants
is one slide away**, and `CombTest.a_ridden_portal_reaches_every_key_ahead` is
that claim.

So the four numbers are not a setting. They are the answer to a search, and
`CombTest.the_portals_reach_as_far_as_the_grid_allows` holds them to it: the
test works the ceiling out from the grid and fails if the portals fall short of
it. Change a grid and the test recomputes what that grid allows.

`CombTest.every_key_ahead_is_still_reachable` is the weaker claim underneath:
every key ahead is reachable at *some* distance. A grid can satisfy steno order
and still strand a key behind a wall of keys the chord does not want.

### Watching one land

Four numbers stay numbers until you watch one land, so the keyboard draws them.
While a thumb is down, every portal side of the cell under the focus gets a
line to the cell it arrives at, and an **arrow** in that cell pointing back at
the side to leave by. A ring means arriving takes that key; a dashed ring means
it comes to rest beside it. Once the focus is resting on a landing the picture
is the same, with one arrow: the stride again. `Show where the portals go`
turns this off. The band over the app draws the same picture.

The line goes on to where the slide ends. Each stop it passes through keeps one
arrowhead, and the last one carries **one head per slide it takes to arrive**.
So the heads are where you are going, not where you go through.

`Comb.slides` is the whole picture, and the touch asks the same functions under
it. It had to be one answer. The drawing worked the ride out its own way, which
let it draw a ride out of `S` that ran into `-H`: a thumb crossing that side
takes the key rather than riding past it. `Comb.onward` answers about **the one
side being crossed** and nothing else now, and
`ChorderTest.every_drawn_slide_is_a_slide_a_thumb_can_make` walks every drawn
stop on the board as thumb motion and fails where the focus does not arrive as
drawn.

## Three kinds of key

The vowels and the modifiers stand on their own ground: a green tint for
`AOEU`, a violet one for the modifiers, plain for the rest. Which is which is
read off the glyph, so a key that spells nothing is a modifier and a layout
that moves one does not have to say so twice.

Across that, every key is in one of three states, because they are three
different answers: **in the chord** (the tint at full strength, a hot rim and a
hot letter), **still takeable**, and **spent by steno order**. A spent key
gives up its hue as well as most of its strength, so the state reads the same
whatever kind of key it is: kind is a question about live keys.
`Show cells already passed` hides the spent ones.

## What the stroke would write

The keys in hand are printed above the comb, and under them **what they would
write if a thumb lifted now**.

The runtime is one mutable board, so the only way to ask is to make the stroke
and take it back. Nothing reaches the editor and the buffer ends where it
started. A stroke that means undo is answered by name instead, because asking
what an undo writes would perform one. A match that supersedes what earlier
strokes wrote says so with a `⌫`.

It runs off the drawing's thread, on the same one the reverse lookups use, and
the answer is kept with the chord it is about. So the last chord's text is
never left sitting over this one.

## One thumb, both combs

A touch that arrives in the other comb's field is handed over to it, and the
comb it leaves keeps what it took. So one hand can write a whole stroke,
slowly, by walking one comb and then the other. It happens only while the other
thumb is up, so two hands never meet it. `One thumb walks both combs` turns it
off.

## The preview over the app

Shrink the keys and a thumb crosses cells in less travel, which is faster. What
it costs is legibility, and `Preview over the app` buys that back: a copy of
both combs, drawn above the keys, over the app.

`onComputeInsets` is what makes that work. The keys are what the app lays out
around and what the window takes touches for; the band above them is neither.
So the app keeps its room and its touches up there, and the copy floats on top.

The band has no ground of its own. Only what the thumb needs is opaque there:
the keys the chord can still take, and the landings the portals go to. The rest
is the app. It fades in as a thumb goes down and out as it lifts, because a
band that arrives at full strength reads as a flash over whatever the app was
showing.

The thumb and the focus are drawn in the band as well. They are places on the
comb rather than places on the glass, so `Comb.carry` reads them in the keys'
placement and writes them in the band's. The settings preview gets a band too,
which is where to look at one without an app under it.

## The focus is not the thumb

A portal throws the point that reads the cells ahead of the thumb, and the
offset holds until the lift: the thumb goes on from where it is, the focus goes
on from where the jump put it.

Both are drawn. The focus is the filled cell; once the two have parted the
thumb is drawn too, faint, tied to the focus by a line. Holding still for
`Stall` brings the focus back, writing nothing.

## Landscape, and what Android will not give you

A deep band, with a comb hugging each edge. The gap between them holds the bar
and the record. The app keeps everything above the band and nothing covers it.

Full-height halves with the app between them is what the comb wants, and
Android will not give it. `onComputeInsets` describes the app's room with a
**top edge alone**: there is no left or right inset, so no keyboard can ask an
app to lay out narrower. Halves that reach the foot of the glass can only be
drawn over the app's own margins, and covering the app is the thing worth
avoiding. So the keyboard takes its room off the bottom, as any keyboard does,
and spends its width on putting the two combs where the thumbs are.
`Landscape keyboard height` sets how deep the band goes, and
`Combs on the edges in landscape` puts the two side by side in the middle
instead.

## Tilt

A thumb sweeps along an arc, not a line, so the comb can be turned. `Tilt` is
degrees, and zero runs the columns straight down the glass. The right bank
turns the other way: two thumbs sweep mirrored arcs, so one angle describes
both and the sign belongs to the bank.

It turns the lattice, which is one placement the drawing and the hit test
share; nothing else about the layout moves.

## Focus reach

`Focus reach` is how much further than the thumb the focus travels. One is the
thumb itself. Above one the whole comb sits inside a shorter sweep, so the
travel per cell falls and the walk is quicker.

What it costs is that the focus is no longer under the thumb, which is what the
mark and the line to it are for. The reach starts again at every touch and at
every stall, so it is a multiplier on this slide rather than a drift that
builds up.

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
stroke instead of writing; lifting on them again sends them alone. Two quick
taps add the side's double-tap key on top of the key under them, so tap tap on
`-T` releases as `-*T`.

## The buzzes

A buzz when a key is taken says a key was taken, which is a thing you can
already see. The buzz that matters is on the **change of cell under the
focus**, because that is what turns a walk into something to feel instead of
something to read.

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
| `⌫` | one character, by code point. Hold it and it repeats |
| `↶` | undo one stroke, which is not the same thing |
| `⌄` | put the keyboard away |
| `⚙` | the settings |

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
| `Comb` | the layout, the lattice, and where a portal lands. No Android, no dictionary |
| `Chorder` | two thumbs, and the rule that turns them into strokes. No Android |
| `Notes` | the record of what was written, and how much of it fits. No Android |
| `CombView` | drawing and touch. Decides nothing |
| `StenoService` | the editor, the insets, and the native handle |
| `stfst-jni` | opens the stack, hands strokes to `stfst-board`, returns the buffer |

`Comb`, `Chorder` and `Notes` are plain Kotlin so they can be tested without a
device, and the tests in `app/src/test` are the ones worth reading first.

## Not carried over

The long-press modifier ring went with the radial layout: it read a direction
from a mark, and there is no mark any more. A modifier picker that fits a comb
is its own piece of work.

The floating specials did come back, as floats. The old board's island and
tombstone did not: the comb's hole does the island's job, and the walk down the
order does the rest.
