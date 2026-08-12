package cx.yeet.stfst

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The fixed layout: one staggered ring of slices round an island, floating
 * arcs past the outer edge, read by where the focus is, never by where it
 * went.
 *
 * Each ring key owns one slice, in ring order round the circle, and every
 * other slice sits [Config.boardStagger] further out. No two slices share an
 * angle, so no cell ever blocks the radial way in or out of another. The
 * stagger opens two lanes of ground inside the ring: a sweep along the outer
 * lane crosses the outward slices only, one along the inner lane the inward
 * ones: every other key in steno order, which is a machine row. Mid ring the
 * slices overlap, so consecutive keys are one short zigzag apart.
 *
 * The island is one circle, cut as a pie among its keys, and it never changes
 * size. Its keys go in any order but before the ring, and the first ring key
 * closes it. A closed island becomes the tombstone: crossing into it throws
 * the focus straight across the board's middle, so a key on the far side of
 * the ring is a short dip through the island instead of a trip around it.
 * With the teleport off, a closed island cell is instead a doorway when
 * [config]'s portals give it a destination that is still takeable.
 *
 * The floats hang past the outer edge, one arc above the ring and one below.
 * A float is taken like any other cell, and taking one does not close the
 * island, so the number bar joins a stroke whenever the machine's would.
 *
 * Entering a cell takes its key, and a taken cell is ground: there is no
 * order on the ring, as there is none on the machine. Two roads carry every
 * skip past a live key: the corridor between island and ring, and the open
 * field past the outer edge, around the floats. A cell is only the button
 * that is drawn.
 */
class Board(val order: String, val config: Config) {

    private fun keysOf(s: String): List<Int> =
        s.mapNotNull { c -> order.indexOf(c).takeIf { it >= 0 } }

    /** A key named as a float wins it, so an old saved layout keeps working. */
    private val haloKeys: Map<Bank, List<Int>> = mapOf(
        Bank.LEFT to keysOf(config.boardArcsLeft),
        Bank.RIGHT to keysOf(config.boardArcsRight),
    )
    private val islandKeys: Map<Bank, List<Int>> = mapOf(
        Bank.LEFT to keysOf(config.boardIslandLeft),
        Bank.RIGHT to keysOf(config.boardIslandRight),
    ).mapValues { (bank, keys) -> keys.filter { it !in haloKeys.getValue(bank) } }
    private val ringKeys: Map<Bank, List<Int>> = mapOf(
        Bank.LEFT to keysOf(config.boardRingLeft),
        Bank.RIGHT to keysOf(config.boardRingRight),
    ).mapValues { (bank, keys) -> keys.filter { it !in haloKeys.getValue(bank) } }

