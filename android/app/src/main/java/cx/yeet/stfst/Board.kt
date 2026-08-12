package cx.yeet.stfst

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The fixed layout: one staggered ring of slices round an island, read by
 * where the thumb is, never by where it went.
 *
 * Each ring key owns one slice, in ring order round the circle, and every
 * other slice sits [Config.boardStagger] further out. No two slices share an
 * angle, so no cell ever blocks the radial way in or out of another. The
 * stagger opens two lanes of ground inside the ring: a sweep along the outer
 * lane crosses the outward slices only, one along the inner lane the inward
 * ones: every other key in steno order, which is a machine row. Mid ring the
 * slices overlap, so consecutive keys are one short zigzag apart.
 *
 * Entering a cell takes its key, and a taken cell is ground: there is no order
 * on the ring, as there is none on the machine. The island is the exception
 * that pays for the middle: its keys go in any order but before the ring, and
 * the first ring key closes it. A closed island cell is ground, or a doorway
 * when [config]'s portals give it a destination that is still takeable.
 *
 * Two roads carry every skip past a live key: the corridor between island and
 * ring, and the open field past the outer edge. A cell is only the button that
 * is drawn; beyond the outer edge nothing takes.
 */
class Board(val order: String, val config: Config) {

    /** Each bank's two strings: the island, then the ring, in steno order. */
    private val sides: Map<Bank, Pair<String, String>> = mapOf(
        Bank.LEFT to (config.boardIslandLeft to config.boardRingLeft),
        Bank.RIGHT to (config.boardIslandRight to config.boardRingRight),
    )

    private val islandKeys: Map<Bank, List<Int>> = sides.mapValues { (_, side) ->
        side.first.mapNotNull { c -> order.indexOf(c).takeIf { it >= 0 } }
    }
    private val ringKeys: Map<Bank, List<Int>> = sides.mapValues { (_, side) ->
        side.second.mapNotNull { c -> order.indexOf(c).takeIf { it >= 0 } }
    }

    /** Each bank's keys, island first. A key in neither string is off the board. */
    private val takes: Map<Bank, List<Int>> = Bank.values().associateWith {
        islandKeys.getValue(it) + ringKeys.getValue(it)
    }

    /** A key's place in its bank's strings, or -1 for a key off the board. */
    private val rank = IntArray(order.length) { -1 }.also {
        for (keys in takes.values) keys.forEachIndexed { i, k -> it[k] = i }
    }

    private val banks = arrayOfNulls<Bank>(order.length).also {
        for ((bank, keys) in takes) for (k in keys) it[k] = bank
    }

    /** Where a dead cell leads, or -1. Parsed from pairs like `A>R`. */
    private val portals = IntArray(order.length) { -1 }.also {
        for (pair in config.boardPortals.split(" ")) {
            if (pair.length != 3 || pair[1] != '>') continue
            val from = order.indexOf(pair[0])
            val to = order.indexOf(pair[2])
            // A doorway across the banks would hand one thumb the other's key.
            if (from >= 0 && to >= 0 && banks[from] != null && banks[from] == banks[to]) {
                it[from] = to
            }
        }
    }

    fun keys(bank: Bank): List<Int> = takes.getValue(bank)

    fun bankOf(key: Int): Bank? = banks[key]

    /** Whether a key sits on the island rather than the ring. */
    fun island(key: Int): Boolean {
        val bank = banks[key] ?: return false
        return rank[key] < islandKeys.getValue(bank).size
    }

    /**
     * Whether a key can still be taken, given the keys this thumb has taken.
     *
     * Taken is dead, and that is the whole rule on the ring: the machine
     * presses everything together, so no order is enforced there. An island
     * key also dies when any ring key is taken, which is what turns the middle
     * into road and its cells into doorways for the rest of the stroke.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = banks[key] ?: return false
        if (key in taken) return false
        if (!island(key)) return true
        return taken.none { banks[it] == bank && !island(it) }
    }

    fun portal(key: Int): Int? = portals[key].takeIf { it >= 0 }

    /**
     * The key entering this cell takes now: its own while live, its doorway's
     * while that one is, and nothing once both are spent.
     */
    fun resolve(cell: Int, taken: List<Int>): Int? = when {
        live(cell, taken) -> cell
        else -> portal(cell)?.takeIf { live(it, taken) }
    }

    /** One cell pitch, which every radius below is a multiple of. */
    fun pitch(field: Field): Float = min(field.width, field.height) / config.boardSpan

    /** An island disc's radius. */
    fun radius(field: Field): Float = pitch(field) * config.boardCell

    /** The ring's inner edge. Between it and the island is the corridor. */
    fun inner(field: Field): Float = pitch(field) * config.boardInner

    /** The ring's outer edge. Past it is the outer road: nothing takes. */
    fun outer(field: Field): Float = pitch(field) * config.boardOuter

