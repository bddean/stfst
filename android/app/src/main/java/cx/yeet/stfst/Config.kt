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
     * Each bank's layout: one character per cell, rows separated by a slash or
     * a newline. A dot is open ground, `o` is a hole, a space is off the board,
     * and anything else is the key of that name.
     *
     * The comb runs down. Alternate columns hang half a cell lower, so a column
     * is a run down the glass and the chain of the steno order zigzags between
     * two of them: a key touches the next in the order diagonally and the one
     * after it directly below. Repeating a value in touching cells melds them
     * into one button, which is all a big key is.
     *
     * A button that touches no other key is a float, and steno order says
     * nothing about one: `#` and `+` here, `@` and `*` on the right. The last
     * keys of each bank ring a hole, so the far end of the order is a dip
     * through the middle rather than a walk down the whole comb.
     */
    var combLeft: String = "#.^S./#.TK./..PW./..HR./..AO./+.oO./+.A..",
    var combRight: String = ".UE.@/.rf.@/.bp../.gl../.st../.zd.*/.od.*/.z...",
    /**
     * The four portal strides, in keys of the steno order.
     *
     * Two of a cell's six sides face the keys the walk can still reach. The
     * other four are portals, and each carries one of these rather than a
     * destination of its own: four numbers to learn instead of one per side per
     * key. The sides take them in order of how far they turn from the way the
     * walk is heading.
     */
    var combStrides: String = "3 5 7 9",
    /**
     * How much the comb is turned, in degrees, portrait and landscape apart.
     *
     * Zero runs the columns straight down the glass. A thumb sweeps along an
     * arc rather than a line, so a few degrees of tilt can put the walk under
     * it; which few is a fact about hands and not about layouts.
     */
    var tiltPortrait: Float = 0f,
    var tiltLandscape: Float = 0f,
    /** How much of the field the comb fills. Less packs it within reach. */
    var combFill: Float = 1f,
    /** Where the middle of the comb sits, as a fraction down the field. */
    var combAt: Float = 0.5f,
    /**
     * A hole takes nothing and a portal can land on one, which is what puts it
     * at the heart of an end cluster: one jump reaches the hole and every key
     * of the cluster round it is then one hop away.
     *
     * This switch adds the second thing a hole can do: a focus that *walks*
     * into one is thrown straight across the comb's middle, so the far end of
     * the order is a dip rather than a walk. Off by default, because the
     * shipped layouts ring their hole with the last keys of the order and a
     * throw from there reaches only cells the order has passed.
     */
    var combHole: Boolean = false,
    /**
     * The keys a lift can arm instead of writing, per bank.
     *
     * A lift that took only these adds them to the next stroke; lifting on them
     * again sends them alone. They are the ones a chord wants alongside a whole
     * outline rather than instead of one.
     */
    var stickyLeft: String = "#+",
    var stickyRight: String = "@*",
    /**
     * The key a double tap adds, per bank, on top of the key under it.
     *
     * Two quick taps stand in for a trip to the far end of the comb. The second
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
     * Hold still this long and the focus comes back to the thumb, writing
     * nothing. What a thumb does when a portal has left the two far apart and
     * it wants its bearings back.
     */
    var stallMs: Long = 900L,

    /**
     * Lifting one thumb writes the stroke, and the thumb still down keeps its
     * keys. That is what spares the right hand from repeating itself while the
     * left spells. False waits for both.
     */
    var commitOnFirstLift: Boolean = true,

    var haptics: Boolean = true,
    /**
     * Buzz when the cell under the focus changes, not only when a key is taken.
     *
     * Where the focus is becomes a thing you can feel instead of read, which is
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

    /** Draw the cells steno order has passed, faded. */
    var showDeadCells: Boolean = true,
    var showLabels: Boolean = true,
    /** Draw the chain of the steno order through the keys, faint. */
    var showChain: Boolean = true,
    /**
     * Draw where each portal out of the cell under the focus goes.
     *
     * A stride is four numbers rather than fifty-two destinations, and this is
     * how the four stop being numbers: leave by that side and the focus goes
     * there. A ring on the landing means arriving takes that key, and a dashed
     * ring means it only comes to rest beside it.
     */
    var showPortals: Boolean = true,

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

    /** Where the left field ends and the right begins, in portrait. */
    var fieldSplit: Float = 0.5f,
    /**
     * Put the two combs on the left and right edges in landscape, with the bar
     * in the gap between them.
     *
     * The comb runs down, so a landscape half wants height. What it cannot
     * have is the app's width: `onComputeInsets` describes the app's room with
     * a top edge alone, so there is no way to ask the app to lay out narrower.
     * The keyboard therefore takes a tall band off the bottom, as any keyboard
     * does, and the app keeps everything above it. Off puts the two combs side
     * by side in the middle instead of on the edges.
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
     * Fill the gap between two edge combs with the record: every stroke, the
     * text it wrote, and the briefer outlines under it.
     *
     * The gap is the only room this keyboard has to spare, and one line is only
     * worth the briefs. Off leaves that one line, in the gap or under the keys.
     */
    var showLog: Boolean = true,
    /** The thumb marks: where the focus is, and where the thumb is now. */
    var markRadius: Float = 0.022f,
    var labelSp: Float = 15f,
    /** The keys' share of the screen in portrait. */
    var keyboardHeight: Float = 0.46f,
) {
    fun sticky(bank: Bank): String = if (bank == Bank.LEFT) stickyLeft else stickyRight

    fun doubleTap(bank: Bank): Char =
        if (bank == Bank.LEFT) doubleTapLeft else doubleTapRight

    fun comb(bank: Bank): String = if (bank == Bank.LEFT) combLeft else combRight

    /** How far the comb is turned, which the two orientations set apart. */
    fun tilt(landscape: Boolean): Float = if (landscape) tiltLandscape else tiltPortrait
}
