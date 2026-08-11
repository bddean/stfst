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
     * Each bank's keys on the direction-read ring, in steno order.
     *
     * One ring per thumb, so a slide is read by direction alone and how far it
     * went means nothing beyond `Minimum slide`. Metering a distance under a
     * thumb without looking is the hard channel, and the answer to "how far" was
     * wrong far more often than the answer to "which way". A key in neither
     * string cannot be stroked in this layout.
     */
    var farLeft: String = "#@^+STKPWHRAO",
    var farRight: String = "*EUfrpblgtsdz",

    /**
     * Read every touch against fixed cells, by where it is rather than by where
     * it went.
     *
     * The direction-read ring caps a key's target by how many keys follow it:
     * twelve after the first means 30 degrees is the ceiling. A fixed cell pays
     * in area instead of angle, and area the field has plenty of. What position
     * reading needs in exchange is a rule with no gate in it, and take-on-entry
     * is that rule: any cell the thumb enters is taken, a spent cell is ground,
     * and the ground between cells takes nothing.
     */
    var board: Boolean = true,
    /**
     * Each bank of the board: an island of cells inside a ring of cells, in
     * take order, island first.
     *
     * The island is unordered, the way a machine's thumb cluster is pressed
     * together: its keys go in any order, and the first ring key closes it.
     * That is its point: the walk starts in the middle, leaves toward the ring
     * from whichever island cell faces the exit, and the vacated middle is the
     * road every skip crosses, since a skipped ring key is still live and has
     * to be gone around.
     *
     * The left island is the one deviation from steno order: the vowels come
     * last in the outline, and an island that stays live until then blocks the
     * middle for the whole consonant walk. Taking them first costs nothing the
     * dictionary can see, because a stroke is a set of keys and the outline
     * spelling stays steno order whatever order the thumb walked.
     */
    var boardIslandLeft: String = "AO",
    var boardRingLeft: String = "#^+STKPWHR",
    /** The right island is steno order already: @ * E U are its first keys. */
    var boardIslandRight: String = "@*EU",
    var boardRingRight: String = "frpblgtsdz",
    /**
     * Doorways. `A>R` means a dead A cell takes R, for as long as R can still
     * be taken; after that it is plain ground. Dead island cells are ground the
     * walk crosses anyway, and a destination on one shortens the worst hops of
     * the bank: each pair here is the far ring key its cell's chords most often
     * still owe, measured over the dictionary.
     */
    var boardPortals: String = "A>R O>H @>s *>t E>d U>g",
    /**
     * Cell pitches across the field's short side. Fewer means a bigger board.
     * The radii below are all in pitches, so this is the one scale knob.
     */
    var boardSpan: Float = 4.8f,
    /** An island disc's radius, in pitches. */
    var boardCell: Float = 0.42f,
    /**
     * The ring's inner edge, in pitches.
     *
     * The gap between it and the island discs is the corridor, and the corridor
     * is the board's whole guard: nothing waits on time or turning, so a walk
     * gets past a live slice by going inside it. Outward the ring has no edge a
     * slice can be missed over; only undershooting misses.
     */
    var boardInner: Float = 1.5f,
    /** Where the ring is drawn to. The answer does not change past it. */
    var boardOuter: Float = 2.3f,
    /** Where the middle of the board sits, as a fraction down the field. */
    var boardAt: Float = 0.5f,
    /** Where each board ring's first key sits. -90 is straight up. */
    var boardStartLeft: Float = -90f,
    var boardStartRight: Float = -90f,

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
    var startFarRight: Float = -90f,

    /**
     * Shorter than this and a slide has not chosen anything yet.
     *
     * The only thing distance decides on the ring. It is the whole guard against
     * a resting thumb taking keys.
     */
    var minSlide: Float = 0.085f,
    /**
     * How far out the ring sits: where the keys are drawn *and* what a slide is
     * measured against.
     */
    var farReach: Float = 0.30f,
    /**
     * How far a slide has to turn before it can take another key.
     *
     * Reaching outward clears the threshold again from every new mark, so a
     * single long drag would take a second and a third key along the same ray. A
     * new key means a new direction. Under the angle between two slots, or the
     * neighbouring key becomes unreachable.
     */
    var turnDegrees: Float = 30f,
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

    /**
     * The band of distances the ring answers to, which is what a key's region
     * fills. The ring has no outer edge, so the drawn one is where the picture
     * stops rather than where the answer changes.
     */
    fun bounds(): ClosedFloatingPointRange<Float> = minSlide..farEdge

    fun startAngle(bank: Bank): Float =
        if (bank == Bank.LEFT) startFarLeft else startFarRight
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
