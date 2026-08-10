package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Which thumb owns a key, and where that key sits.
 *
 * A chord is a rising run through the steno order, so the keys a half-written
 * stroke can still take are exactly the ones later in the order. That is the
 * whole rule. No dictionary is consulted: a fixed key in a fixed direction is
 * what a thumb can learn, and a ring that changed shape with the dictionary
 * could not be learned at all.
 *
 * Two layouts, because a touch-down and a slide are different questions. A
 * touch-down has a whole field to aim at and no direction to read, so the keys
 * are spread over it as a honeycomb. A slide has a direction and one point to
 * measure from, so the keys sit on two rings around that point.
 */
class Ring(val order: String, val config: Config) {

    /** Where the right thumb's keys start. Everything before it is the left's. */
    private val split = order.indexOf(config.splitKey).let { if (it < 0) order.length / 2 else it }

    fun bankOf(key: Int): Bank = if (key < split) Bank.LEFT else Bank.RIGHT

    fun keys(bank: Bank): IntRange =
        if (bank == Bank.LEFT) 0 until split else split until order.length

    /** A key's slot, or null for a key the configuration left off both rings. */
    fun slotOf(key: Int): Slot? {
        val bank = bankOf(key)
        val symbol = order[key]
        val far = config.far(bank).indexOf(symbol)
        if (far >= 0) return Slot(Radius.FAR, angle(bank, Radius.FAR, far))
        val near = config.near(bank).indexOf(symbol)
        if (near >= 0) return Slot(Radius.NEAR, angle(bank, Radius.NEAR, near))
        return null
    }

    /** Slots run clockwise in steno order from the ring's start angle. */
    private fun angle(bank: Bank, radius: Radius, index: Int): Float {
        val slots = config.slots(radius)
        return config.startAngle(bank, radius) + index * (360f / slots)
    }

    /**
     * The keys a thumb can still take. Empty means the bank is finished, which is
     * what an empty ring tells the thumb.
     */
    fun live(bank: Bank, after: Int): List<Int> =
        keys(bank).filter { it > after && slotOf(it) != null }

    /**
     * The key a slide from (ox, oy) to (px, py) chooses, or null when the slide is
     * too short to mean anything.
     */
    fun pick(bank: Bank, after: Int, ox: Float, oy: Float, px: Float, py: Float, unit: Float): Int? {
        val dx = px - ox
        val dy = py - oy
        val distance = hypot(dx, dy)
        if (distance < config.minSlide * unit) return null
        val wanted = if (distance < config.ringSplit() * unit) Radius.NEAR else Radius.FAR
        val heading = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val live = live(bank, after)
        // The wanted ring first, so a short flick cannot be stolen by a far key at
        // a slightly better angle. Falling through to the other ring is what keeps
        // a live key reachable when its own ring is empty.
        return nearest(live, wanted, heading) ?: nearest(live, null, heading)
    }

    private fun nearest(live: List<Int>, radius: Radius?, heading: Float): Int? {
        var best: Int? = null
        var bestOff = Float.MAX_VALUE
        for (key in live) {
            val slot = slotOf(key) ?: continue
            if (radius != null && slot.radius != radius) continue
            val off = separation(slot.angle, heading)
            if (off < bestOff && off <= config.snapDegrees) {
                best = key
                bestOff = off
            }
        }
        return best
    }

    /**
     * Which key each direction takes, as the runs of angle it covers.
     *
     * Probed from [pick] rather than worked out from the slot angles, so the
     * picture cannot disagree with the decision. A live key owns every direction
     * closer to its slot than to any other live slot, and that is usually far
     * wider than the slot itself: three live keys share the whole circle between
     * them however tight their slots are.
     */
    fun sectors(bank: Bank, after: Int): List<Sector> =
        Radius.values().flatMap { band(bank, after, it) }

    private fun band(bank: Bank, after: Int, radius: Radius): List<Sector> {
        val distance = config.reach(radius)
        val step = config.sectorStep.coerceIn(0.25f, 30f)
        val runs = mutableListOf<Sector>()
        var key: Int? = null
        var from = 0f
        var angle = 0f
        while (angle < 360f) {
            val radians = Math.toRadians(angle.toDouble())
            val here = pick(
                bank, after, 0f, 0f,
                (cos(radians) * distance).toFloat(), (sin(radians) * distance).toFloat(), 1f
            )
            if (here != key) {
                key?.let { runs.add(Sector(radius, from, angle, it)) }
                key = here
                from = angle
            }
            angle += step
        }
        key?.let { runs.add(Sector(radius, from, 360f, it)) }
        // A run across zero is one sector, not the first one and the last one.
        if (runs.size > 1 && runs.first().key == runs.last().key) {
            val first = runs.removeAt(0)
            val last = runs.removeAt(runs.size - 1)
            runs.add(Sector(radius, last.from, first.to + 360f, first.key))
        }
        return runs
    }

    /**
     * Where a bank's keys sit for a touch-down: a honeycomb over the field.
     *
     * Rows alternate between [Config.entryCols] and one fewer on a single column
     * pitch, so the cells pack. Thirteen keys and three columns give 3 2 3 2 3.
     *
     * Positions are fixed whatever the stroke has taken already, because a tap has
     * to be aimed before the thumb lands. Steno order is [entryAt]'s to apply.
     */
    fun entry(bank: Bank, field: Field): List<Cell> {
        val keys = keys(bank).toList()
        val rows = rowSizes(keys.size)
        val pitch = field.width / config.entryCols.coerceAtLeast(1)
        val depth = field.height / rows.size
        val cells = ArrayList<Cell>(keys.size)
        var i = 0
        for ((r, size) in rows.withIndex()) {
            val y = field.top + (r + 0.5f) * depth
            for (c in 0 until size) {
                val x = field.centerX + (c - (size - 1) * 0.5f) * pitch
                cells.add(Cell(keys[i++], x, y, pitch, depth))
            }
        }
        return cells
    }

    private fun rowSizes(count: Int): List<Int> {
        val wide = config.entryCols.coerceAtLeast(1)
        val narrow = (wide - 1).coerceAtLeast(1)
        val rows = mutableListOf<Int>()
        var rest = count
        while (rest > 0) {
            val size = if (rows.size % 2 == 0) wide else narrow
            rows.add(minOf(size, rest))
            rest -= rows.last()
        }
        return rows
    }

    /** The live key whose cell holds this point, which is the nearest centre. */
    fun entryAt(bank: Bank, field: Field, x: Float, y: Float, after: Int): Int? =
        entry(bank, field)
            .filter { it.key > after && slotOf(it.key) != null }
            .minByOrNull { hypot(it.x - x, it.y - y) }
            ?.key

    companion object {
        /** The smaller of the two ways round, in degrees. */
        fun separation(a: Float, b: Float): Float {
            val d = abs((a - b) % 360f)
            return if (d > 180f) 360f - d else d
        }
    }
}

enum class Bank { LEFT, RIGHT }

enum class Radius { NEAR, FAR }

data class Slot(val radius: Radius, val angle: Float)

/** A run of directions that all take the same key, on one of the two rings. */
data class Sector(val radius: Radius, val from: Float, val to: Float, val key: Int)

/** A key's place on the tap layout, with the lattice spacing its cell fills. */
data class Cell(val key: Int, val x: Float, val y: Float, val pitch: Float, val depth: Float)

/** One thumb's half of the keyboard. */
data class Field(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
}
