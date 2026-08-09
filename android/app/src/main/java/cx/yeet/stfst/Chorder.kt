package cx.yeet.stfst

import kotlin.math.atan2

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

    /** Where the thumb is now, so a settling key can be taken without a new event. */
    var atX: Float = 0f
    var atY: Float = 0f

    /** The near key under consideration, and since when. */
    var waiting: Int = NO_KEY
    var waitingSince: Long = 0L

    /** Which way the last key was taken, or NaN before the first one. */
    var heading: Float = Float.NaN

    val down: Boolean get() = pointer != NONE

    /** The last key taken, or -1. Steno order can only go up from here. */
    val after: Int get() = keys.lastOrNull() ?: -1

    fun home() {
        markX = homeX
        markY = homeY
        atX = homeX
        atY = homeY
        waiting = NO_KEY
        heading = Float.NaN
    }

    companion object {
        const val NONE = -1
        const val NO_KEY = -1
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

    /**
     * Whether a key has been taken since the last stroke was written.
     *
     * A thumb that stays down keeps its keys, so without this the lift of that
     * thumb writes them a second time: two strokes from one chord, and `Rr` comes
     * out as `Rr` and then `r`. Keeping the keys and asking for a stroke are
     * different things, and only the second one a lift can mean.
     */
    private var pending = false

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
    fun down(bank: Bank, pointer: Int, x: Float, y: Float, unit: Float, now: Long): Boolean {
        val thumb = thumb(bank)
        if (thumb.down) return false
        thumb.pointer = pointer
        thumb.home()
        return advance(thumb, x, y, unit, now)
    }

    /** A thumb slides. True when it took at least one more key. */
    fun move(pointer: Int, x: Float, y: Float, unit: Float, now: Long): Boolean {
        val thumb = forPointer(pointer) ?: return false
        return advance(thumb, x, y, unit, now)
    }

    /**
     * Ask again at the position the thumb is already at.
     *
     * A thumb that has stopped sends no more events, so the wait a near key serves
     * has to be ended by a clock rather than by movement.
     */
    fun settle(bank: Bank, unit: Float, now: Long): Boolean {
        val thumb = thumb(bank)
        if (!thumb.down) return false
        return advance(thumb, thumb.atX, thumb.atY, unit, now)
    }

    /** The key a thumb would take if it stopped here, for the ring to show. */
    fun waiting(bank: Bank): Int = thumb(bank).waiting

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
        val stroke = if (pending) mask() else 0L
        pending = false
        thumb.keys.clear()
        if (!other.down) other.keys.clear()
        if (stroke != 0L) write(stroke)
        return stroke
    }

    /** Give up everything, for a keyboard that is going away. */
    fun reset() {
        pending = false
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

    /**
     * Take the key this position has chosen, if it has chosen one for long enough.
     *
     * A far key is taken at once, because nothing lies further out for the thumb to
     * be on its way to, but only if the slide has turned since the last key: going
     * further along the same ray is the same reach, not another one. A near key has
     * to still be the answer after `settleMs`, which is what lets a thumb cross the
     * near band on the way to a consonant without collecting a vowel.
     */
    private fun advance(thumb: Thumb, x: Float, y: Float, unit: Float, now: Long): Boolean {
        thumb.atX = x
        thumb.atY = y
        val key = ring.pick(thumb.bank, thumb.after, thumb.markX, thumb.markY, x, y, unit)
        if (key == null) {
            thumb.waiting = Thumb.NO_KEY
            return false
        }
        val heading = Math.toDegrees(
            atan2((y - thumb.markY).toDouble(), (x - thumb.markX).toDouble())
        ).toFloat()
        val near = ring.slotOf(key)?.radius == Radius.NEAR
        if (!near && !thumb.heading.isNaN() &&
            Ring.separation(heading, thumb.heading) < config.turnDegrees
        ) {
            return false
        }
        if (near && config.settleMs > 0) {
            if (key != thumb.waiting) {
                thumb.waiting = key
                thumb.waitingSince = now
                return false
            }
            if (now - thumb.waitingSince < config.settleMs) return false
        }
        thumb.keys.add(key)
        thumb.markX = x
        thumb.markY = y
        thumb.heading = heading
        thumb.waiting = Thumb.NO_KEY
        pending = true
        return true
    }
}
