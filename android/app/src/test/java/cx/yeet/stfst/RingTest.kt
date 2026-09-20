package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

// The order the app ships, `🦐` and all: a key outside the basic plane is
// what tells a key number from a string index.
private const val ORDER = "🦐#@^+STKPWHRAO*EUfrpblgtsdz"
private val KEYS = Keys.of(ORDER)

/**
 * What the layout text fixes: where the cells are, what a thumb can write in
 * one motion, and that the ring orders are the ones the corpus chose.
 *
 * None of it needs a device and none of it needs the engine.
 */
class RingTest {

    private val config = Config()
    private val ring = Ring(ORDER, config)

    /** A bank about the size of a thumb's half of a phone, in tenths of a millimetre. */
    private fun field(bank: Bank): Field =
        if (bank == Bank.LEFT) Field(0f, 0f, 350f, 620f)
        else Field(350f, 0f, 700f, 620f)

    private fun keys(chord: String): List<Int> = chord.map { KEYS.indexOf(it) }

    private fun spell(keys: Collection<Int>): String =
        keys.sorted().joinToString("") { KEYS[it] }

    /**
     * Every cell centre hits its own cell, which is what a tap is answered by.
     */
    @Test
    fun a_tap_lands_inside_the_cell_it_is_answered_by() {
        for (bank in Bank.values()) {
            for (cell in ring.cells(bank)) {
                val p = ring.middle(bank, field(bank), cell)
                assertEquals(
                    "$bank $cell, holding ${spell(ring.keys(bank, cell))}",
                    cell, ring.hit(bank, field(bank), p.x, p.y),
                )
            }
        }
    }

    /**
     * For every chord the corpus holds, walk the path the drawing claims and
     * check that what comes out is the chord.
     *
     * The one that matters. A path the layout promises and a thumb cannot make
     * is how the picture and the decision drift apart.
     */
    @Test
    fun every_chord_is_a_path_a_thumb_can_make() {
        var walked = 0
        for ((bank, chord) in chords()) {
            // The floats are in the row, which a walk neither reaches nor needs.
            val onRings = keys(chord).filterNot { it in ring.floats(bank) }
            assertEquals("$bank $chord", spell(onRings), write(bank, keys(chord)))
            walked++
        }
        assertTrue("there should be chords to walk", walked > 1000)
    }

    /**
     * From any key, every key steno order still allows can be entered without
     * taking anything else. Nothing needs a second stroke.
     */
    @Test
    fun every_key_is_reachable() {
        for (bank in Bank.values()) {
            // A float is off the walk, wherever it is drawn, so reachability is
            // about the keys steno order carries.
            val keys = ring.cells(bank).filterNot { it.isRow }
                .flatMap { ring.keys(bank, it) }.distinct()
                .filterNot { it in ring.floats(bank) }
            for (from in keys) {
                for (to in keys.filter { ring.live(it, listOf(from)) }) {
                    val chord = setOf(from, to)
                    assertEquals(
                        "$bank: ${KEYS[from]} then ${KEYS[to]}",
                        spell(chord), write(bank, chord.toList()),
                    )
                }
            }
        }
    }

    /**
     * Every key of the rim is a run straight out from the hub, taking nothing
     * on the way.
     *
     * Not at any one angle: the ground between the keys of a ring is what the
     * run passes through, so the property is that some line out from the middle
     * lands on one of the key's cells and crosses nothing else.
     */
    @Test
    fun the_rim_is_a_run_straight_out() {
        for (bank in Bank.values()) {
            val rim = ring.cells(bank).filter { it.level == ring.outer(bank) }
            assertTrue("$bank should have a rim", rim.isNotEmpty())
            // Per key, not per cell: the ring inside can stand in front of one
            // cell, and what the repeats are for is that another is clear.
            val (studs, keys) = rim.partition { ring.isStud(bank, it) }
            for (cells in keys.groupBy { ring.keys(bank, it) }.values) assertTrue(
                "no run out from the middle reaches any of $cells in $bank alone",
                cells.any { straightOut(bank, it) },
            )
            // A stud stands on a key of the ring inside, so the run that
            // reaches it is the one that carries on out from that key.
            for (stud in studs) assertTrue(
                "no run on out from the ring inside reaches $stud in $bank",
                runsOut(bank, stud, ring.centre(bank, stud).deg, from = 0.7f),
            )
        }
    }

    /** Whether some run out from the middle lands on this cell and nothing else. */
    private fun straightOut(bank: Bank, cell: Cell): Boolean =
        (-30..30).any { step -> runsOut(bank, cell, ring.centre(bank, cell).deg + step) }

    /** Whether a line out from here at this angle lands on [cell] and nothing else. */
    private fun runsOut(bank: Bank, cell: Cell, deg: Float, from: Float = 0.02f): Boolean {
        var r = from
        while (r <= 1f) {
            val at = ring.cellAt(bank, Polar(r, deg))
            if (at == cell) return true
            if (!ring.isHub(at) && ring.keys(bank, at).isNotEmpty()) return false
            r += 0.005f
        }
        return false
    }

