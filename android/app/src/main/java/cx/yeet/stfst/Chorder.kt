package cx.yeet.stfst

import kotlin.math.hypot

/** One thumb's progress down its own comb. */
class Thumb(val bank: Bank) {
    var pointer: Int = NONE

    /** The half of the keyboard this thumb walks. */
    var field: Field = Field(0f, 0f, 0f, 0f)

    /** Where the thumb is now. */
    var atX: Float = 0f
    var atY: Float = 0f

    /**
     * How far the focus that reads the cells sits from the thumb. Zero until a
     * portal or a hole parts them, and until the thumb lifts after one.
     */
    var offX: Float = 0f
    var offY: Float = 0f

    val focusX: Float get() = atX + offX
    val focusY: Float get() = atY + offY

    /** Whether the thumb has been placed, so the focus means anything yet. */
    var live: Boolean = false

    val keys: MutableList<Int> = mutableListOf()

    /**
     * The cell the focus is in, or null before the first touch.
     *
     * What makes entering an event: a cell answers when the focus comes in, not
     * on every sample it stays.
     */
    var spot: Spot? = null

    /** Whether the focus is inside a hole, so crossing in fires once. */
    var inHole: Boolean = false

    val down: Boolean get() = pointer != NONE

    /** The last key taken, or -1. Steno order can only go up from here. */
    val after: Int get() = keys.maxOrNull() ?: -1

    fun release() {
        pointer = NONE
        live = false
        offX = 0f
        offY = 0f
        spot = null
        inHole = false
    }

    companion object {
        const val NONE = -1
    }
}

/**
 * Two thumbs, each walking its own comb, and the rule that turns them into
 * strokes.
 *
 * Lifting a thumb writes the stroke. The thumb still down keeps its keys, so
 * spelling a run of words that share a right-hand shape costs one right-hand
 * motion, not one per word.
 */
