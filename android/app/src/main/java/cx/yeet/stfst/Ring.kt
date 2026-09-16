package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Where a cell sits: which band out from the middle, and which slot round it. */
data class Cell(val level: Int, val slot: Int)

/** A place in a bank's own frame: how far out, and where round it, in degrees. */
data class Polar(val r: Float, val deg: Float)

/**
 * The rings: nested bands per thumb, read by where the thumb is and never by
 * where it went.
 *
 * Entering a live cell takes it, and everything else on the board is ground. A
 * cell is live while steno order still allows its key; it is ground once the
 * key is taken, once the order has passed it, when it holds no key, and when it
 * is the hub and the thumb has left. Nothing waits on time, distance or
 * turning.
 *
 * The rules that look like extra rules follow. Sliding out to the next ring
 * erases the ring behind, because steno order killed those keys. Turning
 * through a ring adds keys, because you cross them. And two cells far apart on
 * one ring are joined by a straight chord through the middle, because the hub
 * is ground once left.
 *
 * A layout is one value per cell and everything else is recovered from it: the
 * walk is steno order over the keys the text names, a float is a key of the
 * rim, a duplicate is nothing special because a key is a set of cells, and a
 * spoke is where the gaps of every ring line up.
 */
class Ring(val order: String, val config: Config) {

    /**
     * One band of a bank: its slots, whether it is turned half a slot, and the
     * ground it keeps between one slot and the next.
     */
    private class Band(val slots: List<List<Int>>, turned: Boolean, gap: Float) {
        val span: Float = 360f / slots.size
        val rot: Float = if (turned) span / 2f else 0f
        val reach: Float = span / 2f - gap / 2f
    }

    /**
     * One bank: the hub, then the rings outward, and the radii between them.
     *
     * The right bank's angles run the other way, so both thumbs sweep mirrored
     * arcs and "against the walk" is the same motion for each hand.
     */
    private class Layout(val bands: List<Band>, val radii: List<Float>, val dir: Float) {
        val keys: Set<Int> = bands.flatMap { band -> band.slots.flatten() }.toSet()

        /**
         * The rim is the outermost ring, and its keys are the floats: steno
         * order never kills one, so a thumb that travels round the rim to find
         * the one it wants takes every float on the way, which is why they
         * repeat.
         */
        val floats: Set<Int> = bands.last().slots.flatten().toSet()

        /**
         * The spokes: every angle the rings mark out, each slot's middle and
         * each gap's. A thumb travels between them, so this is the whole of
         * where it can be between takes.
         */
        val spokes: List<Float> = bands.drop(1)
            .flatMap { band ->
                band.slots.indices.flatMap {
                    listOf(band.rot + it * band.span, band.rot + (it + 0.5f) * band.span)
                }
            }
            .map { Math.round(((it % 360f) + 360f) % 360f * 10f) / 10f }
            .distinct()
            .sorted()
    }

    private val banks: Map<Bank, Layout> = Bank.values().associateWith { bank ->
        val bands = parse(config.ring(bank))
        Layout(bands, radii(config.radii(bank), bands.size), if (bank == Bank.LEFT) 1f else -1f)
    }

    /**
     * A layout is text: the hub first, then the rings outward, separated by
     * spaces. Within a level, a slash separates slots where one of them holds
     * more than one key, and otherwise a slot is one character. A dot is
     * ground.
     *
     * The hub and the rim are turned half a slot: the rim so its cells sit on
     * the spokes, which makes a float a straight run out, and the hub so its
     * cuts fall on the bank's own axes.
     */
    private fun parse(text: String): List<Band> {
        val levels = text.trim().split(WORDS)
        return levels.mapIndexed { level, line ->
            val slots = (if ('/' in line) line.split('/') else line.map { it.toString() })
                .map { slot -> slot.mapNotNull { order.indexOf(it).takeIf { i -> i >= 0 } } }
            Band(
                slots = slots,
                turned = level == 0 || level == levels.size - 1,
                gap = if (level == 0) 0f else GAP,
            )
        }
    }

    /** The radii between the bands, outward, as fractions of the bank's reach. */
    private fun radii(text: String, bands: Int): List<Float> {
        val given = text.trim().split(WORDS).mapNotNull { it.toFloatOrNull() }
        if (given.size == bands + 1) return given
        return List(bands + 1) { it.toFloat() / bands }
    }

    fun bankOf(key: Int): Bank? = Bank.values().firstOrNull { key in banks.getValue(it).keys }

