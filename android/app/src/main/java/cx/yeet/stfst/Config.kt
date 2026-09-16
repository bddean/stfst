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
     * spaces. A slot is one character, or several keys between slashes, and a
     * dot is ground.
     *
     * Everything else is recovered from the text. The walk is steno order over
     * the keys it names; the rim is the outermost ring, and its keys are the
     * floats, which is why they repeat round it; a key in two slots is nothing
     * special, because a key is a set of cells.
     */
    var ringLeft: String = "S/ST/T PHRWK AOAOAO",
    var ringRight: String = "E/EU/U frbglp tststs",
    /**
     * The modifiers, one key to a slot, left to right under the rings.
     *
     * They are off the rings because steno order cannot kill one: a key no walk
     * can spend is a key no walk should have to reach. A tap on one arms it for
     * the next stroke, and holding it puts it in the stroke in hand.
     */
    var modsLeft: String = "^ # +",
    var modsRight: String = "* @ > d z",
    /**
     * The upgrade: what each key becomes while it is on, in pairs.
     *
     * The pinky writes `-D` for `-T` and `-Z` for `-S`, one key at a time, so
     * the row carries one bit for the whole stroke rather than a ring of its
     * own. A stroke that wants both a key and its upgrade takes the plain `-D`
     * or `-Z` of the row instead.
     */
    var upgrade: String = "td sz",
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
    var keyWidth: Float = 0.22f,
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
    /**
     * Two taps this close together are a double tap. A quick tap's stroke waits
     * this long before it goes out, which is what a second tap can cancel. Zero
     * turns double taps off, and quick taps stop waiting.
     */
    var doubleTapMs: Long = 250L,
    /**
     * The key a long press adds, per bank, on top of the key under it.
     *
     * The other way to a modifier without a trip to the row, and the one a
     * thumb already on its key can take.
     */
    var longPressLeft: Char = '#',
    var longPressRight: Char = '@',
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
     * Buzz when the cell under the thumb changes, not only when a key is taken.
     *
     * Where the thumb is becomes a thing you can feel instead of read, which is
     * what a keyboard used without looking needs.
     */
    var hapticTicks: Boolean = true,
    /**
     * Give the two thumbs buzzes that feel different.
     *
     * A phone has one vibrator, so this is not a left channel and a right
     * channel. It is two voices from the same motor, and which two depends on
     * what the hardware offers: two motors if the device has them, else a low
     * tick against a sharp one, else a weak buzz against a strong one, else one
     * pulse against two. The settings screen prints what this device did.
     */
    var hapticSplit: Boolean = true,
    /** How hard, where the motor allows a choice. */
    var hapticStrength: Float = 0.55f,
    /** No two ticks closer together than this, or a walk is one long buzz. */
    var hapticGapMs: Long = 28L,

    /** Draw the cells steno order has passed, sunk towards the ground and struck through. */
    var showDeadCells: Boolean = true,
    var showLabels: Boolean = true,

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
    var previewHeight: Float = 0f,
    /** The mark that shows where the thumb is. */
    var markRadius: Float = 0.022f,
    var labelSp: Float = 15f,
    /** The keys' share of the screen in portrait. */
    var keyboardHeight: Float = 0.46f,
) {
    fun sticky(bank: Bank): String = if (bank == Bank.LEFT) stickyLeft else stickyRight

    fun mods(bank: Bank): String = if (bank == Bank.LEFT) modsLeft else modsRight

    /** The upgrade as pairs of glyphs: what each key writes while it is on. */
    fun upgrades(): List<Pair<Char, Char>> = upgrade.trim().split(Regex("\\s+"))
        .filter { it.length == 2 }.map { it[0] to it[1] }

    fun doubleTap(bank: Bank): Char =
        if (bank == Bank.LEFT) doubleTapLeft else doubleTapRight

    fun longPress(bank: Bank): Char =
        if (bank == Bank.LEFT) longPressLeft else longPressRight

    fun ring(bank: Bank): String = if (bank == Bank.LEFT) ringLeft else ringRight

    fun radii(bank: Bank): String = if (bank == Bank.LEFT) radiiLeft else radiiRight
}
