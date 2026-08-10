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

    /**
     * Which keys sit on which ring, in steno order, one character per key.
     *
     * Every key on one ring by default, and the near ring empty, so a slide is read
     * by direction alone and how far it went means nothing beyond `Minimum slide`.
     * Metering a distance under a thumb without looking is the hard channel: two
     * rings ask for it on every flick, and the answer to "how far" was wrong far more
     * often than the answer to "which way".
     *
     * Nothing is lost by it. The narrowest target a key can ever have is set by how
     * many keys still follow it, not by the layout: twelve keys follow the first, so
     * 30 degrees is the ceiling for any arrangement of them, and one shared ring
     * reaches that ceiling in every state. Two rings bought room by adding a channel
     * that cost more than the room was worth.
     *
     * Put keys in a near string and the short slide comes back, along with `Settle`
     * and the ring boundary.
     */
    var farLeft: String = "#@^+STKPWHRAO",
    var farRight: String = "*EUfrpblgtsdz",
    /** A key on neither ring cannot be stroked. */
    var nearLeft: String = "",
    var nearRight: String = "",

    /**
     * Share each ring out evenly between the keys still live on it.
     *
     * The earliest sits at the start angle and the rest follow clockwise, so the
     * order round the ring is still the order of the chord and still owes the
     * dictionary nothing. What it buys is the worst target: with fixed slots, a key
     * with a live neighbour on each side is squeezed to the gap between them, and
     * three live keys on an eight slot ring can leave the middle one 45 degrees
     * against 157 for each neighbour. Spread, all three hold 120.
     *
     * The cost is that a key's direction depends on what the stroke has taken, so
     * the thing to learn is "the third key still available is down-left" rather
     * than "K is at 0". Off restores fixed slots, and [slotsFar] and [slotsNear].
     */
    var spread: Boolean = true,

    /** Slots per ring, when [spread] is off. Ignored when it is on. */
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
    /**
     * How wide and how tall a cell is, against a honeycomb that fills the field.
     *
     * One fills it. Less packs the keys closer together, which is what a thumb that
     * cannot reach the top of the field wants, and the cells at the edge then grow
     * to cover the field the lattice no longer reaches: a cell is the ground nearer
     * that key than any other, and shrinking the lattice does not change what a tap
     * out there means. Row and column separately, because a field taller than it is
     * wide gives cells taller than they are wide.
     */
    var entryWide: Float = 1f,
    var entryTall: Float = 1f,
    /** Where the middle of the honeycomb sits, as a fraction down the field. */
    var entryAt: Float = 0.5f,
    /**
     * Fill the middle rows of the honeycomb first, so the earliest keys sit there.
     *
     * An early key is where the sliding starts, since most of the stroke is still
     * ahead of it, and a thumb at the edge of the glass has nowhere to slide to.
     * Off fills the rows from the top, which is plain reading order.
     */
    var entryCentral: Boolean = true,

    /** Where each ring's first key sits. -90 is straight up, 0 is right. */
    var startFarLeft: Float = -90f,
    /**
     * Off the axes, because after any consonant this ring holds A and O alone.
     *
     * Two keys put their boundary at the start angle plus and minus 90, and at 0
     * that is straight up and straight down, where the consonants are: an undershot
     * reach upward then lands on the boundary and picks a vowel by coin toss. On the
     * diagonal the boundary misses the consonants of every common state.
     *
     * The right near ring holds three keys after E, so its boundaries move with the
     * count and no one offset clears them. It keeps the plain angle.
     */
    var startNearLeft: Float = -45f,
    var startFarRight: Float = -90f,
    var startNearRight: Float = -90f,

    /**
     * Shorter than this and a slide has not chosen anything yet.
     *
     * The only thing distance decides, once there is one ring. It is the whole guard
     * against a resting thumb taking keys, so it is longer than it was when a short
     * slide had a meaning of its own.
     */
    var minSlide: Float = 0.085f,
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
     * How much better a rival key has to be before a slide changes its answer.
     *
     * A thumb resting on the boundary between two keys is nearer first one and then
     * the other on every touch sample, so the highlight and the tick swap back and
     * forth and neither says anything. The key already chosen keeps the answer
     * inside this margin. Zero takes the nearest every time.
     */
    var hysteresisDegrees: Float = 6f,

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
    /**
     * Buzz when the key under the thumb changes, not only when one is taken.
     *
     * The aim of a slide is a thing you can then feel instead of read, which is
     * what a keyboard used without looking needs.
     */
    var hapticTicks: Boolean = true,
    /**
     * Give the two thumbs buzzes that feel different.
     *
     * A phone has one vibrator, so this is not a left channel and a right channel.
     * It is two voices from the same motor, and which two depends on what the
     * hardware offers: two motors if the device has them, else a low tick against a
     * sharp one, else a weak buzz against a strong one, else one pulse against two.
     * The settings screen prints what this device actually did.
     */
    var hapticSplit: Boolean = true,
    /** How hard, where the motor allows a choice. */
    var hapticStrength: Float = 0.55f,
    /** No two ticks closer together than this, or a slide is one long buzz. */
    var hapticGapMs: Long = 28L,
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
     * Where the copy of the ring goes.
     *
     * It has to go somewhere the thumb is not, and the field is no longer that
     * somewhere: a honeycomb that fills the field leaves nothing for a copy to sit
     * on top of.
     */
    var preview: Preview = Preview.OVER,
    /** Where in the field the copy sits, when it sits in the field. */
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

    /** The keys on one ring of one bank, in steno order. */
    fun keys(bank: Bank, radius: Radius) = if (radius == Radius.FAR) far(bank) else near(bank)

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
    fun bounds(bank: Bank, radius: Radius): ClosedFloatingPointRange<Float> = when {
        radius == Radius.NEAR -> minSlide..ringSplit()
        // One ring, so it owns every distance a slide can be. The boundary between
        // the rings is not a fact about this layout, and drawing one would invent it.
        near(bank).isEmpty() -> minSlide..farEdge
        else -> ringSplit()..farEdge
    }

    fun startAngle(bank: Bank, radius: Radius): Float = when {
        bank == Bank.LEFT && radius == Radius.FAR -> startFarLeft
        bank == Bank.LEFT -> startNearLeft
        radius == Radius.FAR -> startFarRight
        else -> startNearRight
    }
}

/**
 * Where the copy of the ring goes.
 *
 * [OVER] floats it over the app instead of taking room from it: the keyboard's
 * window reaches above the keys, and the app is told to lay out underneath, so the
 * strip costs nothing while no thumb is down and covers nothing that is being read.
 * [BAND] takes the room instead, and pushes the app up out of the way.
 */
enum class Preview(val label: String) {
    OFF("Off"),
    FIELD("In the thumb field"),
    BAND("In a strip above the keys"),
    OVER("Floating over the app"),
    ;

    /** Whether the copy gets a strip of its own above the keys. */
    val strip: Boolean get() = this == BAND || this == OVER
}
