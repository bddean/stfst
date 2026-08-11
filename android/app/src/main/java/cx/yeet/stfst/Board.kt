package cx.yeet.stfst

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The fixed layout: each bank is a ring of slices around an island of discs,
 * and a touch is read by where it is, never by where it went.
 *
 * Entering a cell takes its key, so a chord is a walk that visits its keys.
 * The ring is contiguous: one annular slice per key, open outward, so a slice
 * is missed only inward. Inside the ring, a corridor of ground circles the
 * island and takes nothing: a skipped ring key is still ahead in take order,
 * so it is live, and the corridor is how a walk gets past it. The island discs
 * are the only cells a walk can also go *between*.
 *
 * The island is the take order's front and it is unordered, the way a
 * machine's thumb cluster is pressed: its keys may be walked in any order, and
 * the first ring key closes it. A closed island cell is ground, or a doorway
 * when [config]'s portals give it a destination that is still takeable.
 */
class Board(val order: String, val config: Config) {

    /** Each bank's two strings: the island, then the ring, in take order. */
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

    /** Each bank's keys in take order. A key in neither string is off the board. */
    private val takes: Map<Bank, List<Int>> = Bank.values().associateWith {
        islandKeys.getValue(it) + ringKeys.getValue(it)
    }

    /** A key's place in its bank's take order, or -1 for a key off the board. */
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

    /**
     * Cell reference points in pitch units around the bank's middle: island
     * centres, then each slice's label point. Island first, matching [takes].
     */
    private val spots: Map<Bank, List<Point>> = Bank.values().associateWith { bank ->
        val label = (config.boardInner + config.boardOuter) * 0.5f
        islandSpots(islandKeys.getValue(bank).size) +
            ringSpots(ringKeys.getValue(bank).size, start(bank), label)
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
     * An island key is live until it is taken or any ring key is: within the
     * island there is no order, only the boundary. A ring key is live while it
     * is later than every ring key taken, which is the rising run as before.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = banks[key] ?: return false
        val island = islandKeys.getValue(bank).size
        var ring = -1
        for (k in taken) {
            if (banks[k] == bank && rank[k] >= island) ring = maxOf(ring, rank[k])
        }
        return if (rank[key] < island) ring < 0 && key !in taken
        else rank[key] > ring
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

    /** The ring's inner edge. Inside it, only the island discs take. */
    fun inner(field: Field): Float = pitch(field) * config.boardInner

    /** Where the ring is drawn to. The answer does not change past it. */
    fun outer(field: Field): Float = pitch(field) * config.boardOuter

    /** Degrees of ring one key owns. */
    fun step(bank: Bank): Float = 360f / ringKeys.getValue(bank).size.coerceAtLeast(1)

    fun start(bank: Bank): Float =
        if (bank == Bank.LEFT) config.boardStartLeft else config.boardStartRight

    fun center(field: Field): Point =
        Point(field.centerX, field.top + field.height * config.boardAt)

    fun cells(bank: Bank, field: Field): List<Cell> {
        val pitch = pitch(field)
        val middle = center(field)
        return keys(bank).zip(spots.getValue(bank)) { key, at ->
            Cell(key, middle.x + at.x * pitch, middle.y + at.y * pitch)
        }
    }

    /**
     * The cell this point is inside, or null for the ground.
     *
     * Outside the ring's inner edge every direction belongs to a slice, however
     * far out, so a slice can only be missed inward. Inside are the island
     * discs and the corridor between them and the ring.
     */
    fun hit(bank: Bank, field: Field, x: Float, y: Float): Int? {
        val middle = center(field)
        val dx = x - middle.x
        val dy = y - middle.y
        if (hypot(dx, dy) >= inner(field)) return slice(bank, dx, dy)
        val radius = radius(field)
        return cells(bank, field).take(islandKeys.getValue(bank).size)
            .firstOrNull { hypot(it.x - x, it.y - y) <= radius }?.key
    }

    /**
     * What a touch-down reads, so no tap lands on nothing: the cell hit, or
     * from the corridor whichever is closer, the slice just outward or the
     * nearest island disc.
     */
    fun nearest(bank: Bank, field: Field, x: Float, y: Float): Int? {
        hit(bank, field, x, y)?.let { return it }
        val middle = center(field)
        val dx = x - middle.x
        val dy = y - middle.y
        val toRing = inner(field) - hypot(dx, dy)
        val disc = cells(bank, field).take(islandKeys.getValue(bank).size)
            .minByOrNull { hypot(it.x - x, it.y - y) }
        val toDisc = disc?.let { hypot(it.x - x, it.y - y) - radius(field) }
        if (toDisc != null && toDisc <= toRing) return disc.key
        return slice(bank, dx, dy) ?: disc?.key
    }

    /** The ring key whose slice this direction from the middle belongs to. */
    private fun slice(bank: Bank, dx: Float, dy: Float): Int? {
        val ring = ringKeys.getValue(bank)
        if (ring.isEmpty()) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return ring[Math.floorMod(Math.round((angle - start(bank)) / step(bank)), ring.size)]
    }

    /** Points at one radius, [step] degrees apart, clockwise from [from]. */
    private fun ringSpots(count: Int, from: Float, radius: Float): List<Point> {
        if (count == 0) return emptyList()
        return (0 until count).map {
            val a = Math.toRadians((from + it * 360f / count).toDouble())
            Point((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
        }
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
        else -> ringSpots(count, -90f, 0.7f)
    }
}
