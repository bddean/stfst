package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where a cell sits: which band out from the middle, and which slot round it.
 * Level [Cell.ROW] is the row of modifiers under the rings.
 */
data class Cell(val level: Int, val slot: Int) {
    val isRow: Boolean get() = level == ROW

    companion object {
        const val ROW = -1
    }
}

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
 * spoke is an angle a ring marks out.
 */
class Ring(val order: String, val config: Config) {

    /** The order as keys, which is what every index here counts. */
    val keys: Keys = Keys.of(order)

    /**
     * One band of a bank: its slots, and whether it is turned half a slot.
     */
    private class Band(val slots: List<Slot>, val rot: Float) {
        val span: Float = 360f / slots.size

        /** Half a slot, which is the room one key has to itself. */
        val half: Float = span / 2f
    }

    /**
     * A ring key's body: the arc it runs along, and how wide the stroke drawn
     * on it is.
     *
     * Lengths are in the bank's own units, where the rim is 1, and an angle is
     * carried as the arc it covers at the key's own radius, so a key is the
     * same size on every ring. A key of no length is a circle.
     */
    private class Key(val mid: Polar, val len: Float, val half: Float, val ends: Float) {
        val cap: Float = if (len <= 0f) half else half * ends

        /** Half the width this far along the arc from the key's middle. */
        fun thick(u: Float): Float =
            if (len <= 0f) half else half * (ends + (1f - ends) * (1f - u / len))

        /** Whether a place this far along the arc and this far off it is on the key. */
        fun holds(u: Float, v: Float): Boolean =
            if (abs(u) <= len) abs(v) <= thick(abs(u)) else hypot(abs(u) - len, v) <= cap
    }

    /**
     * One bank: the hub, then the rings outward, and the radii between them.
     *
     * The right bank's angles run the other way, so both thumbs sweep mirrored
     * arcs and "against the walk" is the same motion for each hand.
     */
    /**
     * One slot of a band or of the row: the keys it holds, or the upgrade,
     * which is not a key but a bit over the whole stroke.
     */
    class Slot(val keys: List<Int>, val upgrade: Boolean)