    /** Each bank's keys: island, ring, floats. A key in no string is off the board. */
    private val takes: Map<Bank, List<Int>> = Bank.values().associateWith {
        islandKeys.getValue(it) + ringKeys.getValue(it) + haloKeys.getValue(it)
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

    /** Whether a key sits on the island. */
    fun island(key: Int): Boolean {
        val bank = banks[key] ?: return false
        return rank[key] < islandKeys.getValue(bank).size
    }

    /** Whether a key floats past the outer edge. */
    fun halo(key: Int): Boolean {
        val bank = banks[key] ?: return false
        return rank[key] >= islandKeys.getValue(bank).size + ringKeys.getValue(bank).size
    }

    private fun ring(key: Int): Boolean = banks[key] != null && !island(key) && !halo(key)

    /** Whether this bank's island is closed: any ring key taken shuts it. */
    fun closed(bank: Bank, taken: List<Int>): Boolean =
        taken.any { banks[it] == bank && ring(it) }

    /**
     * Whether a key can still be taken, given the keys this thumb has taken.
     *
     * Taken is dead, and that is the whole rule on the ring and the floats:
     * the machine presses everything together, so no order is enforced there.
     * An island key also dies when any ring key is taken, which is what turns
     * the middle into the tombstone for the rest of the stroke.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = banks[key] ?: return false
        if (key in taken) return false
        if (!island(key)) return true
        return !closed(bank, taken)
    }

    /** Where a dead cell leads, or null. The teleport replaces the doorways. */
    fun portal(key: Int): Int? =
        portals[key].takeIf { it >= 0 && !config.boardTeleport }

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

    /** The island circle's radius: the pie while it lives, the tombstone after. */
    fun isle(field: Field): Float =
        pitch(field) * config.boardIsle.coerceIn(0f, config.boardInner)

    /** The ring's inner edge. Between it and the island is the corridor. */
    fun inner(field: Field): Float = pitch(field) * config.boardInner

    /** The ring's outer edge. Past it, around the floats, nothing takes. */
    fun outer(field: Field): Float = pitch(field) * config.boardOuter

    /** The board's outermost radius: the floats' outer rim. */
    fun reach(field: Field): Float = pitch(field) * haloBand().endInclusive

    /** Degrees of ring one slice owns. */
    fun step(bank: Bank): Float = 360f / ringKeys.getValue(bank).size.coerceAtLeast(1)

    /** Degrees of island one pie sector owns. */
    fun islandStep(bank: Bank): Float =
        360f / islandKeys.getValue(bank).size.coerceAtLeast(1)

    fun start(bank: Bank): Float =
        if (bank == Bank.LEFT) config.boardStartLeft else config.boardStartRight

    fun center(field: Field): Point =
        Point(field.centerX, field.top + field.height * config.boardAt)

    /** The floats' band, in pitches: just past the outer edge. */
    private fun haloBand(): ClosedFloatingPointRange<Float> {
        val at = config.boardOuter + 0.12f
        return at..(at + config.boardArcWide)
    }

    /** Where a float's arc is centred: the first on top, the second below. */
    fun haloAngle(key: Int): Float {
        val bank = banks[key] ?: return -90f
        val at = rank[key] - islandKeys.getValue(bank).size - ringKeys.getValue(bank).size
        return if (at % 2 == 0) -90f else 90f
    }

    /** Where an island sector is centred. The first key takes the left. */
    fun islandAngle(key: Int): Float {
        val bank = banks[key] ?: return 180f
        return 180f + rank[key] * islandStep(bank)
    }

    /**
     * A ring key's radial span, in pitches. Every slice has the same depth,
     * the ring's minus the stagger, and every other slice starts the stagger
     * further out. [Config.boardTopIn] picks which alternation is the inward
     * one. A float's span is its arc's band instead.
     */
    fun radiiOf(key: Int): ClosedFloatingPointRange<Float> {
        val bank = banks[key] ?: return 0f..0f
        if (halo(key)) return haloBand()
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
        val isle = config.boardIsle.coerceIn(0f, config.boardInner)
        val spots = takes.getValue(bank).map { key ->
            val (angle, at) = when {
                island(key) ->
                    islandAngle(key) to if (islandKeys.getValue(bank).size > 1) isle * 0.55f else 0f
                halo(key) ->
                    haloAngle(key) to (radiiOf(key).start + radiiOf(key).endInclusive) * 0.5f
                else ->
                    start(bank) + (rank[key] - islandKeys.getValue(bank).size) * step(bank) to
                        (radiiOf(key).start + radiiOf(key).endInclusive) * 0.5f
            }
            val radians = Math.toRadians(angle.toDouble())
            Point((cos(radians) * at).toFloat(), (sin(radians) * at).toFloat())
        }
        return keys(bank).zip(spots) { key, at ->
            Cell(key, middle.x + at.x * pitch, middle.y + at.y * pitch)
        }
    }

    /**
     * The cell this point is inside, or null for the ground: the corridor,
     * the field around the floats, or a lane's notch.
     */
    fun hit(bank: Bank, field: Field, x: Float, y: Float): Int? {
        val middle = center(field)
        val dx = x - middle.x
        val dy = y - middle.y
        val r = hypot(dx, dy) / pitch(field)
        if (r > config.boardOuter) return haloAt(bank, r, dx, dy)
        if (r >= config.boardInner) {
            val key = sliceAt(bank, dx, dy) ?: return null
            return key.takeIf { r in radiiOf(it) }
        }
        if (r <= config.boardIsle.coerceIn(0f, config.boardInner)) {
            return islandAt(bank, dx, dy)
        }
        return null
    }

    /** The ring key whose slice this direction from the middle belongs to. */
    private fun sliceAt(bank: Bank, dx: Float, dy: Float): Int? {
        val ring = ringKeys.getValue(bank)
        if (ring.isEmpty()) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val at = Math.floorMod(Math.round((angle - start(bank)) / step(bank)), ring.size)
        return ring[at]
    }

    /** The island key whose pie sector this direction belongs to. */
    private fun islandAt(bank: Bank, dx: Float, dy: Float): Int? {
        val island = islandKeys.getValue(bank)
        if (island.isEmpty()) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val at = Math.floorMod(Math.round((angle - 180f) / islandStep(bank)), island.size)
        return island[at]
    }

    /** The float this point is inside, or null. */
    private fun haloAt(bank: Bank, r: Float, dx: Float, dy: Float): Int? {
        val band = haloBand()
        if (r < band.start || r > band.endInclusive) return null
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return haloKeys.getValue(bank).firstOrNull {
            Ring.separation(haloAngle(it), angle) <= config.boardArcSweep * 0.5f
        }
    }
}
