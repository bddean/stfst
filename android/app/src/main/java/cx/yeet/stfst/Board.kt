package cx.yeet.stfst

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The fixed layout: each bank is a ring of cells around an island, and a touch
 * is read by where it is, never by where it went.
 *
 * A cell is a disc, and entering a disc takes its key, so a chord is a walk
 * that visits its keys. Three kinds of ground make every walk possible. A live
 * cell is a key. A dead cell is ground, or a doorway when [config]'s portals
 * give it a destination that is still takeable. Between the discs is ground
 * that takes nothing.
 *
 * A skipped key is still ahead in take order, so it is live, and a skip has to
 * go around it: through the ground between the discs, or across the middle,
 * which the island vacates. The island is the take order's front and it is
 * unordered, the way a machine's thumb cluster is pressed: its keys may be
 * walked in any order, and the first ring key closes it.
 */
class Board(val order: String, val config: Config) {

    /** Each bank's two strings: the island, then the ring, in take order. */
    private val sides: Map<Bank, Pair<String, String>> = mapOf(
        Bank.LEFT to (config.boardIslandLeft to config.boardRingLeft),
        Bank.RIGHT to (config.boardIslandRight to config.boardRingRight),
    )

    /** Each bank's keys in take order. A key in neither string is off the board. */
    private val takes: Map<Bank, List<Int>> = sides.mapValues { (_, side) ->
        (side.first + side.second).mapNotNull { c -> order.indexOf(c).takeIf { it >= 0 } }
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
     * Cell centres in pitch units around the bank's middle, worked out once.
     * Island first, matching [takes], so the two zip.
     */
    private val spots: Map<Bank, List<Point>> = sides.mapValues { (bank, side) ->
        val island = side.first.count { order.indexOf(it) >= 0 }
        val ring = side.second.count { order.indexOf(it) >= 0 }
        val start = if (bank == Bank.LEFT) config.boardStartLeft else config.boardStartRight
        islandSpots(island) + ringSpots(ring, start)
    }

    /** How many island cells a bank has: the unordered front of its take order. */
    private val islands: Map<Bank, Int> = sides.mapValues { (_, side) ->
        side.first.count { order.indexOf(it) >= 0 }
    }

    fun keys(bank: Bank): List<Int> = takes.getValue(bank)

    fun bankOf(key: Int): Bank? = banks[key]

    /**
     * Whether a key can still be taken, given the keys this thumb has taken.
     *
     * An island key is live until it is taken or any ring key is: within the
     * island there is no order, only the boundary. A ring key is live while it
     * is later than every ring key taken, which is the rising run as before.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = banks[key] ?: return false
        val island = islands.getValue(bank)
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

    /** One cell pitch: the distance between two neighbouring cell centres. */
    fun pitch(field: Field): Float = min(field.width, field.height) / config.boardSpan

    /** A cell's radius. Under half the pitch, so ground separates the cells. */
    fun radius(field: Field): Float = pitch(field) * config.boardCell

    fun center(field: Field): Point =
        Point(field.centerX, field.top + field.height * config.boardAt)

    fun cells(bank: Bank, field: Field): List<Cell> {
        val pitch = pitch(field)
        val middle = center(field)
        return keys(bank).zip(spots.getValue(bank)) { key, at ->
            Cell(key, middle.x + at.x * pitch, middle.y + at.y * pitch)
        }
    }

    /** The cell this point is inside, or null for the ground between cells. */
    fun hit(bank: Bank, field: Field, x: Float, y: Float): Int? {
        val radius = radius(field)
        return cells(bank, field).firstOrNull { hypot(it.x - x, it.y - y) <= radius }?.key
    }

    /** The nearest cell, for a touch-down: a tap misses nothing. */
    fun nearest(bank: Bank, field: Field, x: Float, y: Float): Int? =
        cells(bank, field).minByOrNull { hypot(it.x - x, it.y - y) }?.key

    /** A ring just big enough that neighbours sit one pitch apart. */
    private fun ringSpots(count: Int, start: Float): List<Point> {
        if (count == 0) return emptyList()
        val radius = if (count == 1) 1f else 0.5f / sin(Math.PI / count).toFloat()
        return (0 until count).map {
            val a = Math.toRadians((start + it * 360f / count).toDouble())
            Point((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat())
        }
    }

    /**
     * Where the island cells sit: two side by side, four as a diamond with the
     * take order's first key on top. The diamond is squashed so it clears a ten
     * cell ring.
     */
    private fun islandSpots(count: Int): List<Point> = when (count) {
        0 -> emptyList()
        1 -> listOf(Point(0f, 0f))
        2 -> listOf(Point(-0.5f, 0f), Point(0.5f, 0f))
        3 -> listOf(Point(0f, -0.58f), Point(-0.5f, 0.29f), Point(0.5f, 0.29f))
        4 -> listOf(Point(0f, -0.7f), Point(0f, 0.7f), Point(-0.5f, 0f), Point(0.5f, 0f))
        else -> ringSpots(count, -90f).map { Point(it.x * 0.5f, it.y * 0.5f) }
    }
}