    fun floats(bank: Bank): Set<Int> = banks.getValue(bank).floats

    /** Every cell of a bank that holds a key. */
    fun cells(bank: Bank): List<Cell> =
        banks.getValue(bank).bands.flatMapIndexed { level, band ->
            band.slots.indices.filter { band.slots[it].isNotEmpty() }.map { Cell(level, it) }
        }

    /** The keys a cell holds, empty for ground and for anywhere off the board. */
    fun keys(bank: Bank, cell: Cell): List<Int> {
        val bands = banks.getValue(bank).bands
        if (cell.level !in bands.indices || cell.slot < 0) return emptyList()
        return bands[cell.level].slots[cell.slot]
    }

    /** Every cell a key is drawn in. A key is a set of cells, not a place. */
    fun cellsOf(bank: Bank, key: Int): List<Cell> =
        cells(bank).filter { key in keys(bank, it) }

    /**
     * Whether this key can still be taken, given what the thumb has taken.
     *
     * Steno order only goes up. A float is off the order, so the only thing
     * that can stop it is having it already.
     */
    fun live(key: Int, taken: List<Int>): Boolean {
        if (key in taken) return false
        val floats = floats(bankOf(key) ?: return false)
        if (key in floats) return true
        return key > (taken.filter { it !in floats }.maxOrNull() ?: -1)
    }

    /** The keys entering this cell takes now, which is what is live in it. */
    fun taking(bank: Bank, cell: Cell, taken: List<Int>): List<Int> =
        keys(bank, cell).filter { live(it, taken) }

    // ---- geometry ----------------------------------------------------------

    /**
     * One formula for the drawing and for the hit test. A bank is a rect; in
     * its normalised frame the rings are superellipses, which fill a
     * rectangular bank instead of wasting the corners.
     */
    fun point(bank: Bank, field: Field, at: Polar): Point {
        val lay = banks.getValue(bank)
        val th = Math.toRadians((((at.deg + config.tilt) * lay.dir) - 90f).toDouble())
        val c = cos(th).toFloat()
        val s = sin(th).toFloat()
        val k = at.r / (abs(c).pow(P) + abs(s).pow(P)).pow(1f / P)
        return Point(
            field.centerX + field.width * 0.5f * k * c,
            field.centerY + field.height * 0.5f * k * s,
        )
    }

    /** The same point read back: how far out it is, and where round the bank. */
    fun polar(bank: Bank, field: Field, x: Float, y: Float): Polar {
        val lay = banks.getValue(bank)
        val u = (x - field.centerX) / (field.width * 0.5f)
        val v = (y - field.centerY) / (field.height * 0.5f)
        val r = (abs(u).pow(P) + abs(v).pow(P)).pow(1f / P)
        val raw = Math.toDegrees(atan2(v, u).toDouble()).toFloat()
        return Polar(r, wrap((raw + 90f) * lay.dir - config.tilt))
    }

    /** The middle of a cell, which is the place a tap on it aims at. */
    fun centre(bank: Bank, cell: Cell): Polar {
        val lay = banks.getValue(bank)
        val band = lay.bands[cell.level]
        return Polar(
            (lay.radii[cell.level] + lay.radii[cell.level + 1]) * 0.5f,
            band.rot + cell.slot * band.span,
        )
    }

    /**
     * The cell this place falls in.
     *
     * A slot of -1 is the gap between two cells, and a level past the last band
     * is off the rim. Both are ground, and neither is nothing: a thumb is
     * somewhere even when it is on no key.
     */
    fun cellAt(bank: Bank, at: Polar): Cell {
        val lay = banks.getValue(bank)
        if (at.r > 1f) return Cell(lay.bands.size, -1)
        var level = 0
        while (level < lay.bands.size - 1 && at.r > lay.radii[level + 1]) level++
        val band = lay.bands[level]
        val md = wrap(at.deg - band.rot)
        val slot = (md / band.span).roundToInt() % band.slots.size
        val off = abs(wrap(md - slot * band.span + 180f) - 180f)
        return if (off > band.reach) Cell(level, -1) else Cell(level, slot)
    }

    fun hit(bank: Bank, field: Field, x: Float, y: Float): Cell =
        cellAt(bank, polar(bank, field, x, y))

    /** Whether this is the hub, where a tap takes the whole slice and a crossing takes nothing. */
    fun isHub(cell: Cell): Boolean = cell.level == 0

