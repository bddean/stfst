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
 * A key that does not touch the next key of the walk is a float, and order
 * says nothing about one: it is takeable whenever the thumb reaches it. That
 * is what the modifiers are, and it is read off the grid rather than declared.
 *
 * A portal carries a stride rather than a destination. The four strides are
 * the same for every key on the board, so there are four numbers to learn and
 * not one per side per key. Where a stride lands is derived: early in the
 * order there is open ground beside key `i+k` and the focus rests there taking
 * nothing, with several keys then one hop away; late in the order that ground
 * is gone and the jump lands on the key and takes it. The four are chosen
 * together, so no two rest on the same cell or on touching ones.
 *
 * A stride is not spent by one jump. Keep sliding the way the jump went and it
 * goes again, so a side is a run of landings rather than one. Four sides can
 * only name four distances, and the runs reach what four cannot.
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

    /**
     * The walk of one bank, and the keys left off it.
     *
     * The walk is what a rising run can trace, so each key on it touches the
     * next one on it. Read the order backwards: the last key of a bank is on
     * the walk, and a key joins when it touches the next key already on. What
     * cannot join is a float, and steno order says nothing about a float,
     * because nothing joins it to the walk for the walk to pass.
     *
     * That is what the modifiers are. Reading it off the successor rather than
     * off every neighbour is what lets a float sit against the end cluster and
     * still be a float.
     */
    private val walked: Map<Bank, Walk> = spots.mapValues { (bank, list) ->
        val cells = byCell.getValue(bank)
        fun touches(key: Int, other: Int): Boolean =
            buttons.getValue(bank).filter { it.key == key }.any { button ->
                button.spots.any { s ->
                    Side.values().any { keyOf(cells[at(step(s, it))] ?: return@any false) == other }
                }
            }
        val keys = list.map { keyOf(it) }.filter { it >= 0 }.distinct().sorted()
        val walk = ArrayDeque<Int>()
        for (key in keys.asReversed()) {
            if (walk.isEmpty() || touches(key, walk.first())) walk.addFirst(key)
        }
        Walk(walk.toList(), keys.minus(walk.toSet()).toSet())
    }

    /** The keys of one bank on the walk, in steno order. Floats are not on it. */
    val chain: Map<Bank, List<Int>> = walked.mapValues { it.value.chain }

    val floats: Map<Bank, Set<Int>> = walked.mapValues { it.value.floats }

    private val rank: Map<Bank, Map<Int, Int>> =
        chain.mapValues { (_, keys) -> keys.withIndex().associate { (i, k) -> k to i } }

    /**
     * Whether this key can still be taken, given what the thumb has taken.
     *
     * On the walk that is steno order, which only goes up. A float is off the
     * walk, so the only thing that can stop it is having it already.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        val bank = bankOf(key) ?: return false
        if (key in floats.getValue(bank)) return key !in taken
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
     *
     * The right bank turns the other way. Two thumbs sweep mirrored arcs, so
     * one angle describes both and the sign belongs to the bank.
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
        val radians = Math.toRadians(if (bank == Bank.RIGHT) -tilt.toDouble() else tilt.toDouble())
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
        // A cell reaches one radius from its centre whatever the tilt, and the
        // comb keeps a little air past that so it never runs into the glass.
        val edge = 1f + BREATH
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
     * The field clipped by the bisector against every rival, then by the same
     * bisector against the six lattice neighbours whether the board holds them
     * or not. That second clip is what makes an edge cell a hexagon instead of
     * the whole rest of the field, so the comb ends where it is drawn to end.
     *
     * Past that edge [hit] still answers with the nearest cell, so a thumb that
     * overshoots the comb means the cell it overshot. The drawing is the
     * decision inside the comb and the overshoot rule outside it.
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
        val skipped = { col: Int, row: Int -> skip.any { it.col == col && it.row == row } }
        for (rival in spots.getValue(bank)) {
            if (rival.col == spot.col && rival.row == spot.row) continue
            if (skipped(rival.col, rival.row)) continue
            polygon = nearer(polygon, mine, point(rival, at))
            if (polygon.isEmpty()) break
        }
        for (side in Side.values()) {
            val (col, row) = step(spot, side)
            if (skipped(col, row)) continue
            polygon = nearer(polygon, mine, point(Spot(col, row, GROUND), at))
            if (polygon.isEmpty()) break
        }
        return polygon
    }

    /** The middle of one side of a cell, which is where a portal leaves it. */
    fun edge(spot: Spot, side: Side, at: Placement): Point {
        val c = point(spot, at)
        val a = Math.toRadians(angleOf(side).toDouble())
        val dx = cos(a).toFloat() * SQ3 * 0.5f
        val dy = sin(a).toFloat() * SQ3 * 0.5f
        return Point(
            c.x + (dx * at.ct - dy * at.st) * at.radius,
            c.y + (dx * at.st + dy * at.ct) * at.radius,
        )
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
        // A side with no cell beyond it is never crossed, so it carries nothing.
        val sides = Side.values().filter { cells[at(step(from, it))] != null }
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
        return jumps(bank, from, here).getOrNull(min(slot, STRIDES.size - 1))
    }

    private val jumped = HashMap<Pair<Bank, Spot>, List<Landing?>>()

    /**
     * Where all four strides out of one cell land, chosen together.
     *
     * Two portals that come to rest on the same cell, or on touching ones,
     * spend two sides of the comb on one place. So this is an allocation and
     * not four separate answers: every way of filling the strides is tried and
     * the best is kept, with no two resting places closer than one cell apart.
     *
     * A landing that takes a key is exempt, because a key names itself and
     * nothing can be confused with it. A stride that aims where an earlier one
     * already aims is dead: there is nothing left for it to mean.
     */
    private fun jumps(bank: Bank, from: Spot, here: Int): List<Landing?> =
        jumped.getOrPut(bank to from) {
            val keys = chain.getValue(bank)
            val aimed = mutableSetOf<Int>()
            val choices = STRIDES.map { stride ->
                val aim = keys.getOrNull(min(here + stride, keys.size - 1))
                    ?.takeIf { (rank.getValue(bank)[it] ?: -1) > here && aimed.add(it) }
                if (aim == null) listOf(null) else options(bank, from, here, aim, stride)
            }
            spread(choices)
        }

    /**
     * Where the same stride carries the focus on from a landing it just made.
     *
     * A stride is not spent by one jump. That is what puts a key eight of the
     * order ahead inside one slide of the thumb, where four sides can only name
     * four distances.
     *
     * Null when the ride cannot be taken, so the drawing and the touch agree on
     * when it is there: riding is a crossing like any other, and a key on the
     * way out is taken rather than ridden past.
     */
    fun onward(bank: Bank, land: Landing, taken: List<Int>): Landing? {
        if (land.onKey) return null
        val keys = chain.getValue(bank)
        val ranks = rank.getValue(bank)
        val here = ranks[land.aim] ?: return null
        val aim = keys.getOrNull(min(here + land.stride, keys.size - 1))
            ?.takeIf { (ranks[it] ?: -1) > here } ?: return null
        val next = options(bank, land.spot, here, aim, land.stride)
            .firstOrNull()?.first?.takeIf { it.spot != land.spot } ?: return null
        val gate = byCell.getValue(bank)[at(step(land.spot, toward(land.spot, next.spot)))]
        return next.takeIf { gate != null && resolve(gate, taken) == null }
    }

    /** The side of one cell that points most nearly at another. */
    fun toward(from: Spot, to: Spot): Side {
        val a = lattice(from)
        val b = lattice(to)
        val angle = Math.toDegrees(
            Math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())
        ).toFloat()
        return Side.values().minByOrNull { turn(angleOf(it), angle) }!!
    }

    /** The live keys one step from a cell, which a jump cannot buy again. */
    private fun beside(bank: Bank, spot: Spot, here: Int): Set<Int> {
        val cells = byCell.getValue(bank)
        val ranks = rank.getValue(bank)
        val loose = floats.getValue(bank)
        return Side.values()
            .mapNotNull { cells[at(step(spot, it))] }
            .map { keyOf(it) }
            .filter { it >= 0 && (it in loose || (ranks[it] ?: -1) > here) }
            .toSet()
    }

    /**
     * Where one stride could land: the open ground beside the key it aims at,
     * each worth the keys it *adds* one hop away, and the key itself.
     *
     * Worth is what the jump buys, so a key the thumb can already step onto
     * counts for nothing. A landing that shares its neighbours with the cell
     * being left spends a side of the comb on ground the thumb already has.
     *
     * Ground worth fewer than two new keys is not worth the hop. Late in the
     * order there is none worth it at all, which is why the jump can end on the
     * key and take it: one hop instead of a choice.
     */
    private fun options(
        bank: Bank, from: Spot, here: Int, aim: Int, stride: Int,
    ): List<Pair<Landing, Set<Int>>?> {
        val cells = byCell.getValue(bank)
        val mine = buttons.getValue(bank).filter { it.key == aim }
        val had = beside(bank, from, here)
        val rests = mine.asSequence()
            .flatMap { it.spots.asSequence() }
            .flatMap { spot -> Side.values().asSequence().mapNotNull { cells[at(step(spot, it))] } }
            .filter { it.v == GROUND || it.v == HOLE }
            .distinct()
            .map { it to (beside(bank, it, here) - had) }
            .filter { it.second.size >= 2 }
            .sortedWith(compareByDescending<Pair<Spot, Set<Int>>> { it.second.size }
                .thenBy { it.first.row }.thenBy { it.first.col })
            .map { (spot, brings) -> Landing(spot, aim, false, stride) to brings }
            .toList()
        // Taking a key the thumb could step onto anyway buys nothing either.
        val onKey = mine.firstOrNull()?.spots?.firstOrNull()
            ?.let { Landing(it, aim, true, stride) to (setOf(aim) - had) }
        return rests + listOfNotNull(onKey)
    }

    /**
     * The best way to fill the strides: the most keys covered between them,
     * with the resting places kept apart.
     *
     * What the four portals are worth together is the keys they put within
     * reach, counted once. Adding up what each is worth on its own is a
     * different number, and the wrong one: it pays twice for a key two landings
     * share, and it made the shipped board reach 138 of the 155 keys a thumb
     * can want, where 153 is everything the grid allows.
     *
     * A handful of strides over a handful of cells each, so the whole product
     * is a few hundred arrangements and the exact answer is cheaper than a rule
     * for picking one greedily. Filling them in turn is what put two portals on
     * one cell: an early stride would take the only ground a later one had.
     */
    private fun spread(choices: List<List<Pair<Landing, Set<Int>>?>>): List<Landing?> {
        var best: List<Landing?> = choices.map { null }
        var most = -1
        fun walk(i: Int, sofar: List<Landing?>, rests: List<Spot>, covered: Set<Int>) {
            if (i == choices.size) {
                if (covered.size > most) { most = covered.size; best = sofar }
                return
            }
            val open = choices[i].ifEmpty { listOf(null) }
            var any = false
            for (option in open) {
                val land = option?.first
                if (land != null && !land.onKey && rests.any { hops(it, land.spot) < 2 }) continue
                any = true
                walk(
                    i + 1, sofar + land,
                    if (land != null && !land.onKey) rests + land.spot else rests,
                    covered + (option?.second ?: emptySet()),
                )
            }
            // Every resting place was too close to one already placed. The side
            // goes dead rather than doubling up on a cell.
            if (!any) walk(i + 1, sofar + null, rests, covered)
        }
        walk(0, emptyList(), emptyList(), emptySet())
        return best
    }

    /** How many cells apart two spots are, which the odd-column offset hides. */
    fun hops(a: Spot, b: Spot): Int {
        fun cube(s: Spot): Triple<Int, Int, Int> {
            val z = s.row - (s.col - (s.col and 1)) / 2
            return Triple(s.col, -s.col - z, z)
        }
        val (ax, ay, az) = cube(a)
        val (bx, by, bz) = cube(b)
        return (abs(ax - bx) + abs(ay - by) + abs(az - bz)) / 2
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
        /**
         * The four portal strides, in keys of the steno order.
         *
         * Not a taste, and not a knob. Of every set of four these put the most
         * keys within two hops of a thumb: 153 of the 155 a stroke can want,
         * which is everything the shipped grids allow. `3 5 7 9` reaches 138.
         * `CombTest.the_portals_reach_as_far_as_the_grid_allows` is the claim,
         * and it works the ceiling out from the grid rather than naming it.
         */
        val STRIDES = listOf(3, 4, 7, 10)

        /** The air the comb keeps around itself, in cell radii. */
        const val BREATH = 0.12f

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

/** One bank's keys, split into the walk and what could not join it. */
data class Walk(val chain: List<Int>, val floats: Set<Int>)

/** One drawn button: a run of touching cells that all hold one value. */
data class Button(val key: Int, val v: Char, val spots: List<Spot>)

/**
 * Where a portal puts the focus, the key its stride aimed at, and whether
 * arriving takes that key or only comes to rest beside it.
 */
data class Landing(val spot: Spot, val aim: Int, val onKey: Boolean, val stride: Int)

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