    private class Layout(
        val bands: List<Band>,
        /** The keys steno order cannot kill, whichever bank they sit in. */
        free: Set<Int>,
        /** The modifiers under the rings, one to a slot, left to right. */
        val row: List<Slot>,
        val radii: List<Float>,
        val dir: Float,
    ) {
        val keys: Set<Int> = bands.flatMap { band -> band.slots.flatMap { it.keys } }.toSet() +
            row.flatMap { it.keys }.toSet()

        /**
         * The keys no walk can spend: the ones the order cannot kill, and the
         * ones the row holds, which no walk reaches in the first place.
         */
        val floats: Set<Int> = row.flatMap { it.keys }.toSet() + free.intersect(keys)

        /**
         * The spokes: every angle the rings mark out, each slot's middle and
         * each boundary's. A thumb travels between them, so this is the whole of
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

    /** The keys steno order cannot kill, whichever bank they sit in. */
    private val allFloats: Set<Int> = keysOf(config.floats).toSet()

    private val banks: Map<Bank, Layout> = Bank.values().associateWith { bank ->
        val bands = parse(config.ring(bank))
        Layout(
            bands,
            allFloats,
            row(if (config.showRow) config.mods(bank) else ""),
            radii(config.radii(bank), bands.size),
            if (bank == Bank.LEFT) 1f else -1f,
        )
    }

    /**
     * A layout is text: the hub first, then the rings outward, separated by
     * spaces. Within a level, a slash separates slots where one of them holds
     * more than one key, and otherwise a slot is one character. A dot is
     * ground.
     *
     * The first ring sets the angles and the rings outside it line up with it:
     * a band holding a whole number of groups per key of that ring takes its
     * angles, so every key sees the ring beyond at the same angles. A band that
     * does not divide is staggered half a slot instead, which is the best a
     * band out of step can do. The hub is staggered too, so its cuts fall on
     * the bank's own axes.
     */
    private fun parse(text: String): List<Band> {
        val levels = text.trim().split(WORDS)
            .map { line -> if ('/' in line) line.split('/') else Keys.split(line) }
        val first = levels.getOrNull(1)?.size ?: 1
        return levels.mapIndexed { level, words ->
            val span = 360f / words.size
            val aligned = level == 1 || (first > 0 && words.size % first == 0)
            Band(words.map { slot(it) }, if (aligned) 0f else span / 2f)
        }
    }

    /** The row: one slot per word, left to right. */
    private fun row(text: String): List<Slot> =
        text.trim().split(WORDS).filter { it.isNotEmpty() }
            .map { slot(it) }
            .filter { it.keys.isNotEmpty() || it.upgrade }

    /** One slot's text: the keys it names, or [UPGRADE] for the upgrade. */
    private fun slot(text: String): Slot = Slot(keysOf(text), text == UPGRADE)

    private fun keysOf(text: String): List<Int> =
        Keys.split(text).mapNotNull { keys.indexOf(it).takeIf { i -> i >= 0 } }

    /** The radii between the bands, outward, as fractions of the bank's reach. */
    private fun radii(text: String, bands: Int): List<Float> {
        val given = text.trim().split(WORDS).mapNotNull { it.toFloatOrNull() }
        if (given.size == bands + 1) return given
        return List(bands + 1) { it.toFloat() / bands }
    }

    fun bankOf(key: Int): Bank? = Bank.values().firstOrNull { key in banks.getValue(it).keys }

    /** Whether this cell is the upgrade: the bit that lifts -T to -D and -S to -Z. */
    fun isUpgrade(bank: Bank, cell: Cell): Boolean = slotOf(bank, cell)?.upgrade == true

    private fun slotOf(bank: Bank, cell: Cell): Slot? {
        val lay = banks.getValue(bank)
        if (cell.slot < 0) return null
        if (cell.isRow) return lay.row.getOrNull(cell.slot)
        return lay.bands.getOrNull(cell.level)?.slots?.getOrNull(cell.slot)
    }

    fun floats(bank: Bank): Set<Int> = banks.getValue(bank).floats

    /**
     * Whether this cell is a stud: a cell of a ring that no walk can spend,
     * which is the upgrade or a float.
     *
     * A stud is drawn as a circle a hair outside its band, so a thumb turning
     * along the ring passes under it and reaches it only by aiming at it.
     */
    fun isStud(bank: Bank, cell: Cell): Boolean {
        if (cell.isRow || cell.level == 0) return false
        val slot = slotOf(bank, cell) ?: return false
        return slot.upgrade ||
            (slot.keys.isNotEmpty() && slot.keys.all { it in floats(bank) })
    }

    /** Every cell of a bank that holds a key, the row included. */
    fun cells(bank: Bank): List<Cell> {
        val lay = banks.getValue(bank)
        return lay.bands.flatMapIndexed { level, band ->
            band.slots.indices
                .filter { band.slots[it].keys.isNotEmpty() || band.slots[it].upgrade }
                .map { Cell(level, it) }
        } + lay.row.indices.map { Cell(Cell.ROW, it) }
    }

    /** The keys a cell holds, empty for ground and for anywhere off the board. */
    fun keys(bank: Bank, cell: Cell): List<Int> = slotOf(bank, cell)?.keys ?: emptyList()

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
        if (key in allFloats) return true
        val floats = floats(bankOf(key) ?: return false)
        if (key in floats) return true
        return key > (taken.filter { it !in floats }.maxOrNull() ?: -1)
    }

    /** The keys entering this cell takes now, which is what is live in it. */
    fun taking(bank: Bank, cell: Cell, taken: List<Int>): List<Int> =
        keys(bank, cell).filter { live(it, taken) }

    // ---- the bank in its field ---------------------------------------------

    /** The row's share of the bank's depth, zero for a bank with no row. */
    private fun rowShare(bank: Bank): Float =
        if (banks.getValue(bank).row.isEmpty()) 0f else config.rowHeight.coerceIn(0f, 0.4f)

