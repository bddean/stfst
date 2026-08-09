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
     * Repeat the ring above the thumb, where the thumb is not covering it.
     */
    var mirror: Boolean = true,
    /** How far above the thumb the copy sits. */
    var mirrorLift: Float = 0.42f,
    var mirrorScale: Float = 0.75f,

    /** Where the left field ends and the right begins. */
    var fieldSplit: Float = 0.5f,
    var keyRadius: Float = 0.055f,
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

    fun startAngle(bank: Bank, radius: Radius): Float = when {
        bank == Bank.LEFT && radius == Radius.FAR -> startFarLeft
        bank == Bank.LEFT -> startNearLeft
        radius == Radius.FAR -> startFarRight
        else -> startNearRight
    }
}
