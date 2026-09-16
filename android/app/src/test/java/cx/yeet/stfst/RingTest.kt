package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"

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

    private fun keys(chord: String): List<Int> = chord.map { ORDER.indexOf(it) }

    private fun spell(keys: Collection<Int>): String =
        keys.sorted().joinToString("") { ORDER[it].toString() }

    /**
     * Every cell centre hits its own cell, which is what a tap is answered by.
     */
    @Test
    fun a_tap_lands_inside_the_cell_it_is_answered_by() {
        for (bank in Bank.values()) {
            for (cell in ring.cells(bank)) {
                val p = ring.point(bank, field(bank), ring.centre(bank, cell))
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
            assertEquals("$bank $chord", spell(keys(chord)), write(bank, keys(chord)))
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
            val keys = ring.cells(bank).flatMap { ring.keys(bank, it) }.distinct()
            for (from in keys) {
                for (to in keys.filter { ring.live(it, listOf(from)) }) {
                    val chord = setOf(from, to)
                    assertEquals(
                        "$bank: ${ORDER[from]} then ${ORDER[to]}",
                        spell(chord), write(bank, chord.toList()),
                    )
                }
            }
        }
    }

    /**
     * The gaps of every ring line up, so a straight run from the hub to the rim
     * takes nothing and a float is a run straight out.
     */
    @Test
    fun the_corridor_is_open() {
        for (bank in Bank.values()) {
            val floats = ring.floats(bank)
            for (spoke in 0 until 6) {
                val deg = 30f + spoke * 60f
                var r = 0.02f
                var reachedFloat = false
                while (r <= 1f) {
                    val cell = ring.cellAt(bank, Polar(r, deg))
                    val keys = ring.keys(bank, cell)
                    if (keys.isNotEmpty() && !ring.isHub(cell)) {
                        assertEquals(
                            "the corridor at $deg\u00b0 in $bank should be clear to the rim",
                            keys.toSet(), keys.filter { it in floats }.toSet(),
                        )
                        reachedFloat = true
                    }
                    r += 0.005f
                }
                assertTrue("the corridor at $deg\u00b0 in $bank should end on a float", reachedFloat)
            }
        }
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
            val config = config.copy(ringLeft = "./S/ST/T .$order AOAOAO ^#+^#+")
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
            val config = config.copy(ringRight = "./E/EU/U $order tststs dzdzdz *@*@*@")
            val other = share(Ring(ORDER, config), Bank.RIGHT, right)
            assertTrue(
                "$order writes ${pct(other)} of the right corpus, over ${pct(shippedRight)}",
                other <= shippedRight + 1e-9,
            )
        }

        // The pinky keys are two keys everywhere rather than four in four of
        // six slots, so every combination of them is one motion.
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
        val points = samples(bank, ring.path(bank, keys).stops)
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
