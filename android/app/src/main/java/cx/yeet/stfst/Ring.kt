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

    /**
     * Which ring each key sits on, worked out once.
     *
     * The drawing probes the decision function a few hundred times a frame, and each
     * probe asks about every live key, so none of this can afford to be a search.
     */
    private val rings: Array<Radius?> = Array(order.length) { key ->
        val bank = bankOf(key)
        val symbol = order[key]
        when {
            config.far(bank).indexOf(symbol) >= 0 -> Radius.FAR
            config.near(bank).indexOf(symbol) >= 0 -> Radius.NEAR
            else -> null
        }
    }

    /** The live keys of one bank, and of one ring of one bank, per state. */
    private val lives = HashMap<Int, List<Int>>()
    private val bands = HashMap<Int, List<Int>>()

    /** Which ring a key sits on, or null for a key the configuration left off both. */
    fun ringOf(key: Int): Radius? = rings[key]

    /**
     * A key's ring and the angle it answers to, given what the stroke has taken.
     *
     * Spread, the live keys of a ring share it evenly: the earliest sits at the
     * start angle and the rest follow clockwise. That is what keeps the smallest
     * target from being small. Fixed slots give each key a permanent angle, and a
     * key with a live neighbour on each side is then squeezed to the gap between
     * them: three live keys on an eight slot ring can leave the middle one 45
     * degrees while its neighbours hold 157 each. Even spreading gives all three
     * 120. The average is 360 over the count either way, so what spreading buys is
     * the worst case, not the mean.
     *
     * Clockwise in steno order from the start angle, spread or not, so the order
     * round the ring is the order of the chord.
     */
    fun slotOf(key: Int, after: Int): Slot? {
        val radius = ringOf(key) ?: return null
        val bank = bankOf(key)
        if (!config.spread) {
            val index = config.keys(bank, radius).indexOf(order[key])
            return Slot(radius, config.startAngle(bank, radius) + index * (360f / config.slots(radius)))
        }
        val band = liveOn(bank, radius, after)
        val index = band.indexOf(key)
        if (index < 0) return null
        return Slot(radius, config.startAngle(bank, radius) + index * (360f / band.size))
    }

    /**
     * The keys a thumb can still take. Empty means the bank is finished, which is
     * what an empty ring tells the thumb.
     */
    fun live(bank: Bank, after: Int): List<Int> =
        lives.getOrPut(at(bank, after)) { keys(bank).filter { it > after && rings[it] != null } }

    /** The live keys of one ring of one bank, in steno order. */
    private fun liveOn(bank: Bank, radius: Radius, after: Int): List<Int> =
        bands.getOrPut(at(bank, after) * Radius.values().size + radius.ordinal) {
            live(bank, after).filter { rings[it] == radius }
        }

    /** One number per (bank, state), so the caches can be plain maps. */
    private fun at(bank: Bank, after: Int) = (after + 1) * 2 + bank.ordinal

    /**
     * The key a slide from (ox, oy) to (px, py) chooses, or null when the slide is
     * too short to mean anything.
     *
     * `held` is the key this slide chose a moment ago, and it keeps the answer
     * unless another one beats it by more than `Hold on to a key`. A thumb resting
     * on a boundary would otherwise swap between two keys on every touch sample,
     * and the highlight and the tick would swap with it.
     */
    fun pick(
        bank: Bank,
        after: Int,
        ox: Float,
        oy: Float,
        px: Float,
        py: Float,
        unit: Float,
        held: Int = NONE,
    ): Int? {
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
        val best = nearest(live, wanted, after, heading) ?: nearest(live, null, after, heading)
        return steady(best, held, live, after, heading)
    }

    private fun nearest(live: List<Int>, radius: Radius?, after: Int, heading: Float): Int? {
        var best: Int? = null
        var bestOff = Float.MAX_VALUE
        for (key in live) {
            val slot = slotOf(key, after) ?: continue
            if (radius != null && slot.radius != radius) continue
            val off = separation(slot.angle, heading)
            if (off < bestOff && off <= config.snapDegrees) {
                best = key
                bestOff = off
            }
        }
        return best
    }

    /** The held key wins ties and near ties, which is what stops the flicker. */
    private fun steady(best: Int?, held: Int, live: List<Int>, after: Int, heading: Float): Int? {
        if (best == null || held == NONE || held == best || held !in live) return best
        val margin = config.hysteresisDegrees
        if (margin <= 0f) return best
        val a = slotOf(held, after) ?: return best
        val b = slotOf(best, after) ?: return best
        // Only against a rival on the same ring: crossing between rings is a
        // distance the thumb travelled on purpose, not a wobble at a boundary.
        if (a.radius != b.radius) return best
        return if (separation(a.angle, heading) <= separation(b.angle, heading) + margin) held else best
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
        val pitch = field.width / config.entryCols.coerceAtLeast(1) * config.entryWide
        val depth = field.height / rows.size * config.entryTall
        val middle = field.top + field.height * config.entryAt
        val top = middle - (rows.size - 1) * 0.5f * depth
        val places = mutableListOf<Point>()
        for (r in rowOrder(rows.size)) {
            val y = top + r * depth
            for (c in 0 until rows[r]) {
                places.add(Point(field.centerX + (c - (rows[r] - 1) * 0.5f) * pitch, y))
            }
        }
        return keys.mapIndexed { i, key -> Cell(key, places[i].x, places[i].y) }
    }

    /**
     * The ground a cell owns, as a polygon, or null for a key a tap cannot answer
     * with.
     *
     * The field clipped by the bisector against every rival, which is the definition
     * of the cell rather than a drawing of it. Worked out this way it stays right for
     * any lattice, and it covers the whole field however small the lattice is: shrink
     * the rows and the cells at the edge grow, because a tap out there is still
     * nearest the key it was always nearest.
     */
    fun cellOf(key: Int, bank: Bank, field: Field, after: Int): List<Point>? {
        val cells = entry(bank, field).filter { it.key > after && rings[it.key] != null }
        val mine = cells.firstOrNull { it.key == key } ?: return null
        var polygon = listOf(
            Point(field.left, field.top), Point(field.right, field.top),
            Point(field.right, field.bottom), Point(field.left, field.bottom),
        )
        for (rival in cells) {
            if (rival.key == key) continue
            polygon = nearer(polygon, mine, rival)
            if (polygon.isEmpty()) break
        }
        return polygon
    }

    /** The part of a polygon nearer `mine` than `rival`. */
    private fun nearer(polygon: List<Point>, mine: Cell, rival: Cell): List<Point> {
        // Positive on our side of the bisector. The squared distances differ by a
        // linear expression, so the boundary is a straight edge and this is a clip.
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

    /**
     * Which row steno order fills first.
     *
     * The middle row outward, so the earliest keys land in the middle of the field.
     * An early key is where the sliding starts, since most of the stroke is still
     * ahead of it, and a thumb at the edge of the glass has nowhere to slide to. A
     * late key ends the stroke and slides nowhere, so the edge costs it nothing.
     *
     * By row and not by cell. Ranking every cell by its distance from the middle
     * gives the earliest keys the most central cells and leaves the layout
     * scattered, and thirteen keys nobody can find are worse than thirteen keys
     * with less room. A row still reads left to right.
     */
    private fun rowOrder(rows: Int): List<Int> =
        if (!config.entryCentral) (0 until rows).toList()
        else (0 until rows).sortedBy { abs(it - (rows - 1) * 0.5f) }

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
            .filter { it.key > after && ringOf(it.key) != null }
            .minByOrNull { hypot(it.x - x, it.y - y) }
            ?.key

    companion object {
        /** No key. What `held` is when a slide has not chosen anything yet. */
        const val NONE = -1

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

/** A key's place on the tap layout. [Ring.cellOf] is the ground it owns. */
data class Cell(val key: Int, val x: Float, val y: Float)

data class Point(val x: Float, val y: Float)

/** One thumb's half of the keyboard. */
data class Field(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
}