class Chorder(
    private val comb: Comb,
    private val config: Config,
    private val write: (Long) -> Unit,
) {

    val left = Thumb(Bank.LEFT)
    val right = Thumb(Bank.RIGHT)

    /** Whether the comb is turned for a landscape screen. Set by the view. */
    var landscape: Boolean = false

    /** Keys armed to join the next stroke: a tapped special, or a double tap's. */
    val sticky = mutableSetOf<Int>()

    /** A quick tap's stroke, waiting out the double-tap window. */
    private var held = 0L

    /**
     * Whether a key has been taken since the last stroke was written.
     *
     * A thumb that stays down keeps its keys, so without this its own lift
     * writes them a second time: `Rr` comes out as `Rr` and then `r`. Keeping
     * the keys and asking for a stroke are different things, and only the
     * second one a lift can mean.
     */
    private var pending = false

    private val tilt: Float get() = config.tilt(landscape)

    fun thumb(bank: Bank) = if (bank == Bank.LEFT) left else right

    fun forPointer(pointer: Int): Thumb? = when (pointer) {
        left.pointer -> left
        right.pointer -> right
        else -> null
    }

    /** Give a thumb its half of the keyboard. */
    fun place(bank: Bank, field: Field) {
        thumb(bank).field = field
    }

    /**
     * A thumb touches down, and takes the key it landed on.
     *
     * Nothing here is measured or timed, which is what the missing scale and
     * clock say: a cell is where it is, and a tap on one is finished.
     */
    fun down(bank: Bank, pointer: Int, x: Float, y: Float): Boolean {
        val thumb = thumb(bank)
        if (thumb.down) return false
        thumb.release()
        thumb.pointer = pointer
        thumb.live = true
        thumb.atX = x
        thumb.atY = y
        val spot = comb.hit(bank, thumb.field, tilt, x, y) ?: return false
        thumb.spot = spot
        val key = comb.resolve(spot, thumb.keys) ?: return false
        take(thumb, key)
        return true
    }

    /**
     * A thumb slides. True when it took at least one more key.
     *
     * The whole rule: entering a cell takes what it yields. No gate waits on
     * time, distance or turning, because a cell is a place and not a direction.
     * Asked once per entry, so standing in a cell does not take through it
     * twice, and a spent cell is ground.
     *
     * Read at the focus and not the thumb. The two part at a hole, which throws
     * the focus across the comb's middle, and at a portal, which throws it
     * ahead in the steno order. Either offset holds until the lift.
     */
    fun move(pointer: Int, x: Float, y: Float): Boolean {
        val thumb = forPointer(pointer) ?: return false
        thumb.atX = x
        thumb.atY = y
        var took = false
        val was = thumb.spot
        var now = comb.hit(thumb.bank, thumb.field, tilt, thumb.focusX, thumb.focusY) ?: return false

        if (config.combHole && now.v == Comb.HOLE) {
            if (!thumb.inHole) {
                val middle = comb.middle(thumb.field)
                thumb.offX = 2f * middle.x - thumb.focusX - x
                thumb.offY = 2f * middle.y - thumb.focusY - y
                now = comb.hit(thumb.bank, thumb.field, tilt, thumb.focusX, thumb.focusY) ?: return false
            }
            thumb.inHole = true
        } else {
            thumb.inHole = false
        }

        // A portal is a side of the cell the last key was taken in, so it fires
        // on the way out of that cell and nowhere else.
        if (was != null && now != was && comb.keyOf(was) == thumb.after && thumb.after >= 0) {
            val side = Side.values().firstOrNull {
                Comb.step(was, it) == (now.col to now.row)
            }
            val land = side?.let { comb.portal(thumb.bank, was, it, thumb.keys) }
            if (land != null) {
                val to = comb.point(land.spot, comb.place(thumb.bank, thumb.field, tilt))
                thumb.offX = to.x - x
                thumb.offY = to.y - y
                now = land.spot
                // A portal that aims at a hole means to leave the focus in it,
                // beside the cluster it rings. Falling through as well would
                // undo the jump on the next sample.
                thumb.inHole = now.v == Comb.HOLE
            }
        }

        if (now == thumb.spot) return took
        thumb.spot = now
        val key = comb.resolve(now, thumb.keys)
        if (key != null) {
            take(thumb, key)
            took = true
        }
        return took
    }

    /**
     * A thumb lifts. Returns the stroke written or held, or zero.
     *
     * A lift that took armed keys and nothing else arms them instead of
     * writing: they join the next stroke, and lifting on them again sends them
     * alone. `tap` marks a quick touch, whose stroke waits out the double-tap
     * window in [held] for [flush], so a second tap can still cancel it.
     */
    fun up(pointer: Int, tap: Boolean = false): Long {
        val thumb = forPointer(pointer) ?: return 0L
        thumb.release()
        val other = if (thumb === left) right else left
        val armable = config.sticky(thumb.bank).mapNotNull { c ->
            comb.order.indexOf(c).takeIf { it >= 0 }
        }
        if (thumb.keys.isNotEmpty() && other.keys.isEmpty() && thumb.keys.all { it in armable }) {
            val again = thumb.keys.all { it in sticky }
            sticky.addAll(thumb.keys)
            thumb.keys.clear()
            if (!again) return 0L
        }
        if (!config.commitOnFirstLift && other.down) return 0L
        val stroke = if (pending) mask() else 0L
        pending = false
        thumb.keys.clear()
        if (!other.down) other.keys.clear()
        if (stroke != 0L) {
            if (tap) {
                flush()
                held = stroke
            } else {
                emit(stroke)
            }
        }
        return stroke
    }

    /** Arm a key to join the next stroke, the way a tapped special does. */
    fun stick(key: Int) {
        sticky.add(key)
        pending = true
    }

    /** Send the held stroke on, its double-tap window over. */
    fun flush() {
        val stroke = held
        held = 0L
        if (stroke != 0L) emit(stroke)
    }

    /** Forget the held stroke: its tap was the start of a double tap. */
    fun cancelHeld() {
        held = 0L
    }

    private fun emit(stroke: Long) {
        sticky.clear()
        write(stroke)
    }

    /** Give up everything, for a keyboard that is going away. */
    fun reset() {
        pending = false
        held = 0L
        sticky.clear()
        for (thumb in listOf(left, right)) {
            thumb.keys.clear()
            thumb.release()
        }
    }

    /** Both banks as one bit per key, plus the armed keys: what a stroke is. */
    fun mask(): Long {
        var bits = 0L
        for (key in left.keys + right.keys + sticky) bits = bits or (1L shl key)
        return bits
    }

    /**
     * Bring the focus back to the thumb, writing nothing.
     *
     * What a thumb does when a portal has left the two far apart and it wants
     * its bearings back. The cell under the thumb is not entered by this, or
     * the twitch that ends the stall would spend it on a key nobody asked for.
     */
    fun stall(bank: Bank) {
        val thumb = thumb(bank)
        if (!thumb.down) return
        thumb.offX = 0f
        thumb.offY = 0f
        thumb.inHole = false
        thumb.spot = comb.hit(bank, thumb.field, tilt, thumb.atX, thumb.atY)
    }

    private fun take(thumb: Thumb, key: Int) {
        thumb.keys.add(key)
        pending = true
    }
}
