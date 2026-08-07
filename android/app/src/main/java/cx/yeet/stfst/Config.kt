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
    var minSlide: Float = 0.07f,
    /** The boundary between the two rings. */
    var farRadius: Float = 0.17f,
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

    /** Where the left field ends and the right begins. */
    var fieldSplit: Float = 0.5f,
    var keyRadius: Float = 0.055f,
    var labelSp: Float = 15f,
    var keyboardHeight: Float = 0.42f,
) {
    fun far(bank: Bank) = if (bank == Bank.LEFT) farLeft else farRight
    fun near(bank: Bank) = if (bank == Bank.LEFT) nearLeft else nearRight
    fun slots(radius: Radius) = if (radius == Radius.FAR) slotsFar else slotsNear

    fun startAngle(bank: Bank, radius: Radius): Float = when {
        bank == Bank.LEFT && radius == Radius.FAR -> startFarLeft
        bank == Bank.LEFT -> startNearLeft
        radius == Radius.FAR -> startFarRight
        else -> startNearRight
    }
}
