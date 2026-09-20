package cx.yeet.stfst

/**
 * Every parameter the keyboard has, in one place.
 *
 * Lengths are fractions of the thumb field unless a comment says otherwise, so
 * a setting means the same thing on any screen. Angles are degrees clockwise,
 * zero pointing right, which is what `atan2` gives.
 */
data class Config(
    /**
     * Each bank's layout: the hub first, then the rings outward, separated by
     * spaces. A slot is one character, or several keys between slashes, a dot
     * is ground, and [Ring.UPGRADE] is the upgrade rather than a key.
     *
     * Everything else is recovered from the text. The walk is steno order over
     * the keys it names; a key in two slots is nothing special, because a key
     * is a set of cells; and a slot no walk can spend is drawn as a stud
     * between its neighbours, which is why the outer rings carry one between
     * each group of repeats.
     */
    var ringLeft: String = "S/ST/T PHRWK +AO+AO+AO+AO+AO",
    var ringRight: String = "E/EU/U frbglp >ts>ts>ts>ts>ts>ts",
    /**
     * The modifiers, one key to a slot, left to right under the rings.
     *
     * They are off the rings because steno order cannot kill one: a key no walk
     * can spend is a key no walk should have to reach. A tap on one arms it for
     * the next stroke, and holding it puts it in the stroke in hand.
     */
    var modsLeft: String = "^ #",
    var modsRight: String = "* @ d z",
    /**
     * The keys steno order cannot kill, whatever a walk has taken.
     *
     * A walk may take one at any point, so a float is safe anywhere: in the
     * row, or on a ring between the keys it sits among.
     */
    var floats: String = "\uD83E\uDD90#@^+*",
    /**
     * The key a backwards turn round the outer ring takes, per bank.
     *
     * The outer ring is the last of the walk, and its keys repeat round it, so
     * a chord never has to turn backwards there. A turn that takes nothing is
     * therefore free to mean something else.
     */
    var turnBackLeft: Char = '^',
    var turnBackRight: Char = '*',
    /**
     * The key a pull inwards off the outer ring takes, per bank.
     *
     * Nothing inside the outer ring is still live once the thumb is out there,
     * so a pull back in is the second motion no chord needs.
     */
    var pullInLeft: Char = '#',
    var pullInRight: Char = '@',
    /**
     * The upgrade: what each key becomes while it is on, in pairs.
     *
     * The pinky writes `-D` for `-T` and `-Z` for `-S`, one key at a time, so
     * the row carries one bit for the whole stroke rather than a ring of its
     * own. A stroke that wants both a key and its upgrade takes the plain `-D`
     * or `-Z` of the row instead.
     */
    var upgrade: String = "td sz",
    /**
     * A stud's width, as a share of [keyWidth], and how far out of its band's
     * middle it stands, as a share of the band's half depth.
     *
     * A cell no walk can spend is drawn as a stud: a circle between the keys
     * of its ring and a hair further out, so a thumb turning along the ring
     * passes under it rather than through it.
     */
    var studWidth: Float = 0.7f,
    var studOut: Float = 0.42f,
    /** The row's share of a bank's height. */
    var rowHeight: Float = 0.13f,
    /**
     * Keep each bank's rings square, and take only the depth that needs.
     *
     * A thumb sweeps an arc, and a ring stretched to a tall bank answers the
     * same sweep with different keys at different angles.
     */
    var squareBanks: Boolean = true,
    /**
     * The radii between the bands, outward, as fractions of the bank's reach.
     *
     * One more number than there are bands. The radial budget in portrait is
     * the tightest thing about this layout, which is why the numbers are here
     * rather than in the geometry.
     */
    var radiiLeft: String = "0 .34 .67 1",
    var radiiRight: String = "0 .34 .67 1",
    /**
     * The superellipse exponent at the hub and at the rim: 2 is a circle, and
     * the larger it grows the closer a ring comes to a rectangle.
     *
     * It rises outward, so the middle stays round, where a thumb turns, and the
     * outer rings take the corners of the bank, where the room is. An outer
     * ring is therefore the widest, which is what the keys out there need.
     */
    var shapeHub: Float = 2.6f,
    var shapeRim: Float = 3.2f,
    /**
     * A ring key's width, as a fraction of the bank's reach.
     *
     * One width for every ring, so the ground between two rings grows outward
     * as the rings themselves do. The ground is what a slide passes through on
     * its way to a further key, which is what a narrow key buys.
     */
    var keyWidth: Float = 0.26f,
    /**
     * How much of its slot a ring key's body runs along: 0 is a circle, 1 fills
     * the slot and leaves its neighbours no ground between them.
     */
    var keySpread: Float = 0.45f,
    /**
     * The width left at a key's ends, as a fraction of [keyWidth]. 1 is a
     * rounded end, and less tapers the key towards a point, which widens the
     * corridor a slide takes between two keys of one ring.
     */
    var keyEnds: Float = 1f,
    /**
     * How far the rings are turned, in degrees.
     *
     * A thumb sweeps an arc and the rings are already arcs, so this may be
     * unnecessary; it is one rotation either way. The right bank turns the
     * other way, because the two thumbs sweep mirrored arcs.
     */
    var tilt: Float = 0f,
    /**
     * The keys a lift can arm instead of writing, per bank.
     *
     * A lift that took only these adds them to the next stroke; lifting on them
     * again sends them alone. They are the ones a chord wants alongside a whole
     * outline rather than instead of one.
     *
     * `*` and `+` are not among them. A bare `*` is the undo stroke, and arming
     * a key is not undoing anything, so a tap on it has to write it.
     */
    var stickyLeft: String = "#",
    var stickyRight: String = "@",
    /**
     * The key a double tap adds, per bank, on top of the key under it.
     *
     * Two quick taps stand in for a trip to the far end of the bank. The second
     * tap still strokes as itself, so tap tap on `-T` releases as `-*T`.
     */
    var doubleTapLeft: Char = '+',
    var doubleTapRight: Char = '*',
    var doubleTaps: Boolean = true,
    /**
     * Two taps this close together are a double tap. A quick tap's stroke waits
     * this long before it goes out, which is what a second tap can cancel.
     */
    var doubleTapMs: Long = 250L,
    /**
     * The key a long press adds, per bank, on top of the key under it.
     *
     * The other way to a modifier without a trip to the row, and the one a
     * thumb already on its key can take.
     */
    var longPresses: Boolean = false,
    var longPressLeft: Char = '^',
    var longPressRight: Char = '*',
    /** How long a touch must stand still on its cell to count as a long press. */
    var longPressMs: Long = 400L,

    /**
     * Lifting one thumb writes the stroke, and the thumb still down keeps its
     * keys. That is what spares the right hand from repeating itself while the
     * left spells. False waits for both.
     */
    var commitOnFirstLift: Boolean = true,

    var haptics: Boolean = true,
    /**
     * What the motor says on each of the three occasions, per thumb, and how
     * hard.
     *
     * A phone has one vibrator, so a voice per thumb is not a left channel and
     * a right channel. It is two buzzes from the same motor, and the difference
     * carries whichever way the hardware allows: a low tick against a sharp
     * one, a weak pulse against a strong one, one pulse against two. [Haptics]
     * holds what each name comes to, and the settings screen offers only the
     * ones this device can make.
     *
     * The buzz that matters is the one on the change of cell, because that is
     * what turns a slide into something to feel instead of something to read.
     */
    var tickOn: Boolean = true,
    var tickPower: Float = 0.55f,
    var tickLeft: String = "low tick",
    var tickRight: String = "tick",
    var takeOn: Boolean = true,
    var takePower: Float = 1f,
    var takeLeft: String = "low tick",
    var takeRight: String = "click",
    var strokeOn: Boolean = true,
    var strokePower: Float = 1f,
    var strokeLeft: String = "click",
    var strokeRight: String = "click",
    /** No two ticks closer together than this, or a walk is one long buzz. */
    var hapticGapMs: Long = 28L,

    /** Draw the cells steno order has passed, sunk towards the ground and struck through. */
    var showDeadCells: Boolean = true,
    var showLabels: Boolean = true,
    /**
     * The board's two colours: `light`, `dark`, or `system` for whichever the
     * phone is set to.
     *
     * The board has no hue, so a theme is the ground and the ink and nothing
     * else. Every other colour on it is one of the two, faded.
     */
    var theme: String = "system",
    /**
     * The dither on a cell's face, and how far apart its dots are: the fine one
     * is a vowel and the coarse one a modifier.
     *
     * What a key is shows in the texture of its face rather than in a hue, and
     * a dot's size follows its spacing, so one number is the whole of a grain.
     */
    var dither: Boolean = true,
    var grainFine: Float = 0.019f,
    var grainCoarse: Float = 0.026f,
    /**
     * Draw the two moves off the outer ring, from where the thumb is to where
     * each would fire, and mark the row keys they take.
     */
    var showMoves: Boolean = true,
    /** The row of modifiers under the rings. Off gives its depth to the rings. */
    var showRow: Boolean = true,
    /**
     * The briefs under the keys: a shorter way to write what was just written.
     *
     * One line, and the briefs alone, because a stroke you have just made is
     * not news. How many fit on the line, and how long a quiet spell clears it.
     */
    var briefs: Boolean = true,
    var briefsMost: Int = 8,
    var briefsIdleMs: Long = 60_000L,

    /**
     * Draw every touch sample, so the shape and size of the contact is visible.
     *
     * What the hardware reports about a touch beyond its centre, which is what
     * a hard and a light press would have to be told apart by.
     */
    var cloud: Boolean = false,
    var cloudSamples: Int = 400,
    var cloudAlpha: Float = 0.1f,
    /** Print what the driver says about the live touch: pressure, size, axes. */
    var touchReadout: Boolean = false,

    /**
     * A touch that arrives in the other bank's field is handed over to it.
     *
     * The bank it leaves keeps what it took, so one hand can write a whole
     * stroke, slowly. It happens only while the other thumb is up, so two hands
     * never meet it.
     */
    var oneThumb: Boolean = true,
    /** Where the left field ends and the right begins, in portrait. */
    var fieldSplit: Float = 0.5f,
    /** The ground kept round each bank, as a share of its field's width. */
    var bankPad: Float = 0f,
    /**
     * Where a bank sits in a field too wide for it: 0 against the thumb's own
     * edge, 1 against the middle.
     *
     * An uneven split leaves one field wider than a square bank can use, and
     * the slack is on the side the thumb does not start from.
     */
    var bankPull: Float = 0f,
    /**
     * Put the two banks on the left and right edges in landscape, with the bar
     * in the gap between them.
     *
     * The keyboard takes a band off the bottom, as any keyboard does:
     * `onComputeInsets` describes the app's room with a top edge alone, so
     * there is no way to ask the app to lay out narrower and keep the banks
     * full height beside it. Off puts the two side by side in the middle
     * instead of on the edges.
     */
    var landscapeSides: Boolean = true,
    /**
     * Each field's share of the width in landscape, anchored to its own side.
     * 0.5 closes the gap between them.
     */
    var splitLandscape: Float = 0.3f,
    /**
     * The keys' share of the screen in landscape.
     *
     * The whole height would leave the app nothing, and covering the app is
     * what this stopped doing, so the band is deep rather than total.
     */
    var landscapeHeight: Float = 0.72f,
    /**
     * A copy of the banks, drawn over the app above the keys.
     *
     * Its share of the screen, zero for none. What it buys is a small
     * keyboard: shrink the keys and a thumb crosses cells in less travel, and
     * the copy over the app keeps the rings legible while it does. It is drawn
     * only while a thumb is down, and it takes no touches, so the app under it
     * still works.
     */
    var band: Boolean = false,
    var previewHeight: Float = 0.25f,
    /** The mark that shows where the thumb is. */
    var markRadius: Float = 0.022f,
    var labelSp: Float = 15f,
    /** The keys' share of the screen in portrait. */
    var keyboardHeight: Float = 0.46f,
) {
    fun sticky(bank: Bank): String = if (bank == Bank.LEFT) stickyLeft else stickyRight

    /** Whether this occasion buzzes at all, which the master switch also answers. */
    fun buzzes(on: Buzz): Boolean = haptics && when (on) {
        Buzz.TICK -> tickOn
        Buzz.TAKE -> takeOn
        Buzz.STROKE -> strokeOn
    }

    fun power(on: Buzz): Float = when (on) {
        Buzz.TICK -> tickPower
        Buzz.TAKE -> takePower
        Buzz.STROKE -> strokePower
    }

    /** What the motor says on this occasion, in this thumb's voice. */
    fun voice(on: Buzz, bank: Bank): String = when (on) {
        Buzz.TICK -> if (bank == Bank.LEFT) tickLeft else tickRight
        Buzz.TAKE -> if (bank == Bank.LEFT) takeLeft else takeRight
        Buzz.STROKE -> if (bank == Bank.LEFT) strokeLeft else strokeRight
    }

    /** The double tap window, or nothing while double taps are off. */
    fun doubleTapWindow(): Long = if (doubleTaps) doubleTapMs else 0L

    /** How long a long press waits, or nothing while long presses are off. */
    fun longPressWait(): Long = if (longPresses) longPressMs else 0L

    /** The band over the app's share of the screen, zero while it is off. */
    fun bandShare(): Float = if (band) previewHeight else 0f

    fun mods(bank: Bank): String = if (bank == Bank.LEFT) modsLeft else modsRight

    /** The upgrade as pairs of glyphs: what each key writes while it is on. */
    fun upgrades(): List<Pair<Char, Char>> = upgrade.trim().split(Regex("\\s+"))
        .filter { it.length == 2 }.map { it[0] to it[1] }

    fun doubleTap(bank: Bank): Char =
        if (bank == Bank.LEFT) doubleTapLeft else doubleTapRight

    fun longPress(bank: Bank): Char =
        if (bank == Bank.LEFT) longPressLeft else longPressRight

    fun ring(bank: Bank): String = if (bank == Bank.LEFT) ringLeft else ringRight

    fun turnBack(bank: Bank): Char = if (bank == Bank.LEFT) turnBackLeft else turnBackRight

    fun pullIn(bank: Bank): Char = if (bank == Bank.LEFT) pullInLeft else pullInRight

    fun radii(bank: Bank): String = if (bank == Bank.LEFT) radiiLeft else radiiRight

    companion object {
        /** No key: what a shortcut is set to when it is off. */
        const val NONE: Char = ' '
    }
}

/** The three occasions the motor has something to say about. */
enum class Buzz { TICK, TAKE, STROKE }
