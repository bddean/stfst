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
     * The keys taken while the upgrade was on, which are the ones it writes.
     *
     * The mode can turn over during a stroke, so what a key writes is the mode
     * it was taken under: the toggle, `-T`, the toggle, `-S` is `-DS`.
     */
    val raised: MutableSet<Int> = mutableSetOf()

    /**
     * The cell the thumb is in, or null before the first touch.
     *
     * What makes entering an event: a cell answers when the thumb comes in, not
     * on every sample it stays.
     */
    var cell: Cell? = null

    val down: Boolean get() = pointer != NONE

    /** Give up the keys in hand, and what the upgrade was doing to them. */
    fun clear() {
        keys.clear()
        raised.clear()
    }

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

    /**
     * What the upgrade writes on its own: the keys it lifts to.
     *
     * A touch that turns the mode on and takes nothing else has nothing to
     * upgrade, so what it means is those keys themselves. `-DZ` is a stroke
     * worth a cell of its own, and this is how the toggle's own cell carries
     * it.
     */
    private fun upgraded(): Long {
        var bits = 0L
        for (key in lifted.values) bits = bits or (1L shl key)
        return bits
    }

    /** What a key writes now: itself, or what the upgrade lifts it to. */
    fun lift(key: Int): Int = if (upgrade) lifted[key] ?: key else key

    /** What a key already taken writes: the mode it was taken under. */
    private fun written(thumb: Thumb, key: Int): Int =
        if (key in thumb.raised) lifted[key] ?: key else key

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

    /**
     * Whether the two halves of the glass are the other way round.
     *
     * One hand writes a whole stroke by turning the board rather than by
     * reaching across it: crossing the middle swaps the halves, and the next
     * touch walks the other bank where the thumb already is. It lasts one
     * stroke, because that is what it was for.
     */
    var swapped = false
        private set

    /** Each half of the glass, kept so the swap can reseat both thumbs. */
    private val fields = mutableMapOf<Bank, Field>()

    fun thumb(bank: Bank) = if (bank == Bank.LEFT) left else right

    /** The bank a half of the glass walks now, which the swap turns round. */
    fun bankFor(side: Bank): Bank =
        if (swapped) (if (side == Bank.LEFT) Bank.RIGHT else Bank.LEFT) else side

    fun forPointer(pointer: Int): Thumb? = when (pointer) {
        left.pointer -> left
        right.pointer -> right
        else -> null
    }

    /** Give a half of the keyboard to the thumb that walks it. */
    fun place(side: Bank, field: Field) {
        fields[side] = field
        thumb(bankFor(side)).field = field
    }

    /**
     * Turn the two halves round, keeping in hand what has been taken.
     *
     * This is a touch crossing the middle. The touch itself is over: it wrote
     * nothing, because a stroke half taken is not a stroke, and the keys wait
     * for the next touch, which walks the other bank.
     */
    fun swap(pointer: Int): Boolean {
        val thumb = forPointer(pointer) ?: return false
        thumb.release()
        swapped = !swapped
        seat()
        return true
    }

    /** Put the halves back, which is what writing the stroke does. */
    private fun unswap() {
        if (!swapped) return
        swapped = false
        seat()
    }

    private fun seat() {
        for ((side, field) in fields) thumb(bankFor(side)).field = field
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
        return land(thumb, pointer, x, y, snap = true)
    }

    /**
     * A fresh touch starts this thumb's walk, and takes the keys it landed on.
     *
     * The hub is a pie of complete answers, so landing on one takes the whole
     * slice. It is the only way into the hub that takes anything: crossing it
     * later takes nothing, which is what makes the middle a shortcut.
     */
    private fun land(
        thumb: Thumb, pointer: Int, x: Float, y: Float, snap: Boolean,
    ): Boolean {
        thumb.pointer = pointer
        thumb.live = true
        thumb.atX = x
        thumb.atY = y
        val cell = if (snap) aim(thumb, x, y) else ring.hit(thumb.bank, thumb.field, x, y)
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
     * a cell the walk has spent flips its ring's key instead.
     *
     * The hub is ground once the thumb has left it, so a chord through the
     * middle joins two cells too far apart on one ring to reach any other way.
     */
    fun move(pointer: Int, x: Float, y: Float): Boolean {
        val thumb = forPointer(pointer) ?: return false
        thumb.atX = x
        thumb.atY = y
        val now = ring.hit(thumb.bank, thumb.field, x, y)
        val entered = now != thumb.cell
        thumb.cell = now
        return when {
            !entered || ring.isHub(now) -> false
            ring.isUpgrade(thumb.bank, now) -> turn()
            else -> take(thumb, ring.taking(thumb.bank, now, thumb.keys)) || flip(thumb, now)
        }
    }

    /**
     * The cell a touch coming down means, which is the nearest one when it
     * lands on ground.
     *
     * A tap is aimed before the thumb arrives, so a tap short of its key is the
     * aim and not an answer. A slide is read where it goes, so this is for a
     * touch going down alone.
     */
    private fun aim(thumb: Thumb, x: Float, y: Float): Cell {
        val cell = ring.hit(thumb.bank, thumb.field, x, y)
        if (!config.tapSnap) return cell
        if (ring.keys(thumb.bank, cell).isNotEmpty() || ring.isUpgrade(thumb.bank, cell)) return cell
        return ring.nearest(thumb.bank, thumb.field, x, y) ?: cell
    }

    /**
     * A cell the walk can no longer spend flips its ring's key, in or out of
     * the stroke in hand.
     *
     * Entering it is a place a chord never goes, so it is free to mean
     * something else, and a flip rather than a take is what lets a thumb that
     * turned back too far turn back again.
     */
    private fun flip(thumb: Thumb, cell: Cell): Boolean {
        val key = ring.flip(thumb.bank, cell, thumb.keys)
        if (key < 0) return false
        if (thumb.keys.contains(key)) {
            thumb.keys.removeAll { it == key }
            thumb.raised.remove(key)
        } else {
            thumb.keys.add(key)
            if (upgrade) thumb.raised.add(key)
        }
        pending = true
        return true
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
        return land(to, pointer, x, y, snap = false)
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
            thumb.clear()
            if (!again) return 0L
        }
        if (!config.commitOnFirstLift && other.down) return 0L
        val stroke = when {
            !pending -> 0L
            mask() == 0L && upgrade -> upgraded()
            else -> mask()
        }
        pending = false
        thumb.clear()
        if (!other.down) other.clear()
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
        // An armed key whose thumb is still down is held rather than armed, so
        // it outlives the stroke the way the keys that thumb took do.
        for (key in sticky) {
            val thumb = ring.bankOf(key)?.let { thumb(it) } ?: continue
            if (thumb.down && key !in thumb.keys) thumb.keys.add(key)
        }
        sticky.clear()
        upgrade = false
        unswap()
        write(stroke)
    }

    /** Give up everything, for a keyboard that is going away. */
    fun reset() {
        unswap()
        pending = false
        held = 0L
        upgrade = false
        sticky.clear()
        for (thumb in listOf(left, right)) {
            thumb.clear()
            thumb.release()
        }
    }

    /** Both banks as one bit per key, plus the armed keys: what a stroke is. */
    fun mask(): Long {
        var bits = 0L
        for (thumb in listOf(left, right)) {
            for (key in thumb.keys) bits = bits or (1L shl written(thumb, key))
        }
        for (key in sticky) bits = bits or (1L shl lift(key))
        return bits
    }

    private fun take(thumb: Thumb, keys: List<Int>): Boolean {
        if (keys.isEmpty()) return false
        thumb.keys.addAll(keys)
        if (upgrade) thumb.raised.addAll(keys)
        pending = true
        return true
    }
}