    /**
     * The shipped ring orders are the ones the corpus chose: no other
     * arrangement of the same keys writes more of it in one motion.
     *
     * Recomputed rather than asserted, so a layout change answers for itself.
     */
    @Test
    fun the_order_is_the_one_measured() {
        val left = corpus("L")
        val shippedLeft = share(ring, Bank.LEFT, left)
        assertTrue("the left bank writes ${pct(shippedLeft)} in one motion", shippedLeft > 0.9)
        for (order in arrangements("PHRWK")) {
            val config = config.copy(ringLeft = "S/ST/T $order AOAOAO")
            val other = share(Ring(ORDER, config), Bank.LEFT, left)
            assertTrue(
                "$order writes ${pct(other)} of the left corpus, over ${pct(shippedLeft)}",
                other <= shippedLeft + 1e-9,
            )
        }

        val right = corpus("R1")
        val shippedRight = share(ring, Bank.RIGHT, right)
        assertTrue("the right ring writes ${pct(shippedRight)} in one motion", shippedRight > 0.95)
        for (order in arrangements("frbglp")) {
            val config = config.copy(ringRight = "E/EU/U $order tststs")
            val other = share(Ring(ORDER, config), Bank.RIGHT, right)
            assertTrue(
                "$order writes ${pct(other)} of the right corpus, over ${pct(shippedRight)}",
                other <= shippedRight + 1e-9,
            )
        }

        // The pinky keys are one bit over the stroke, so nothing about them
        // costs a motion.
        assertEquals(1.0, share(ring, Bank.RIGHT, corpus("R2")), 0.0001)
    }

    private fun pct(share: Double): String = "%.1f%%".format(share * 100)

    /** Every way a ring's keys can sit round it. */
    private fun arrangements(slots: String): List<String> {
        if (slots.length <= 1) return listOf(slots)
        return slots.indices.flatMap { i ->
            arrangements(slots.removeRange(i, i + 1)).map { slots[i] + it }
        }
    }

    /** The share of chord occurrences this layout writes in one motion. */
    private fun share(ring: Ring, bank: Bank, rows: List<Pair<String, Int>>): Double {
        var whole = 0L
        var once = 0L
        for ((chord, count) in rows) {
            whole += count
            if (ring.path(bank, keys(chord)).crossings == 0) once += count
        }
        return once.toDouble() / whole
    }

    /**
     * Walk the path the layout claims for a chord, sampling finely enough that
     * no cell is stepped over, and answer with what the thumb took.
     *
     * Through [Chorder], so what is checked is the keyboard's own rule and not
     * a second copy of it.
     */
    private fun write(bank: Bank, keys: List<Int>): String {
        val chorder = Chorder(ring, config) {}
        chorder.place(bank, field(bank))
        val stops = ring.path(bank, keys).stops
        if (stops.isEmpty()) return ""
        val points = samples(bank, stops)
        chorder.down(bank, 0, points[0].x, points[0].y)
        for (p in points.drop(1)) chorder.move(0, p.x, p.y)
        return spell(chorder.thumb(bank).keys)
    }

    /** The stops joined up: a step round a ring is an arc, a step between them a run out. */
    private fun samples(bank: Bank, stops: List<Polar>): List<Point> {
        val out = mutableListOf(ring.point(bank, field(bank), stops[0]))
        for (i in 1 until stops.size) {
            val from = stops[i - 1]
            val to = stops[i]
            val reach = max(field(bank).width, field(bank).height) * 0.5f
            val far = reach * (abs(to.r - from.r) +
                abs(to.deg - from.deg) * (Math.PI.toFloat() / 180f) * max(from.r, to.r))
            val steps = max(2, ceil(far / STEP).toInt())
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                val at = Polar(
                    from.r + (to.r - from.r) * t,
                    from.deg + (to.deg - from.deg) * t,
                )
                out.add(ring.point(bank, field(bank), at))
            }
        }
        return out
    }

    /** Chords to walk: the corpus, and the corpus with the vowels a stroke adds. */
    private fun chords(): List<Pair<Bank, String>> {
        val out = mutableListOf<Pair<Bank, String>>()
        for ((chord, _) in corpus("L")) {
            for (vowels in listOf("", "A", "O", "AO")) out.add(Bank.LEFT to chord + vowels)
        }
        val pinky = listOf("") + corpus("R2").map { it.first }
        for ((chord, _) in corpus("R1")) {
            for (vowels in listOf("", "E", "U", "EU")) {
                for (end in pinky) out.add(Bank.RIGHT to vowels + chord + end)
            }
        }
        return out
    }

    /** One bank's chords and how often the corpus writes them. */
    private fun corpus(tag: String): List<Pair<String, Int>> =
        javaClass.classLoader!!.getResourceAsStream("corpus.txt")!!.bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.trim().split(" ") }
            .filter { it[0] == tag }
            .map { it[1] to it[2].toInt() }

    companion object {
        /** How far apart two samples of a path are, in the field's own units. */
        private const val STEP = 1.5f
    }
}