    /**
     * The outline of a cell, as the ground it owns less the air between bands.
     *
     * Drawn from the same formula the hit test reads, so the picture cannot
     * promise a cell the touch does not answer with.
     */
    fun outline(bank: Bank, field: Field, cell: Cell): List<Point> {
        val lay = banks.getValue(bank)
        val band = lay.bands[cell.level]
        val inner = lay.radii[cell.level] + if (cell.level == 0) 0f else PAD
        val outer = lay.radii[cell.level + 1] - PAD
        val from = centre(bank, cell).deg - band.reach
        val to = centre(bank, cell).deg + band.reach
        val out = mutableListOf<Point>()
        var d = from
        while (d < to) {
            out.add(point(bank, field, Polar(outer, d)))
            d += STEP
        }
        out.add(point(bank, field, Polar(outer, to)))
        d = to
        while (d > from) {
            out.add(point(bank, field, Polar(inner, d)))
            d -= STEP
        }
        out.add(point(bank, field, Polar(inner, from)))
        return out
    }

    // ---- paths -------------------------------------------------------------

    /**
     * Where a thumb can stand between takes: the hub, or one band at one of the
     * spokes, which are the angles the layout marks out. Every slot's middle is
     * a spoke and so is every gap's, and because the gaps of the rings line up
     * a spoke is a corridor from the hub to the rim.
     */
    private data class Node(val level: Int, val spoke: Int)

    private fun placeOf(bank: Bank, node: Node): Polar {
        if (node.level == 0) return Polar(0f, 0f)
        val lay = banks.getValue(bank)
        return Polar(
            (lay.radii[node.level] + lay.radii[node.level + 1]) * 0.5f,
            lay.spokes[node.spoke],
        )
    }

    private fun nodeOf(bank: Bank, cell: Cell): Node {
        if (isHub(cell)) return Node(0, 0)
        val deg = centre(bank, cell).deg
        val spokes = banks.getValue(bank).spokes
        return Node(cell.level, spokes.indices.minByOrNull {
            abs(wrap(spokes[it] - deg + 180f) - 180f)
        }!!)
    }

    /**
     * The stops a thumb makes to write these keys, and how many times it cuts
     * through the middle to do it.
     *
     * A move between stops is an arc round a ring or a run straight out, so
     * what the thumb crosses is what the layout puts between the two. Nothing
     * on the way may be a live cell the chord does not want.
     *
     * Two cells too far apart on one ring to reach over ground alone are joined
     * through the middle, which is ground once left. That is the shortcut, and
     * each one is a second motion.
     */
    fun path(bank: Bank, keys: List<Int>): Path {
        val lay = banks.getValue(bank)
        val mine = keys.filter { it in lay.keys }.distinct()
        // Floats last: steno order cannot kill one, and the rim is outside
        // every ring, so a float taken early would be a trip back inward.
        val walk = mine.filter { it !in lay.floats }.sorted() +
            mine.filter { it in lay.floats }.sorted()
        val hub = walk.filter { key -> cellsOf(bank, key).all { isHub(it) } }

        var taken = emptyList<Int>()
        var here: Map<Node, Route> = emptyMap()
        if (hub.isNotEmpty()) {
            // The hub is a pie of complete answers, so a tap on the slice that
            // holds them takes the lot.
            val slices = lay.bands[0].slots
            val slot = slices.indexOfFirst { it.toSet() == hub.toSet() }.takeIf { it >= 0 }
                ?: slices.indices.maxByOrNull { slices[it].count { key -> key in hub } } ?: 0
            here = mapOf(Node(0, 0) to Route(listOf(centre(bank, Cell(0, slot))), 0, 0f))
            taken = hub
        }
        for (key in walk - hub.toSet()) {
            here = if (here.isEmpty()) {
                cellsOf(bank, key).associate {
                    nodeOf(bank, it) to Route(listOf(centre(bank, it)), 0, 0f)
                }
            } else {
                reach(bank, here, key, taken)
            }
            if (here.isEmpty()) return Path(emptyList(), walk.size)
            taken = taken + key
        }
        val best = here.values.reduce { a, b -> if (a.better(b)) a else b }
        return Path(best.stops, best.crossings)
    }

    private class Route(val stops: List<Polar>, val crossings: Int, val travel: Float) {
        // A crossing is a second motion and the bank is one unit across, so no
        // amount of travel is worth one.
        val price: Float get() = crossings * 100f + travel

        fun better(other: Route): Boolean = price < other.price
    }

