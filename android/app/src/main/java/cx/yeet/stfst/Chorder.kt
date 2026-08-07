package cx.yeet.stfst

/** One thumb's progress through its own bank. */
class Thumb(val bank: Bank) {
    var pointer: Int = NONE
    var homeX: Float = 0f
    var homeY: Float = 0f
    /**
     * Where the last key was taken, which is what the next slide is measured
     * from and where the ring is drawn.
     *
     * One point for both. Measuring from anywhere else leaves the distance
     * already travelled sitting above the slide threshold, and the next touch
     * sample spends it on a key nobody asked for.
     */
    var markX: Float = 0f
    var markY: Float = 0f
    val keys: MutableList<Int> = mutableListOf()

    val down: Boolean get() = pointer != NONE

    /** The last key taken, or -1. Steno order can only go up from here. */
    val after: Int get() = keys.lastOrNull() ?: -1

    fun home() {
        markX = homeX
        markY = homeY
    }

    companion object {
        const val NONE = -1
    }
}

/**
 * Two thumbs, each walking its own bank, and the rule that turns them into
 * strokes.
 *
 * Lifting a thumb writes the stroke. The thumb still down keeps its keys, so
 * spelling a run of words that share a right-hand shape costs one right-hand
 * motion, not one per word. Both-up is the other option and only changes when
 * the write happens.
 */
class Chorder(private val ring: Ring, private val config: Config, private val write: (Long) -> Unit) {

    val left = Thumb(Bank.LEFT)
    val right = Thumb(Bank.RIGHT)

    fun thumb(bank: Bank) = if (bank == Bank.LEFT) left else right

    fun forPointer(pointer: Int): Thumb? = when (pointer) {
        left.pointer -> left
        right.pointer -> right
        else -> null
    }

    /** Place a field's home point. The fan is measured from it. */
    fun place(bank: Bank, x: Float, y: Float) {
        val thumb = thumb(bank)
        thumb.homeX = x
        thumb.homeY = y
        if (!thumb.down) thumb.home()
    }

    /**
     * A thumb touches down. Where it lands relative to the field's home point is
     * already a slide, so the first key needs no separate layout.
     */
    fun down(bank: Bank, pointer: Int, x: Float, y: Float, unit: Float): Boolean {
        val thumb = thumb(bank)
        if (thumb.down) return false
        thumb.pointer = pointer
        thumb.home()
        return advance(thumb, x, y, unit)
    }

    /** A thumb slides. True when it took at least one more key. */
    fun move(pointer: Int, x: Float, y: Float, unit: Float): Boolean {
        val thumb = forPointer(pointer) ?: return false
        return advance(thumb, x, y, unit)
    }

    /**
     * A thumb lifts. Returns the stroke written, or zero.
     *
     * The lifted thumb always gives up its keys. Whether the other one does is
     * the difference between the two commit rules.
     */
    fun up(pointer: Int): Long {
        val thumb = forPointer(pointer) ?: return 0L
        thumb.pointer = Thumb.NONE
        thumb.home()
        val other = if (thumb === left) right else left
        if (!config.commitOnFirstLift && other.down) return 0L
        val stroke = mask()
        thumb.keys.clear()
        if (!other.down) other.keys.clear()
        if (stroke != 0L) write(stroke)
        return stroke
    }

    /** Give up everything, for a keyboard that is going away. */
    fun reset() {
        for (thumb in listOf(left, right)) {
            thumb.pointer = Thumb.NONE
            thumb.keys.clear()
            thumb.home()
        }
    }

    /** Both banks as one bit per key, which is what a stroke is. */
    fun mask(): Long {
        var bits = 0L
        for (key in left.keys + right.keys) bits = bits or (1L shl key)
        return bits
    }

    /**
     * Send the ring back to the field's home point, writing nothing.
     *
     * What a thumb does when it has drifted and wants its bearings back.
     */
    fun stall(bank: Bank) {
        thumb(bank).home()
    }

    /** Take the key this position has moved far enough to choose, if any. */
    private fun advance(thumb: Thumb, x: Float, y: Float, unit: Float): Boolean {
        val key = ring.pick(thumb.bank, thumb.after, thumb.markX, thumb.markY, x, y, unit)
            ?: return false
        thumb.keys.add(key)
        thumb.markX = x
        thumb.markY = y
        return true
    }
}