    /**
     * The bank itself inside the field it was given: the field less the ground
     * kept round it, held against the thumb's own edge and against the floor.
     *
     * A field wider than a square bank is what an uneven split leaves, and the
     * slack belongs to the far side of the glass rather than to the middle,
     * because the thumb reaches the edge it starts from.
     */
    fun box(bank: Bank, field: Field): Field {
        val pad = config.bankPad.coerceIn(0f, 0.4f) * field.width
        val over = Field(
            field.left + pad, field.top + pad, field.right - pad, field.bottom - pad
        )
        if (!config.squareBanks) return over
        val share = 1f - rowShare(bank)
        val side = min(over.width, over.height * share)
        val slack = (over.width - side) * config.bankPull.coerceIn(0f, 1f)
        val left = if (bank == Bank.LEFT) over.left + slack else over.right - side - slack
        return Field(left, over.bottom - side / share, left + side, over.bottom)
    }

    /**
     * The horizontal run each thumb's field covers on a board this wide.
     *
     * Split, the two fields tile the width; on the edges each hugs its own
     * side and the ground between them belongs to neither.
     */
    fun spans(
        width: Float, sides: Boolean,
    ): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        if (sides) {
            val wide = width * config.splitLandscape.coerceIn(0.15f, 0.5f)
            return (0f..wide) to ((width - wide)..width)
        }
        val edge = width * config.fieldSplit.coerceIn(0.1f, 0.9f)
        return (0f..edge) to (edge..width)
    }

    /**
     * How deep the keys are, and the band over the app above them, on a board
     * this wide with this much screen behind it.
     *
     * Square rings want a square bank, so the keys take the depth asked for or
     * the depth the wider bank can use, whichever is less. `room` is the window
     * there actually is, where there is one: measuring past it would put the
     * top of the keys off the glass, so the keys and the band give it up
     * together.
     */
    fun deep(
        width: Float, screen: Float, landscape: Boolean, room: Float? = null,
    ): Pair<Float, Float> {
        val share = if (landscape) config.landscapeHeight else config.keyboardHeight
        var keys = screen * share.coerceIn(0.15f, 0.95f)
        if (config.squareBanks) {
            val (left, right) = spans(width, landscape && config.landscapeSides)
            keys = min(
                keys,
                max(
                    depth(Bank.LEFT, left.endInclusive - left.start),
                    depth(Bank.RIGHT, right.endInclusive - right.start),
                ),
            )
        }
        var band = screen * config.bandShare().coerceIn(0f, 0.6f)
        if (room != null && keys + band > room) {
            keys *= room / (keys + band)
            band = room - keys
        }
        return keys to band
    }

    /**
     * How deep a field this wide lets the bank be.
     *
     * Square rings want a square bank, so depth past this is room the app gives
     * up for nothing.
     */
    fun depth(bank: Bank, width: Float): Float {
        val pad = config.bankPad.coerceIn(0f, 0.4f) * width
        return (width - 2f * pad) / (1f - rowShare(bank)) + 2f * pad
    }

    /** How deep the row of modifiers is, zero for a bank with none. */
    fun rowDeep(bank: Bank, field: Field): Float = box(bank, field).height * rowShare(bank)

    /**
     * The rings' own field: the bank less the strip the row stands on, and
     * square, because a thumb sweeps an arc and a stretched ring answers the
     * same sweep with different keys at different angles.
     */
    fun rings(bank: Bank, field: Field): Field {
        val on = box(bank, field)
        return Field(on.left, on.top, on.right, on.bottom - rowDeep(bank, field))
    }

    /** One row key's rectangle, the row being an even split of the bank's width. */
    fun rowCell(bank: Bank, field: Field, slot: Int): Field {
        val on = box(bank, field)
        val slots = banks.getValue(bank).row.size.coerceAtLeast(1)
        val wide = on.width / slots
        return Field(
            on.left + slot * wide,
            on.bottom - rowDeep(bank, field),
            on.left + (slot + 1) * wide,
            on.bottom,
        )
    }

    // ---- rings -------------------------------------------------------------

    /**
     * The superellipse exponent at this distance out: 2 is a circle, and the
     * larger it grows the closer the ring comes to a rectangle.
     *
     * It rises outward, so the middle stays round, where a thumb turns, and the
     * outer rings take the corners of the bank, where the room is.
     */
    private fun shape(r: Float): Float =
        config.shapeHub + (config.shapeRim - config.shapeHub) * r.coerceIn(0f, 1f)

    /**
     * One formula for the drawing and for the hit test. A bank is a rect; in
     * its normalised frame the rings are superellipses, which fill a
     * rectangular bank instead of wasting the corners.
     */
    fun point(bank: Bank, field: Field, at: Polar): Point {
        val lay = banks.getValue(bank)
        val on = rings(bank, field)
        val th = Math.toRadians((((at.deg + config.tilt) * lay.dir) - 90f).toDouble())
        val k = at.r * stretch(bank, at)
        return Point(
            on.centerX + on.width * 0.5f * k * cos(th).toFloat(),
            on.centerY + on.height * 0.5f * k * sin(th).toFloat(),
        )
    }

    /**
     * How far the superellipse carries a place at this angle, per unit of [Polar.r].
     *
     * It is one at the axes and more towards the corners, which is the room a
     * rectangular bank has there. A key divides its width by it, so the corners
     * hold a key of the same size rather than a stretched one.
     */
    private fun stretch(bank: Bank, at: Polar): Float {
        val dir = banks.getValue(bank).dir
        val th = Math.toRadians((((at.deg + config.tilt) * dir) - 90f).toDouble())
        val p = shape(at.r)
        return 1f / (abs(cos(th).toFloat()).pow(p) + abs(sin(th).toFloat()).pow(p)).pow(1f / p)
    }

    /**
     * The same point read back: how far out it is, and where round the bank.
     *
     * The exponent depends on the answer, so the answer is found by repeating
     * the question. Each ring is further out than the one inside it whatever
     * exponent it has, so the iteration has one fixed point to find.
     */
    fun polar(bank: Bank, field: Field, x: Float, y: Float): Polar {
        val lay = banks.getValue(bank)
        val on = rings(bank, field)
        val u = abs((x - on.centerX) / (on.width * 0.5f))
        val v = abs((y - on.centerY) / (on.height * 0.5f))
        var r = 1f
        repeat(SOLVE) {
            val p = shape(r)
            r = (u.pow(p) + v.pow(p)).pow(1f / p)
        }
        val raw = Math.toDegrees(
            atan2((y - on.centerY) / (on.height * 0.5f), (x - on.centerX) / (on.width * 0.5f))
                .toDouble()
        ).toFloat()
        return Polar(r, wrap((raw + 90f) * lay.dir - config.tilt))
    }

    /**
     * One ring key's body, which is both what is drawn and what a touch is
     * answered by. Everything around it is ground, and that ground is the room
     * a slide has to pass between keys.
     */
    private fun key(bank: Bank, cell: Cell): Key {
        val lay = banks.getValue(bank)
        val mid = centre(bank, cell)
        if (isStud(bank, cell)) {
            val wide = config.keyWidth * config.studWidth.coerceIn(0.2f, 1f) * 0.5f
            return Key(mid, 0f, wide / stretch(bank, mid), 1f)
        }
        val wide = config.keyWidth.coerceIn(0.02f, 1f) * 0.5f / stretch(bank, mid)
        val ends = config.keyEnds.coerceIn(0f, 1f)
        // The slot's half, as the arc it covers at this key's radius.
        val room = lay.bands[cell.level].half * RADIAN * mid.r
        val len = (config.keySpread.coerceIn(0f, 1f) * room - wide * ends).coerceAtLeast(0f)
        return Key(mid, len, wide, ends)
    }

    private fun Key.holds(at: Polar): Boolean =
        holds(signed(at.deg - mid.deg) * RADIAN * mid.r, at.r - mid.r)

    /** The middle of a cell, which is the place a tap on it aims at. */
    fun centre(bank: Bank, cell: Cell): Polar {
        val lay = banks.getValue(bank)
        val band = lay.bands[cell.level]
        val inner = lay.radii[cell.level]
        val outer = lay.radii[cell.level + 1]
        // A stud stands a hair outside the middle of its band, which is what
        // leaves the ring's own radius clear under it.
        val out = if (isStud(bank, cell)) config.studOut.coerceIn(0f, 0.8f) else 0f
        return Polar(
            (inner + outer) * 0.5f + (outer - inner) * 0.5f * out,
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
        val region = regionAt(bank, at)
        // The hub is a pie and fills its circle, so a place inside it is always
        // on one of its slices.
        if (region.level == 0 || region.slot < 0) return region
        return if (key(bank, region).holds(at)) region else Cell(region.level, -1)
    }

    /**
     * The slot a place falls in whether or not it is on the key: the band, and
     * the nearest slot round it.
     *
     * [cellAt] answers a place off a key body with a slot of -1, because a cell
     * is what a thumb takes. This is what it crosses, which is what a gesture
     * over the ground is read from.
     */
    fun regionAt(bank: Bank, at: Polar): Cell {
        val lay = banks.getValue(bank)
        if (at.r > 1f) return Cell(lay.bands.size, -1)
        if (at.r <= lay.radii[1]) return Cell(0, slotAt(lay.bands[0], at.deg))
        val level = (1 until lay.bands.size).minByOrNull {
            abs(at.r - (lay.radii[it] + lay.radii[it + 1]) * 0.5f)
        } ?: return Cell(lay.bands.size, -1)
        return Cell(level, slotAt(lay.bands[level], at.deg))
    }

    /** The same, read off the glass: the row is one region of its own. */
    fun region(bank: Bank, field: Field, x: Float, y: Float): Cell {
        if (banks.getValue(bank).row.isNotEmpty() && y >= rings(bank, field).bottom) {
            return hit(bank, field, x, y)
        }
        return regionAt(bank, polar(bank, field, x, y))
    }

    /** A band's inner and outer edge, as fractions of the bank's reach. */
    fun edges(bank: Bank, level: Int): Pair<Float, Float> {
        val radii = banks.getValue(bank).radii
        return radii[level] to radii[level + 1]
    }

    /** The outermost ring: the last band of the walk. */
    fun outer(bank: Bank): Int = banks.getValue(bank).bands.size - 1

    /** How many slots a band is cut into, which is what a turn counts. */
    fun slots(bank: Bank, level: Int): Int =
        banks.getValue(bank).bands.getOrNull(level)?.slots?.size ?: 0

    private fun slotAt(band: Band, deg: Float): Int =
        (wrap(deg - band.rot) / band.span).roundToInt() % band.slots.size

    /**
     * The cell under a touch: the row if the touch is in the strip under the
     * rings, and the ring the polar frame answers with otherwise.
     */
    fun hit(bank: Bank, field: Field, x: Float, y: Float): Cell {
        val row = banks.getValue(bank).row
        if (row.isNotEmpty() && y >= rings(bank, field).bottom) {
            val on = box(bank, field)
            val slot = ((x - on.left) / (on.width / row.size)).toInt()
            return Cell(Cell.ROW, slot.coerceIn(0, row.size - 1))
        }
        return cellAt(bank, polar(bank, field, x, y))
    }

    /** Where a cell's label sits: the middle of the cell, in the field's own pixels. */
    fun middle(bank: Bank, field: Field, cell: Cell): Point {
        if (cell.isRow) {
            val rect = rowCell(bank, field, cell.slot)
            return Point(rect.centerX, rect.centerY)
        }
        return point(bank, field, centre(bank, cell))
    }

    /**
     * The same place on the bank, read in another field.
     *
     * A place on the rings rather than a place on the glass, so a second
     * drawing of the bank shows where the thumb is in its own frame.
     */
    fun mirror(bank: Bank, from: Field, to: Field, x: Float, y: Float): Point {
        val strip = rings(bank, from).bottom
        if (rowDeep(bank, from) > 0f && y >= strip) {
            val here = box(bank, from)
            val there = box(bank, to)
            val across = (x - here.left) / here.width
            val down = (y - strip) / rowDeep(bank, from)
            return Point(
                there.left + across * there.width,
                rings(bank, to).bottom + down * rowDeep(bank, to),
            )
        }
        return point(bank, to, polar(bank, from, x, y))
    }

    /** Whether this is the hub, where a tap takes the whole slice and a crossing takes nothing. */
    fun isHub(cell: Cell): Boolean = cell.level == 0

    /**
     * The outline of a cell: the hub's slice of the pie, or a ring key's body.
     *
     * Drawn from the same formula the hit test reads, so the picture cannot
     * promise a cell the touch does not answer with.
     */
    fun outline(bank: Bank, field: Field, cell: Cell): List<Point> {
        val lay = banks.getValue(bank)
        if (cell.isRow) {
            val rect = rowCell(bank, field, cell.slot)
            val air = rect.height * ROW_PAD
            return listOf(
                Point(rect.left + air, rect.top + air),
                Point(rect.right - air, rect.top + air),
                Point(rect.right - air, rect.bottom - air),
                Point(rect.left + air, rect.bottom - air),
            )
        }
        // The hub is a pie: its slices meet, and together they are the circle.
        if (cell.level == 0) {
            val band = lay.bands[0]
            val outer = lay.radii[1]
            val from = centre(bank, cell).deg - band.half
            val to = centre(bank, cell).deg + band.half
            val out = mutableListOf(point(bank, field, Polar(0f, from)))
            var d = from
            while (d < to) {
                out.add(point(bank, field, Polar(outer, d)))
                d += STEP
            }
            out.add(point(bank, field, Polar(outer, to)))
            return out
        }
        val key = key(bank, cell)
        val out = mutableListOf<Point>()
        fun add(u: Float, v: Float) = out.add(
            point(bank, field, Polar(key.mid.r + v, key.mid.deg + u / (RADIAN * key.mid.r)))
        )
        for (i in 0..SIDE) {
            val u = -key.len + 2f * key.len * i / SIDE
            add(u, key.thick(abs(u)))
        }
        for (i in 0..CAP) {
            val a = Math.PI.toFloat() * i / CAP
            add(key.len + key.cap * sin(a), key.cap * cos(a))
        }
        for (i in 0..SIDE) {
            val u = key.len - 2f * key.len * i / SIDE
            add(u, -key.thick(abs(u)))
        }
        for (i in 0..CAP) {
            val a = Math.PI.toFloat() * i / CAP
            add(-key.len - key.cap * sin(a), -key.cap * cos(a))
        }
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
        // A float is off the walk: steno order cannot kill one, so a chord
        // never has to reach it in any particular place or at any point.
        val walk = keys.filter { it in lay.keys && it !in lay.floats }.distinct().sorted()
        if (walk.isEmpty()) return Path(emptyList(), 0)
        val hub = walk.filter { key -> cellsOf(bank, key).all { isHub(it) } }

        var taken = emptyList<Int>()
        var here: Map<Node, Route> = emptyMap()
        if (hub.isNotEmpty()) {
            // The hub is a pie of complete answers, so a tap on the slice that
            // holds them takes the lot.
            val slices = lay.bands[0].slots.map { it.keys }
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
            // A turn back past a whole cell of the outer ring is a modifier, so
            // a route takes one only where the ring leaves it no way forward.
            val back = if (way < 0 && node.level == rings) BACK else 0f
            go(next, listOf(Polar(to.r, deg)), 0, abs(deg - from.deg) * RADIAN * to.r + back)
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
        /** What a step backwards round the outer ring costs a route. */
        private const val BACK = 10f

        /** How many times [polar] repeats itself to land on its own exponent. */
        private const val SOLVE = 6

        /** The air round a row key, as a fraction of the row's depth. */
        private const val ROW_PAD = 0.07f

        /** Radians to a degree, for pricing an arc against a run outward. */
        private const val RADIAN = (Math.PI / 180.0).toFloat()

        /** How finely the hub's arcs are drawn, in degrees. */
        private const val STEP = 2f

        /** How many points a key's side and its end cap are drawn with. */
        private const val SIDE = 10
        private const val CAP = 12

        /** The word that puts the upgrade in a row, rather than a key. */
        const val UPGRADE = ">"

        /** Spaces and newlines alike separate the levels of a layout. */
        private val WORDS = Regex("\\s+")

        private fun wrap(deg: Float): Float = ((deg % 360f) + 360f) % 360f

        /** The same angle as a turn of at most half a circle, either way. */
        private fun signed(deg: Float): Float = wrap(deg + 180f) - 180f

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