    /**
     * The cheapest way from where the thumb is to each cell holding [key],
     * crossing nothing live it does not want on the way.
     *
     * A crossing of the middle is worth far more than any distance, so a route
     * that keeps to the rings wins whenever there is one.
     */
    private fun reach(
        bank: Bank, from: Map<Node, Route>, key: Int, taken: List<Int>,
    ): Map<Node, Route> {
        val best = HashMap(from)
        val queue = java.util.PriorityQueue<Pair<Float, Node>>(compareBy { it.first })
        for ((node, route) in from) queue.add(route.price to node)
        val ends = mutableMapOf<Node, Route>()
        while (queue.isNotEmpty()) {
            val (price, node) = queue.poll() ?: break
            val route = best.getValue(node)
            if (price > route.price) continue
            if (key in keys(bank, cellAt(bank, placeOf(bank, node)))) {
                ends.getOrPut(node) { route }
                continue
            }
            for ((next, step) in steps(bank, node, route, key, taken)) {
                if (best[next]?.better(step) == true) continue
                best[next] = step
                queue.add(step.price to next)
            }
        }
        return ends
    }

    /** Every move out of a place, and what it would take to make it. */
    private fun steps(
        bank: Bank, node: Node, route: Route, key: Int, taken: List<Int>,
    ): List<Pair<Node, Route>> {
        val lay = banks.getValue(bank)
        val spokes = lay.spokes.size
        val rings = lay.bands.size - 1
        // Where the route already stands, which carries how far round it has
        // come: a stop says which way round the ring it was reached by.
        val from = route.stops.last()
        val out = mutableListOf<Pair<Node, Route>>()
        fun go(next: Node, stops: List<Polar>, crossings: Int, travel: Float) {
            // Entering a live cell takes it, so a route may only cross what is
            // ground to it, and may only end on the key it is going for. The
            // hub is exempt: a thumb that crosses it takes nothing.
            val cell = cellAt(bank, placeOf(bank, next))
            if (next.level > 0 && keys(bank, cell).any { live(it, taken) && it != key }) return
            out.add(
                next to Route(
                    route.stops + stops,
                    route.crossings + crossings,
                    route.travel + travel,
                )
            )
        }
        if (node.level == 0) {
            for (spoke in 0 until spokes) {
                val to = placeOf(bank, Node(1, spoke))
                go(Node(1, spoke), listOf(Polar(0f, to.deg), to), 0, to.r)
            }
            return out
        }
        for (way in listOf(1, -1)) {
            val next = Node(node.level, (node.spoke + way + spokes) % spokes)
            val to = placeOf(bank, next)
            val deg = round(from.deg, to.deg, way)
            go(next, listOf(Polar(to.r, deg)), 0, abs(deg - from.deg) * RADIAN * to.r)
        }
        for (way in listOf(1, -1)) {
            val level = node.level + way
            if (level > rings) continue
            if (level == 0) {
                // Through the middle, which is ground: a second motion, and the
                // only way between two cells a ring keeps apart.
                go(Node(0, 0), listOf(Polar(0f, from.deg)), 1, from.r)
                continue
            }
            val next = Node(level, node.spoke)
            val to = placeOf(bank, next)
            go(next, listOf(Polar(to.r, from.deg)), 0, abs(to.r - from.r))
        }
        return out
    }

    companion object {
        /** The superellipse exponent: 2 is a circle, and this fills a rect. */
        const val P = 3.5f

        /** The ground between two slots of a ring, in degrees. */
        const val GAP = 13f

        /** The air between two bands, as a fraction of the bank's reach. */
        const val PAD = 0.012f

        /** Radians to a degree, for pricing an arc against a run outward. */
        private const val RADIAN = (Math.PI / 180.0).toFloat()

        /** How finely an arc is drawn, in degrees. */
        private const val STEP = 2f

        /** Spaces and newlines alike separate the levels of a layout. */
        private val WORDS = Regex("\\s+")

        private fun wrap(deg: Float): Float = ((deg % 360f) + 360f) % 360f

        /** The same angle, reached by going this way round. */
        private fun round(from: Float, to: Float, way: Int): Float =
            from + way * wrap((to - from) * way)
    }
}

/** What writing a chord costs a thumb: the stops it makes, and its hub crossings. */
data class Path(val stops: List<Polar>, val crossings: Int)

enum class Bank { LEFT, RIGHT }

data class Point(val x: Float, val y: Float)

/** One thumb's half of the keyboard. */
data class Field(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}
