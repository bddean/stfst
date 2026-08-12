package cx.yeet.stfst

import kotlin.math.atan2
import kotlin.math.hypot

/** One thumb's progress through its own bank. */
class Thumb(val bank: Bank) {
    var pointer: Int = NONE
    /** The half of the keyboard this thumb taps in. */
    var field: Field = Field(0f, 0f, 0f, 0f)
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

    /** Where the thumb is now. */
    var atX: Float = 0f
    var atY: Float = 0f

    /**
     * The key this slide chose a moment ago, taken or not.
     *
     * What the ring highlights, what the tick reports, and what a rival key has to
     * beat by a margin to take over.
     */
    var hovering: Int = NO_KEY

    /**
     * The board cell the thumb is in, or NO_KEY on the ground between cells.
     *
     * What makes entering an event: a cell answers when the thumb comes in, not
     * on every sample it stays.
     */
    var cell: Int = NO_KEY

    /**
     * How far the focus that reads the keys sits from the thumb. Zero until a
     * teleport parts them, and until the thumb lifts after one.
     */
    var offX: Float = 0f
    var offY: Float = 0f

    /** Whether the focus is inside the tombstone, so crossing in fires once. */
    var inTomb: Boolean = false

    val focusX: Float get() = atX + offX
    val focusY: Float get() = atY + offY

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
        hovering = NO_KEY
        cell = NO_KEY
        offX = 0f
        offY = 0f
        inTomb = false
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
class Chorder(
    private val ring: Ring,
    private val board: Board,
    private val config: Config,
    private val write: (Long) -> Unit,
) {

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

    /** Give a thumb its half of the keyboard. The home point follows from it. */
    fun place(bank: Bank, field: Field) {
        val thumb = thumb(bank)
        thumb.field = field
        thumb.homeX = field.centerX
        thumb.homeY = field.top + field.height * config.homeAt
        if (!thumb.down) thumb.home()
    }

    /**
     * A thumb touches down, and takes the key it landed on.
     *
     * The tap layout, not the ring: a touch-down has no direction to read, so
     * reading it as a slide from the field's home point crowded thirteen keys
     * around one point and made the first key the hardest one.
     *
     * Nothing here is measured or timed, which is what the missing scale and clock
     * say: a cell is where it is, and a tap on one is finished.
     */
    fun down(bank: Bank, pointer: Int, x: Float, y: Float): Boolean {
        val thumb = thumb(bank)
        if (thumb.down) return false
        thumb.pointer = pointer
        thumb.home()
        thumb.markX = x
        thumb.markY = y
        thumb.atX = x
        thumb.atY = y
        if (config.board) {
            // The nearest cell, not the entered one: a touch-down is an aim at a
            // whole field, so no tap lands on nothing.
            val cell = board.nearest(bank, thumb.field, x, y) ?: return false
            thumb.cell = cell
            val key = board.resolve(cell, thumb.keys) ?: return false
            take(thumb, key, Float.NaN)
            return true
        }
        val key = ring.entryAt(bank, thumb.field, x, y, thumb.after) ?: return false
        take(thumb, key, Float.NaN)
        return true
    }

    /** A thumb slides. True when it took at least one more key. */
    fun move(pointer: Int, x: Float, y: Float, unit: Float): Boolean {
        val thumb = forPointer(pointer) ?: return false
        if (config.board) return enter(thumb, x, y)
        return advance(thumb, x, y, unit)
    }

    /**
     * The key the thumb is over right now, gated by nothing.
     *
     * What the ring lights up and what the tick reports. Aiming is the hard part of
     * a slide, and an answer that shows before the key is taken is the answer to it.
     */
    fun hover(bank: Bank): Int = thumb(bank).hovering

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
     * Bring the ring to the thumb, writing nothing.
     *
     * What a thumb does when it has drifted and wants its bearings back. To the
     * thumb and not to the field's home point, because the mark is the measuring
     * origin: a mark left where the thumb is not counts the gap between them as a
     * slide, and the next twitch spends it on a key nobody asked for.
     */
    fun stall(bank: Bank) {
        val thumb = thumb(bank)
        if (!thumb.down) {
            thumb.home()
            return
        }
        thumb.markX = thumb.atX
        thumb.markY = thumb.atY
        thumb.hovering = Thumb.NO_KEY
        thumb.heading = Float.NaN
    }

    /**
     * The board's whole slide rule: entering a cell takes what it yields.
     *
     * No gate waits on time or turning. The ground between the cells is the
     * guard instead: crossing the field without taking is done between them,
     * and a spent cell is itself ground. Asked once per entry, not per sample,
     * so standing in a cell does not take through it twice.
     *
     * Read at the focus, not the thumb, and they part at the tombstone:
     * crossing into it throws the focus straight across the board's middle,
     * and the offset holds until the lift, so the far side of the ring is a
     * dip through the island. The crossing takes nothing, and going back
     * through throws the focus home again.
     */
    private fun enter(thumb: Thumb, x: Float, y: Float): Boolean {
        thumb.atX = x
        thumb.atY = y
        var crossed = false
        val tomb = board.tomb(thumb.field)
        if (tomb > 0f && board.closed(thumb.bank, thumb.keys)) {
            val middle = board.center(thumb.field)
            val inside =
                hypot(thumb.focusX - middle.x, thumb.focusY - middle.y) <= tomb
            if (inside && !thumb.inTomb) {
                thumb.offX = 2f * middle.x - thumb.focusX - x
                thumb.offY = 2f * middle.y - thumb.focusY - y
                crossed = true
            }
            thumb.inTomb = inside
        }
        val cell = board.hit(thumb.bank, thumb.field, thumb.focusX, thumb.focusY)
        if (cell == null) {
            thumb.cell = Thumb.NO_KEY
            return crossed
        }
        if (cell == thumb.cell) return crossed
        thumb.cell = cell
        val key = board.resolve(cell, thumb.keys) ?: return crossed
        thumb.markX = thumb.focusX
        thumb.markY = thumb.focusY
        take(thumb, key, Float.NaN)
        return true
    }

    /**
     * Take the key this position has chosen.
     *
     * At once, because nothing lies further out for the thumb to be on its way
     * to, but only if the slide has turned since the last key: going further
     * along the same ray is the same reach, not another one.
     */
    private fun advance(thumb: Thumb, x: Float, y: Float, unit: Float): Boolean {
        thumb.atX = x
        thumb.atY = y
        val key = ring.pick(
            thumb.bank, thumb.after, thumb.markX, thumb.markY, x, y, unit, thumb.hovering
        )
        thumb.hovering = key ?: Thumb.NO_KEY
        if (key == null) return false
        val heading = Math.toDegrees(
            atan2((y - thumb.markY).toDouble(), (x - thumb.markX).toDouble())
        ).toFloat()
        if (!thumb.heading.isNaN() &&
            Ring.separation(heading, thumb.heading) < config.turnDegrees
        ) {
            return false
        }
        thumb.markX = x
        thumb.markY = y
        take(thumb, key, heading)
        return true
    }

    /**
     * Add a key and start the next slide from here.
     *
     * A NaN heading means there is no direction to turn away from, which is what a
     * touch-down leaves behind: the first slide of a stroke may go any way.
     */
    private fun take(thumb: Thumb, key: Int, heading: Float) {
        thumb.keys.add(key)
        thumb.heading = heading
        thumb.hovering = Thumb.NO_KEY
        pending = true
    }
}
