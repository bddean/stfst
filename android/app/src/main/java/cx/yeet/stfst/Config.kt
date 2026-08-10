package cx.yeet.stfst

/**
 * Every parameter the keyboard has, in one place.
 *
 * Lengths are fractions of the thumb field's short side, so a setting means the
 * same thing on any screen. Angles are degrees clockwise, zero pointing right,
 * which is what `atan2` gives.
 */
data class Config(
    /** The first key of the right thumb's bank. Everything before it is the left's. */
    var splitKey: Char = '*',

    /** Keys on the long-slide ring, in steno order, one character per key. */
    var farLeft: String = "STKPWHR",
    var farRight: String = "frpblgts",
    /** Keys on the short-slide ring. A key on neither ring cannot be stroked. */
    var nearLeft: String = "AO#@^+",
    var nearRight: String = "*EUdz",

    var slotsFar: Int = 8,
    var slotsNear: Int = 8,

    /**
     * How many columns the widest row of the tap layout has.
     *
     * The tap layout owes the rings nothing: a touch-down has no direction to
     * read, so it is a honeycomb over the whole field and the thumb only has to
     * find the key. Three columns put thirteen keys in rows of 3 2 3 2 3.
     */
    var entryCols: Int = 3,

    /** Where each ring's first key sits. -90 is straight up, 0 is right. */
    var startFarLeft: Float = -90f,
    var startNearLeft: Float = 0f,
    var startFarRight: Float = -90f,
    var startNearRight: Float = -90f,

    /** Shorter than this and a slide has not chosen anything yet. */
    var minSlide: Float = 0.06f,
    /**
     * How far out each ring sits. These are where the keys are drawn *and* what a
     * slide is measured against, and the boundary between them is derived rather
     * than set: a boundary of its own is a boundary that can disagree with what
     * the thumb was aiming at.
     */
    var nearReach: Float = 0.13f,
    var farReach: Float = 0.30f,
    /**
     * Where between the two rings the boundary sits, as a fraction of the gap.
     *
     * Low, because undershooting is what a thumb does. Halfway means a slide has
     * to cover seven tenths of the distance to a far key to count as one, and a
     * thumb that falls short of a consonant gets a vowel instead.
     */
    var ringSplitAt: Float = 0.35f,
    /**
     * How long a near-ring key has to stay the best answer before it is taken.
     *
     * A thumb reaching for a consonant crosses the near band on the way out, and a
     * key taken the moment its threshold is crossed is a vowel the thumb never
     * asked for. A far key has nothing beyond it, so it is taken at once; a near
     * key waits to see whether the thumb was going further. Zero takes both at
     * once, which is the behaviour that made the consonants unreachable.
     */
    var settleMs: Long = 70L,
    /**
     * How far a slide has to turn before it can take another far key.
     *
     * Reaching outward clears the far threshold again from every new mark, so a
     * single long drag would take a second and a third key along the same ray. A
     * new key means a new direction. Under the angle between two slots, or the
     * neighbouring key becomes unreachable.
     */
    var turnDegrees: Float = 30f,
    /** How far off a slot's angle a slide may be. 180 always takes the nearest. */
    var snapDegrees: Float = 180f,

    /**
     * Hold still this long and the ring returns to the field's home point.
     *
     * The only drift remedy, and it has to be: a ring drawn anywhere but the
     * measuring point would send the thumb at an angle nothing measures, so
     * pulling the origin back part way is not available.
     */
    var stallMs: Long = 900L,

    /** Where in its field a thumb's home point sits, as a fraction from the top. */
    var homeAt: Float = 0.62f,

    /**
     * Lifting one thumb writes the stroke, and the thumb still down keeps its
     * keys. That is what spares the right hand from repeating itself while the
     * left spells. False waits for both.
     */
    var commitOnFirstLift: Boolean = true,

    /** Off by default: a hold is easy to do by accident while learning. */
    var longPress: Boolean = false,
    var longPressMs: Long = 400L,

    var haptics: Boolean = true,
    /** Draw the keys steno order has passed, faded. */
    var showDeadSlots: Boolean = true,
    var showLabels: Boolean = true,

    /**
     * How finely the drawn sectors are probed, in degrees.
     *
     * The picture asks the decision function which key each direction takes, so
     * this is the only thing that separates the two. Small is exact and cheap.
     */
    var sectorStep: Float = 2f,
    /** How far out the far ring's sectors are drawn. Beyond it is the same key. */
    var farEdge: Float = 0.46f,

    /**
     * Repeat the ring where the thumb is not covering it.
     *
     * Fixed in the field rather than carried above the thumb, so a ring that has
     * drifted to the edge still has its copy in full view.
     */
    var mirror: Boolean = true,
    /** Where in the field the copy's centre sits, as a fraction from the top. */
    var mirrorAt: Float = 0.28f,
    var mirrorScale: Float = 0.62f,
    /** How much weaker the copy is drawn, so it reads as a copy. */
    var mirrorDim: Float = 0.5f,

    /**
     * Draw every touch sample, so the shape and size of the contact is visible.
     *
     * What the hardware reports about a touch beyond its centre, which is what a
     * hard and light press would have to be told apart by.
     */
    var cloud: Boolean = true,
    var cloudSamples: Int = 400,
    var cloudAlpha: Float = 0.1f,
    /** Print what the driver says about the live touch: pressure, size, axes. */
    var touchReadout: Boolean = true,

    /** Where the left field ends and the right begins. */
    var fieldSplit: Float = 0.5f,
    /** The thumb marks: where the last key was taken, and where the thumb is now. */
    var markRadius: Float = 0.022f,
    var labelSp: Float = 15f,
    var keyboardHeight: Float = 0.42f,
) {
    fun far(bank: Bank) = if (bank == Bank.LEFT) farLeft else farRight
    fun near(bank: Bank) = if (bank == Bank.LEFT) nearLeft else nearRight
    fun slots(radius: Radius) = if (radius == Radius.FAR) slotsFar else slotsNear

    /** Where a ring's keys sit, as a fraction of the field's short side. */
    fun reach(radius: Radius) = if (radius == Radius.FAR) farReach else nearReach

    /** Longer than this and a slide wanted the far ring. */
    fun ringSplit() = nearReach + (farReach - nearReach) * ringSplitAt

    /**
     * The band of distances a ring answers to, which is what a key's region fills.
     *
     * The far ring has no outer edge, so the drawn one is where the picture stops
     * rather than where the answer changes.
     */
    fun bounds(radius: Radius): ClosedFloatingPointRange<Float> =
        if (radius == Radius.NEAR) minSlide..ringSplit() else ringSplit()..farEdge

    fun startAngle(bank: Bank, radius: Radius): Float = when {
        bank == Bank.LEFT && radius == Radius.FAR -> startFarLeft
        bank == Bank.LEFT -> startNearLeft
        radius == Radius.FAR -> startFarRight
        else -> startNearRight
    }
}
