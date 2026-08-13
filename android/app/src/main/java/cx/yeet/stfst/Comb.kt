package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The comb: a fixed honeycomb per thumb, walked downward in steno order, read
 * by where the focus is and never by where it went.
 *
 * A layout is one value per cell and nothing else. Everything the keyboard
 * needs is recovered from that: which cells hold keys, which are ground, where
 * a melded button's outline runs, which two keys a walk can reach next, and
 * where a portal lands. There is no span, no size, no order field and no
 * portal table, because each of those is a fact the grid already fixes.
 *
 * The chain zigzags between two columns as it descends, so a key touches the
 * next two in the order: the one diagonally below it and the one directly
 * below. Steno order is enforced, so the other four sides can never be taken
 * as keys and all four are portals, the two holding already-passed keys
 * included.
 *
 * A portal carries a stride rather than a destination. The four strides are
 * the same for every key on the board, so there are four numbers to learn and
 * not one per side per key. Where a stride lands is derived: early in the
 * order the ground between key `i+k` and `i+k+1` is still open and the focus
 * lands there taking nothing, with two keys then one hop away; late in the
 * order that ground is gone and the jump lands on the key and takes it.
 */
class Comb(val order: String, val config: Config) {

    /** Rows split on either a newline or a slash, so a layout fits on one line. */
    private fun parse(text: String): List<Spot> {
        val out = mutableListOf<Spot>()
        text.split('\n', '/').forEachIndexed { row, line ->
            line.forEachIndexed { col, ch ->
                if (ch != ' ') out.add(Spot(col, row, ch))
            }
        }
        return out
    }

    private val spots: Map<Bank, List<Spot>> = mapOf(
        Bank.LEFT to parse(config.combLeft),
        Bank.RIGHT to parse(config.combRight),
    )

    private val byCell: Map<Bank, Map<Long, Spot>> =
        spots.mapValues { (_, list) -> list.associateBy { at(it.col, it.row) } }

    /** A key's order index, or -1 for ground, a hole, or a value off the order. */
    fun keyOf(spot: Spot): Int =
        if (spot.v == GROUND || spot.v == HOLE) -1 else order.indexOf(spot.v)

    private val banks = arrayOfNulls<Bank>(order.length).also {
        for ((bank, list) in spots) for (s in list) keyOf(s).let { k -> if (k >= 0) it[k] = bank }
    }

    fun bankOf(key: Int): Bank? = banks.getOrNull(key)

    /** The keys of one bank, in steno order. Their order is the walk. */
    val chain: Map<Bank, List<Int>> = spots.mapValues { (_, list) ->
        list.map { keyOf(it) }.filter { it >= 0 }.distinct().sorted()
    }

    private val rank: Map<Bank, Map<Int, Int>> =
        chain.mapValues { (_, keys) -> keys.withIndex().associate { (i, k) -> k to i } }