    /** Degrees of ring one slice owns. */
    fun step(bank: Bank): Float = 360f / ringKeys.getValue(bank).size.coerceAtLeast(1)

    fun start(bank: Bank): Float =
        if (bank == Bank.LEFT) config.boardStartLeft else config.boardStartRight

    fun center(field: Field): Point =
        Point(field.centerX, field.top + field.height * config.boardAt)

    /**
     * A ring key's radial span, in pitches. Every slice has the same depth,
     * the ring's minus the stagger, and every other slice starts the stagger
     * further out. [Config.boardTopIn] picks which alternation is the inward
     * one.
     */
    fun radiiOf(key: Int): ClosedFloatingPointRange<Float> {
        val bank = banks[key] ?: return 0f..0f
        val at = rank[key] - islandKeys.getValue(bank).size
        val stagger = config.boardStagger.coerceIn(0f, config.boardOuter - config.boardInner)
        return if ((at % 2 == 0) == config.boardTopIn) {
            config.boardInner..(config.boardOuter - stagger)
        } else {
            (config.boardInner + stagger)..config.boardOuter
        }
    }

    fun cells(bank: Bank, field: Field): List<Cell> {
        val pitch = pitch(field)
        val middle = center(field)
        val island = islandKeys.getValue(bank)
        val spots = islandSpots(island.size) + ringKeys.getValue(bank).map { key ->
            val angle = Math.toRadians(
                (start(bank) + (rank[key] - island.size) * step(bank)).toDouble()
            )
            val at = (radiiOf(key).start + radiiOf(key).endInclusive) * 0.5f
            Point((cos(angle) * at).toFloat(), (sin(angle) * at).toFloat())
        }
        return keys(bank).zip(spots) { key, at ->
            Cell(key, middle.x + at.x * pitch, middle.y + at.y * pitch)
        }
    }

    /**
     * The cell this point is inside, or null for the ground: the corridor, the
     * outer road, a lane's notch, or the space between island discs.
     */
    fun hit(bank: Bank, field: Field, x: Float, y: Float): Int? {
        val middle = center(field)
        val dx = x - middle.x
        val dy = y - middle.y
        val r = hypot(dx, dy) / pitch(field)
        if (r > config.boardOuter) return null
        if (r >= config.boardInner) {
            val key = sliceAt(bank, dx, dy) ?: return null
            return key.takeIf { r in radiiOf(it) }
        }
        val radius = radius(field)
        return cells(bank, field).take(islandKeys.getValue(bank).size)
            .firstOrNull { hypot(it.x - x, it.y - y) <= radius }?.key
    }

    /**
     * What a touch-down reads, so no tap lands on nothing: the cell hit, or
     * whichever is closer, the slice this direction owns or the nearest
     * island disc.
     */
    fun nearest(bank: Bank, field: Field, x: Float, y: Float): Int? {
        hit(bank, field, x, y)?.let { return it }
        val middle = center(field)
        val dx = x - middle.x
        val dy = y - middle.y
        val r = hypot(dx, dy) / pitch(field)
        val slice = sliceAt(bank, dx, dy)
        val toSlice = slice?.let {
            val span = radiiOf(it)
            maxOf(span.start - r, r - span.endInclusive, 0f)
        }
        val disc = cells(bank, field).take(islandKeys.getValue(bank).size)
            .minByOrNull { hypot(it.x - x, it.y - y) }
        val toDisc = disc?.let { (hypot(it.x - x, it.y - y) - radius(field)) / pitch(field) }
        return when {
            toSlice == null -> disc?.key
            toDisc != null && toDisc <= toSlice -> disc.key
            else -> slice
        }
    }

    /** The ring key whose slice this direction from the middle belongs to. */
    private fun sliceAt(bank: Bank, dx: Float, dy: Float): Int? {
        val ring = ringKeys.getValue(bank)
        if (ring.isEmpty()) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val at = Math.floorMod(Math.round((angle - start(bank)) / step(bank)), ring.size)
        return ring[at]
    }

    /**
     * Where the island discs sit: two side by side, four as a diamond with the
     * take order's first key on top. Squashed so the corridor around it keeps
     * its width.
     */
    private fun islandSpots(count: Int): List<Point> = when (count) {
        0 -> emptyList()
        1 -> listOf(Point(0f, 0f))
        2 -> listOf(Point(-0.5f, 0f), Point(0.5f, 0f))
        3 -> listOf(Point(0f, -0.58f), Point(-0.5f, 0.29f), Point(0.5f, 0.29f))
        4 -> listOf(Point(0f, -0.7f), Point(0f, 0.7f), Point(-0.5f, 0f), Point(0.5f, 0f))
        else -> (0 until count).map {
            val a = Math.toRadians((-90.0 + it * 360.0 / count))
            Point((cos(a) * 0.7f).toFloat(), (sin(a) * 0.7f).toFloat())
        }
    }
}
