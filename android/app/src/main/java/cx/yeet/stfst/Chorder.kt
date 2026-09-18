package cx.yeet.stfst

/** One thumb's progress round its own rings. */
class Thumb(val bank: Bank) {
    var pointer: Int = NONE

    /** The half of the keyboard this thumb walks. */
    var field: Field = Field(0f, 0f, 0f, 0f)

    /** Where the thumb is now. */
    var atX: Float = 0f
    var atY: Float = 0f

    /** Whether the thumb has been placed, so where it is means anything yet. */
    var live: Boolean = false

    val keys: MutableList<Int> = mutableListOf()

    /**
     * The cell the thumb is in, or null before the first touch.
     *
     * What makes entering an event: a cell answers when the thumb comes in, not
     * on every sample it stays.
     */
    var cell: Cell? = null

    val down: Boolean get() = pointer != NONE

    fun release() {
        pointer = NONE
        live = false
        cell = null
    }

    companion object {
        const val NONE = -1
    }
}

/**
 * Two thumbs, each walking its own rings, and the rule that turns them into
 * strokes.
 *
 * Lifting a thumb writes the stroke. The thumb still down keeps its keys, so
 * spelling a run of words that share a right-hand shape costs one right-hand
 * motion, not one per word.
 */
class Chorder(
    private val ring: Ring,
    private val config: Config,
    private val write: (Long) -> Unit,
) {

    val left = Thumb(Bank.LEFT)
    val right = Thumb(Bank.RIGHT)

    /** Keys armed to join the next stroke: a tapped special, or a double tap's. */
    val sticky = mutableSetOf<Int>()

    /**
     * The upgrade: one bit over the whole stroke, which writes `-D` for every
     * `-T` it holds and `-Z` for every `-S`.
     *
     * The pinky presses those one at a time, so what the board needs is a mode
     * and not a key. A stroke wanting `-T` and `-D` at once takes the row's
     * plain `-D` instead.
     */
    var upgrade = false
        private set

    /** What a key writes now: itself, or what the upgrade lifts it to. */
    fun lift(key: Int): Int = if (upgrade) lifted[key] ?: key else key

    private val lifted: Map<Int, Int> = config.upgrades().mapNotNull { (from, to) ->
        val a = ring.keys.indexOf(from)
        val b = ring.keys.indexOf(to)
        if (a >= 0 && b >= 0) a to b else null
    }.toMap()

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
        return land(thumb, pointer, x, y)
    }

    /**
     * A fresh touch starts this thumb's walk, and takes the keys it landed on.
     *
     * The hub is a pie of complete answers, so landing on one takes the whole
     * slice. It is the only way into the hub that takes anything: crossing it
     * later takes nothing, which is what makes the middle a shortcut.
     */
    private fun land(thumb: Thumb, pointer: Int, x: Float, y: Float): Boolean {
        thumb.pointer = pointer
        thumb.live = true
        thumb.atX = x
        thumb.atY = y
        val cell = ring.hit(thumb.bank, thumb.field, x, y)
        thumb.cell = cell
        if (ring.isUpgrade(thumb.bank, cell)) return turn()
        return take(thumb, ring.taking(thumb.bank, cell, thumb.keys))
    }

    /**
     * A thumb slides. True when it took at least one more key.
     *
     * The whole rule: entering a live cell takes it. No gate waits on time,
     * distance or turning, because a cell is a place and not a direction. Asked
     * once per entry, so standing in a cell does not take through it twice, and
     * a spent cell is ground.
     *
     * The hub is ground once the thumb has left it, so a chord through the
     * middle joins two cells too far apart on one ring to reach any other way.
     */
    fun move(pointer: Int, x: Float, y: Float): Boolean {
        val thumb = forPointer(pointer) ?: return false
        thumb.atX = x
        thumb.atY = y
        val now = ring.hit(thumb.bank, thumb.field, x, y)
        if (now == thumb.cell) return false
        thumb.cell = now
        if (ring.isHub(now)) return false
        if (ring.isUpgrade(thumb.bank, now)) return turn()
        return take(thumb, ring.taking(thumb.bank, now, thumb.keys))
    }

    /**
     * One thumb walks the other bank too.
     *
     * A touch that leaves its own field and arrives in the other one is handed
     * over: the bank it leaves keeps what it took, and the bank it arrives in
     * takes the touch. Two hands never need this, so it happens only while the
     * other thumb is up, and one hand can then write a whole stroke.
     */
    fun cross(pointer: Int, into: Bank, x: Float, y: Float): Boolean {
        val from = forPointer(pointer) ?: return false
        val to = thumb(into)
        if (from === to || to.down) return false
        from.release()
        return land(to, pointer, x, y)
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
            ring.keys.indexOf(c).takeIf { it >= 0 }
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

    /** Turn the upgrade on, or off again on a second entry. */
    private fun turn(): Boolean {
        upgrade = !upgrade
        pending = true
        return true
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
        upgrade = false
        write(stroke)
    }

    /** Give up everything, for a keyboard that is going away. */
    fun reset() {
        pending = false
        held = 0L
        upgrade = false
        sticky.clear()
        for (thumb in listOf(left, right)) {
            thumb.keys.clear()
            thumb.release()
        }
    }

    /** Both banks as one bit per key, plus the armed keys: what a stroke is. */
    fun mask(): Long {
        var bits = 0L
        for (key in left.keys + right.keys + sticky) bits = bits or (1L shl lift(key))
        return bits
    }

    private fun take(thumb: Thumb, keys: List<Int>): Boolean {
        if (keys.isEmpty()) return false
        thumb.keys.addAll(keys)
        pending = true
        return true
    }
}
