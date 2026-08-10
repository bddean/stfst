package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The order the compiled dictionaries are built over. */
private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"

private const val UNIT = 1000f

class RingTest {

    private val config = Config()
    private val ring = Ring(ORDER, config)
    private val far = config.reach(Radius.FAR) * UNIT
    private val near = config.reach(Radius.NEAR) * UNIT

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    /** A point that distance and heading away from the origin. */
    private fun at(angle: Float, distance: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angle.toDouble())
        return Pair((cos(radians) * distance).toFloat(), (sin(radians) * distance).toFloat())
    }

    /** Slide from the origin at a heading and see which key it takes. */
    private fun slide(bank: Bank, after: Int, angle: Float, distance: Float): Int? {
        val (x, y) = at(angle, distance)
        return ring.pick(bank, after, 0f, 0f, x, y, UNIT)
    }

    @Test
    fun the_banks_split_at_the_star() {
        assertEquals(0 until 13, ring.keys(Bank.LEFT))
        assertEquals(13 until 26, ring.keys(Bank.RIGHT))
        assertEquals(Bank.LEFT, ring.bankOf(key('A')))
        assertEquals(Bank.RIGHT, ring.bankOf(key('*')))
    }

    @Test
    fun every_key_has_a_slot() {
        for (k in ORDER.indices) {
            assertNotNull("no slot for ${ORDER[k]}", ring.slotOf(k))
        }
    }

    /**
     * The layout invariant: at every state the thumb can be in, each key still
     * available has a slot of its own. That is what lets the ring always offer the
     * next key without putting it where the thumb already is.
     */
    @Test
    fun live_keys_never_share_a_slot() {
        for (bank in Bank.values()) {
            for (after in -1 until ORDER.length) {
                val taken = mutableSetOf<Pair<Radius, Float>>()
                for (k in ring.live(bank, after)) {
                    val slot = ring.slotOf(k)!!
                    val at = Pair(slot.radius, ((slot.angle % 360f) + 360f) % 360f)
                    assertTrue("$bank after $after: ${ORDER[k]} collides at $at", taken.add(at))
                }
            }
        }
    }

    @Test
    fun steno_order_is_the_only_constraint() {
        fun live(bank: Bank, after: Char) =
            ring.live(bank, key(after)).map { ORDER[it] }.joinToString("")

        assertEquals("PWHRAO", live(Bank.LEFT, 'K'))
        assertEquals("O", live(Bank.LEFT, 'A'))
        assertEquals("", live(Bank.RIGHT, 'z'))
        // The number and modifier keys come before every letter, so a stroke that
        // has taken one letter can no longer take them.
        assertEquals("", live(Bank.LEFT, 'O'))
        assertTrue(ring.live(Bank.LEFT, -1).map { ORDER[it] }.joinToString("") == "#@^+STKPWHRAO")
    }

    @Test
    fun a_short_slide_chooses_nothing() {
        assertNull(slide(Bank.LEFT, -1, 0f, config.minSlide * UNIT - 1f))
    }

    @Test
    fun a_slide_to_where_a_key_is_drawn_takes_that_key() {
        // Straight up is S on the far ring and nothing on the near one, so a short
        // slide up falls through to the far key rather than choosing at random.
        assertEquals(key('S'), slide(Bank.LEFT, -1, -90f, far))
        assertEquals(key('A'), slide(Bank.LEFT, -1, 0f, near))
        assertEquals(key('K'), slide(Bank.LEFT, -1, 0f, far))
    }

    /**
     * The boundary sits between the rings, not on one of them, so a slide that
     * falls short of a far key still reads as far and not as its near neighbour.
     *
     * Undershooting was what made the consonants unreachable: after one consonant
     * the near ring holds only the vowels, so every short flick became A or O.
     */
    @Test
    fun a_slide_most_of_the_way_to_a_far_key_is_still_far() {
        assertEquals(key('P'), slide(Bank.LEFT, key('K'), 45f, config.ringSplit() * UNIT + 1f))
        assertEquals(key('O'), slide(Bank.LEFT, key('K'), 45f, config.ringSplit() * UNIT - 1f))
        // Two thirds of the way to a far key is a far key, so a thumb aiming at a
        // consonant has room to fall short of it.
        assertTrue("the boundary is too far out", config.ringSplit() < config.farReach * 0.7f)
        assertEquals(key('W'), slide(Bank.LEFT, key('K'), 90f, far * 0.7f))
        assertEquals(key('R'), slide(Bank.LEFT, key('K'), 180f, far * 2f))
    }

    /**
     * `KAOEPT` writes "keep the". Each thumb taps its first key and slides for the
     * rest, so this is the four slides that follow the two taps.
     */
    @Test
    fun the_worked_example_from_the_design() {
        var after = key('K')
        for ((angle, distance, expected) in listOf(
            Triple(0f, near, 'A'),
            Triple(45f, near, 'O'),
        )) {
            val got = slide(Bank.LEFT, after, angle, distance)
            assertEquals("left wanted $expected", key(expected), got)
            after = got!!
        }
        after = key('E')
        for ((angle, distance, expected) in listOf(
            Triple(0f, far, 'p'),
            Triple(180f, far, 't'),
        )) {
            val got = slide(Bank.RIGHT, after, angle, distance)
            assertEquals("right wanted $expected", key(expected), got)
            after = got!!
        }
    }

    @Test
    fun a_slide_can_never_go_back_in_steno_order() {
        for (bank in Bank.values()) {
            for (after in -1 until ORDER.length) {
                for (angle in 0 until 360 step 15) {
                    for (distance in listOf(80f, 150f, 250f, 600f)) {
                        val got = slide(bank, after, angle.toFloat(), distance) ?: continue
                        assertTrue("$bank after $after took $got", got > after)
                        assertTrue("$bank took a key of the other bank", ring.bankOf(got) == bank)
                    }
                }
            }
        }
    }

    // The tap layout. A touch-down has a field to aim at and no direction to read,
    // so it is a honeycomb over the field and owes the rings nothing.

    private val field = Field(0f, 0f, 420f, 900f)

    @Test
    fun the_tap_layout_is_a_honeycomb() {
        val rows = ring.entry(Bank.LEFT, field)
            .groupBy { it.y }
            .toSortedMap()
            .values
            .map { row -> row.map { it.x }.sorted() }
        assertEquals(listOf(3, 2, 3, 2, 3), rows.map { it.size })
        // A row of two sits between the columns of a row of three, half a pitch over.
        val pitch = field.width / config.entryCols
        assertEquals(rows[0][0] + pitch * 0.5f, rows[1][0], 0.001f)
    }

    /**
     * An early key is where the sliding starts, so it gets a middle row. A late key
     * ends the stroke and slides nowhere, so the edge costs it nothing.
     */
    @Test
    fun the_middle_rows_go_to_the_earliest_keys() {
        val cells = ring.entry(Bank.LEFT, field).associateBy { it.key }
        val middle = field.top + field.height * 0.5f
        fun row(symbol: Char) = abs(cells.getValue(key(symbol)).y - middle)

        // Row by row, each one no further from the middle than the next.
        val rows = "#@^+STKPWHRAO".map { row(it) }
        assertEquals(rows, rows.sorted())
        assertTrue("the earliest key is not in a middle row", row('#') < row('O'))

        // And a row still reads left to right, which is what makes a key findable.
        val top = cells.values.filter { it.y == cells.getValue(key('#')).y }
        assertEquals(top.sortedBy { it.x }, top.sortedBy { it.key })
    }

    @Test
    fun reading_order_fills_from_the_top() {
        val plain = Ring(ORDER, Config().also { it.entryCentral = false })
        val cells = plain.entry(Bank.LEFT, field)
        assertEquals(cells.map { it.y }, cells.map { it.y }.sorted())
    }

    @Test
    fun every_key_has_a_cell_of_its_own() {
        for (bank in Bank.values()) {
            val cells = ring.entry(bank, field)
            assertEquals(ring.keys(bank).count(), cells.size)
            assertEquals(ring.keys(bank).toList(), cells.map { it.key })
        }
    }

    @Test
    fun a_tap_on_a_cell_takes_that_cell_s_key() {
        for (cell in ring.entry(Bank.LEFT, field)) {
            assertEquals(cell.key, ring.entryAt(Bank.LEFT, field, cell.x, cell.y, -1))
        }
    }

    /** No dead space: every point of the field belongs to some key. */
    @Test
    fun the_tap_layout_covers_the_field() {
        for (x in 0 until field.width.toInt() step 7) {
            for (y in 0 until field.height.toInt() step 11) {
                val got = ring.entryAt(Bank.LEFT, field, x.toFloat(), y.toFloat(), -1)
                assertNotNull("nothing at $x $y", got)
                assertEquals(Bank.LEFT, ring.bankOf(got!!))
            }
        }
    }

    /** Fixed positions, but steno order still decides what a tap may answer with. */
    @Test
    fun a_tap_cannot_go_back_in_steno_order() {
        val cells = ring.entry(Bank.LEFT, field)
        for (after in cells.map { it.key }) {
            for (cell in cells) {
                val got = ring.entryAt(Bank.LEFT, field, cell.x, cell.y, after) ?: continue
                assertTrue("after $after took $got", got > after)
            }
        }
        assertNull(ring.entryAt(Bank.LEFT, field, 10f, 10f, key('O')))
    }

    // The drawn regions, which are what the picture of a slide is made of.

    @Test
    fun the_regions_cover_every_direction() {
        for (radius in Radius.values()) {
            val sweep = ring.sectors(Bank.LEFT, key('K'))
                .filter { it.radius == radius }
                .sumOf { (it.to - it.from).toDouble() }
            assertEquals("$radius", 360.0, sweep, 0.001)
        }
    }

    /**
     * The regions are the decision, not a picture of it. A region wide where the
     * slot is narrow is the point: three live keys share the whole circle.
     */
    @Test
    fun a_region_is_where_a_slide_of_its_angle_goes() {
        for (after in -1 until ORDER.length) {
            for (bank in Bank.values()) {
                for (sector in ring.sectors(bank, after)) {
                    val mid = (sector.from + sector.to) * 0.5f
                    val got = slide(bank, after, mid, config.reach(sector.radius) * UNIT)
                    assertEquals("$bank after $after at $mid", sector.key, got)
                }
            }
        }
        // Two live keys on a ring of eight slots still own half the circle each.
        val wide = ring.sectors(Bank.LEFT, key('H')).filter { it.radius == Radius.FAR }
        assertEquals(1, wide.size)
        assertEquals(360f, wide[0].to - wide[0].from, 0.001f)
    }

    @Test
    fun separation_takes_the_short_way_round() {
        assertEquals(10f, Ring.separation(-5f, 5f), 0.001f)
        assertEquals(20f, Ring.separation(350f, 10f), 0.001f)
        assertEquals(180f, Ring.separation(0f, 180f), 0.001f)
    }
}