    /**
     * The melded buttons of a bank: a run of touching cells holding one value
     * is one button, and cells that do not touch are separate buttons for the
     * same key. Neither is a flag anyone sets; both fall out of the grid.
     */
    val buttons: Map<Bank, List<Button>> = spots.mapValues { (bank, list) ->
        val cells = byCell.getValue(bank)
        val seen = mutableSetOf<Long>()
        val out = mutableListOf<Button>()
        for (spot in list) {
            if (spot.v == GROUND || spot.v == HOLE || !seen.add(at(spot.col, spot.row))) continue
            val run = mutableListOf<Spot>()
            val stack = ArrayDeque(listOf(spot))
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                run.add(cur)
                for (side in Side.values()) {
                    val n = cells[at(step(cur, side))] ?: continue
                    if (n.v == spot.v && seen.add(at(n.col, n.row))) stack.addLast(n)
                }
            }
            out.add(Button(keyOf(spot), spot.v, run))
        }
        out
    }

    /** Whether steno order still allows this key, given what the thumb has taken. */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = bankOf(key) ?: return false
        val here = rank.getValue(bank)[key] ?: return false
        val last = taken.mapNotNull { rank.getValue(bank)[it] }.maxOrNull() ?: -1
        return here > last
    }

    /** The key entering this cell takes now, or null for ground and spent keys. */
    fun resolve(spot: Spot, taken: List<Int>): Int? =
        keyOf(spot).takeIf { it >= 0 && live(it, taken) }

    fun spots(bank: Bank): List<Spot> = spots.getValue(bank)

    fun spotAt(bank: Bank, col: Int, row: Int): Spot? = byCell.getValue(bank)[at(col, row)]

    // ---- geometry ----------------------------------------------------------

    /**
     * Where a bank's cells sit in the field.
     *
     * Flat-top hexes with odd columns hanging half a cell lower, so a column is
     * a run down the glass and the chain can zigzag between two of them. The
     * lattice is then turned by [Config.tilt] and scaled to fill the field. One
     * placement answers the drawing and the hit test both, which is what stops
     * the picture disagreeing with the decision.
     */
    fun place(bank: Bank, field: Field, tilt: Float): Placement {
        val key = Triple(bank, field, tilt)
        placed[key]?.let { return it }
        val fresh = compute(bank, field, tilt)
        // One field per bank and one tilt per orientation, so this never grows.
        if (placed.size > 8) placed.clear()
        placed[key] = fresh
        return fresh
    }

    private val placed = HashMap<Triple<Bank, Field, Float>, Placement>()

    /** The comb's middle, which a hole reflects the focus through. */
    fun middle(field: Field): Point =
        Point(field.centerX, field.top + field.height * config.combAt)

    private fun compute(bank: Bank, field: Field, tilt: Float): Placement {
        val list = spots.getValue(bank)
        if (list.isEmpty()) return Placement(1f, field.centerX, field.centerY, 1f, 0f)
        val radians = Math.toRadians(tilt.toDouble())
        val ct = cos(radians).toFloat()
        val st = sin(radians).toFloat()
        var lowX = Float.MAX_VALUE
        var lowY = Float.MAX_VALUE
        var highX = -Float.MAX_VALUE
        var highY = -Float.MAX_VALUE
        for (spot in list) {
            val p = lattice(spot)
            // A hex reaches one unit sideways and root three over two down.
            val x = p.x * ct - p.y * st
            val y = p.x * st + p.y * ct
            lowX = min(lowX, x); highX = maxOf(highX, x)
            lowY = min(lowY, y); highY = maxOf(highY, y)
        }
        val edge = 1f
        val wide = (highX - lowX) + 2f * edge
        val tall = (highY - lowY) + 2f * edge
        val fill = config.combFill.coerceIn(0.2f, 1f)
        val radius = min(field.width / wide, field.height / tall) * fill
        return Placement(
            radius = radius,
            originX = field.centerX - (lowX + highX) * 0.5f * radius,
            originY = field.top + field.height * config.combAt -
                (lowY + highY) * 0.5f * radius,
            ct = ct,
            st = st,
        )
    }

    /** The lattice point of a cell, in cell radii, before tilt and scale. */
    fun lattice(spot: Spot): Point =
        Point(1.5f * spot.col, SQ3 * (spot.row + 0.5f * (spot.col and 1)))

    fun point(spot: Spot, at: Placement): Point {
        val p = lattice(spot)
        return Point(
            at.originX + (p.x * at.ct - p.y * at.st) * at.radius,
            at.originY + (p.x * at.st + p.y * at.ct) * at.radius,
        )
    }

    /**
     * The cell this point falls in, or null when the bank has none.
     *
     * The nearest centre, which inside the lattice is exactly the hexagon and
     * outside it is the boundary cell grown to cover the rest of the field. A
     * thumb that overshoots the edge still means the cell it overshot.
     */
    fun hit(bank: Bank, field: Field, tilt: Float, x: Float, y: Float): Spot? {
        val at = place(bank, field, tilt)
        return spots.getValue(bank).minByOrNull {
            val p = point(it, at)
            hypot(p.x - x, p.y - y)
        }
    }

    /**
     * The ground a cell owns, as a polygon.
     *
     * The field clipped by the bisector against every rival, which is the
     * definition of the cell rather than a drawing of it. It stays right at any
     * tilt and covers the whole field however small the lattice is.
     */
    fun region(
        bank: Bank,
        field: Field,
        tilt: Float,
        spot: Spot,
        skip: List<Spot> = emptyList(),
    ): List<Point> {
        val at = place(bank, field, tilt)
        val mine = point(spot, at)
        var polygon = listOf(
            Point(field.left, field.top), Point(field.right, field.top),
            Point(field.right, field.bottom), Point(field.left, field.bottom),
        )
        for (rival in spots.getValue(bank)) {
            if (rival.col == spot.col && rival.row == spot.row) continue
            if (skip.any { it.col == rival.col && it.row == rival.row }) continue
            polygon = nearer(polygon, mine, point(rival, at))
            if (polygon.isEmpty()) break
        }
        return polygon
    }

    /** A button's ground, as one convex piece per cell it melds. */
    fun regions(bank: Bank, field: Field, tilt: Float, button: Button): List<List<Point>> =
        button.spots.map { region(bank, field, tilt, it, button.spots) }

    /** A button's middle, for its label and for the chain of the order. */
    fun center(bank: Bank, field: Field, tilt: Float, button: Button): Point {
        val at = place(bank, field, tilt)
        val points = button.spots.map { point(it, at) }
        return Point(points.sumOf { it.x.toDouble() }.toFloat() / points.size,
            points.sumOf { it.y.toDouble() }.toFloat() / points.size)
    }

    /** The part of a polygon nearer `mine` than `rival`. */
    private fun nearer(polygon: List<Point>, mine: Point, rival: Point): List<Point> {
        // The squared distances differ by a linear expression, so the boundary
        // is a straight edge and this is a clip.
        fun side(p: Point): Float {
            val ours = (p.x - mine.x) * (p.x - mine.x) + (p.y - mine.y) * (p.y - mine.y)
            val theirs = (p.x - rival.x) * (p.x - rival.x) + (p.y - rival.y) * (p.y - rival.y)
            return theirs - ours
        }
        val kept = mutableListOf<Point>()
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val sa = side(a)
            val sb = side(b)
            if (sa >= 0f) kept.add(a)
            if ((sa < 0f) != (sb < 0f)) {
                val t = sa / (sa - sb)
                kept.add(Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        return kept
    }

    // ---- portals -----------------------------------------------------------

    /** The four strides, read from the config and padded to four. */
    val strides: List<Int> = config.combStrides.split(' ', ',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .let { given -> List(4) { i -> given.getOrElse(i) { DEFAULT_STRIDES[i] } } }

    /**
     * Where a portal out of `from` through side `side` lands, or null when the
     * side is not a portal.
     *
     * The two sides facing keys still ahead are the walk and take no jump. The
     * other four are sorted by how far they turn from the way the walk is
     * heading, and take the strides in that order, so a side means the same
     * jump wherever the thumb is on the comb. Open ground before a spent key
     * settles the two that turn by the same amount.
     */
    fun portal(bank: Bank, from: Spot, side: Side, taken: List<Int>): Landing? {
        val key = keyOf(from)
        if (key < 0) return null
        val here = rank.getValue(bank)[key] ?: return null
        val cells = byCell.getValue(bank)
        val ahead = { s: Spot -> resolve(s, taken) != null }
        val sides = Side.values().toList()
        val forward = sides.filter { cells[at(step(from, it))]?.let(ahead) == true }
        if (side in forward) return null
        val heading = walkAngle(bank, from, here)
        val portals = sides.filter { it !in forward }.sortedWith(
            compareBy(
                { turn(angleOf(it), heading) },
                { if (cells[at(step(from, it))]?.let { c -> c.v == GROUND || c.v == HOLE } != false) 0 else 1 },
                { it.ordinal },
            )
        )
        val slot = portals.indexOf(side)
        if (slot < 0) return null
        return landing(bank, here, strides[min(slot, strides.size - 1)])
    }

    /**
     * The cell a stride from rank `here` lands on.
     *
     * The open ground beside key `i+k` that touches the most keys still ahead,
     * so the jump buys a choice rather than one key: it takes nothing and
     * several keys are then one hop away. Ground worth only one key is not
     * worth the hop, and late in the order there is no such ground left at all,
     * so the jump lands on the key and takes it.
     */
    private fun landing(bank: Bank, here: Int, stride: Int): Landing? {
        val keys = chain.getValue(bank)
        val a = keys.getOrNull(min(here + stride, keys.size - 1)) ?: return null
        val cells = byCell.getValue(bank)
        val mine = buttons.getValue(bank).filter { it.key == a }
        val ranks = rank.getValue(bank)
        fun reach(ground: Spot): Int = Side.values()
            .mapNotNull { cells[at(step(ground, it))] }
            .mapNotNull { ranks[keyOf(it)] }
            .filter { it > here }
            .distinct().size
        val best = mine.asSequence()
            .flatMap { it.spots.asSequence() }
            .flatMap { spot -> Side.values().asSequence().mapNotNull { cells[at(step(spot, it))] } }
            .filter { it.v == GROUND || it.v == HOLE }
            .distinct()
            .maxWithOrNull(compareBy({ reach(it) }, { -it.row }, { -it.col }))
        if (best != null && reach(best) >= 2) return Landing(best, false)
        return mine.firstOrNull()?.spots?.firstOrNull()?.let { Landing(it, true) }
    }

    /** Which way the walk is heading out of this cell: toward the key two on. */
    private fun walkAngle(bank: Bank, from: Spot, here: Int): Float {
        val keys = chain.getValue(bank)
        val next = keys.getOrNull(here + 2) ?: keys.getOrNull(here + 1) ?: return 90f
        val target = buttons.getValue(bank).firstOrNull { it.key == next }?.spots?.firstOrNull()
            ?: return 90f
        val a = lattice(from)
        val b = lattice(target)
        return Math.toDegrees(
            Math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        ).toFloat()
    }

    companion object {
        const val GROUND = '.'
        const val HOLE = 'o'
        val SQ3: Float = sqrt(3f)
        val DEFAULT_STRIDES = listOf(3, 5, 7, 9)

        private fun at(col: Int, row: Int): Long = (col.toLong() shl 32) or (row.toLong() and 0xffffffffL)
        private fun at(p: Pair<Int, Int>): Long = at(p.first, p.second)

        /** The neighbour on one side, with odd columns hanging half a cell lower. */
        fun step(spot: Spot, side: Side): Pair<Int, Int> {
            val odd = spot.col and 1
            return when (side) {
                Side.N -> spot.col to spot.row - 1
                Side.S -> spot.col to spot.row + 1
                Side.NE -> spot.col + 1 to spot.row - 1 + odd
                Side.SE -> spot.col + 1 to spot.row + odd
                Side.NW -> spot.col - 1 to spot.row - 1 + odd
                Side.SW -> spot.col - 1 to spot.row + odd
            }
        }

        /** Where a side points, in degrees clockwise from right. */
        fun angleOf(side: Side): Float = when (side) {
            Side.SE -> 60f
            Side.S -> 90f
            Side.SW -> 120f
            Side.NW -> 240f
            Side.N -> 270f
            Side.NE -> 300f
        }

        /** The smaller of the two ways round, in degrees. */
        fun turn(a: Float, b: Float): Int {
            val d = abs((a - b) % 360f)
            return Math.round(if (d > 180f) 360f - d else d)
        }
    }
}

/** A cell of the lattice and the value it answers with. */
data class Spot(val col: Int, val row: Int, val v: Char)

/** One drawn button: a run of touching cells that all hold one value. */
data class Button(val key: Int, val v: Char, val spots: List<Spot>)

/** Where a portal puts the focus, and whether arriving there takes the key. */
data class Landing(val spot: Spot, val onKey: Boolean)

/** A cell's six sides, in the order its corners are drawn. */
enum class Side { SE, S, SW, NW, N, NE }

/** The lattice turned, scaled and set down in a field. */
data class Placement(
    val radius: Float,
    val originX: Float,
    val originY: Float,
    val ct: Float,
    val st: Float,
)

enum class Bank { LEFT, RIGHT }

data class Point(val x: Float, val y: Float)

/** One thumb's half of the keyboard. */
data class Field(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}
