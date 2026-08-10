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

    /**
     * The two ring layout, which the default is not any more.
     *
     * A short slide and a long one meant different keys, and metering a distance
     * under a thumb is the channel that went wrong. What these tests hold on to is
     * that the layout still works when it is asked for.
     */
    private fun twoRings(also: (Config) -> Unit = {}) = Config().also {
        it.farLeft = "STKPWHR"
        it.nearLeft = "AO#@^+"
        it.farRight = "frpblgts"
        it.nearRight = "*EUdz"
        also(it)
    }

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    /** A point that distance and heading away from the origin. */
    private fun at(angle: Float, distance: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angle.toDouble())
        return Pair((cos(radians) * distance).toFloat(), (sin(radians) * distance).toFloat())
    }

    /** Slide from the origin at a heading and see which key it takes. */
    private fun slide(bank: Bank, after: Int, angle: Float, distance: Float, held: Int = -1): Int? {
        val (x, y) = at(angle, distance)
        return ring.pick(bank, after, 0f, 0f, x, y, UNIT, held)
    }

    /**
     * Slide toward where a key is drawn.
     *
     * Which angle that is depends on what the stroke has taken, because the live
     * keys share the ring out between them. Asking the ring is the contract: aim at
     * a key and you get that key.
     */
    private fun toward(bank: Bank, after: Int, symbol: Char): Int? {
        val slot = ring.slotOf(key(symbol), after)!!
        return slide(bank, after, slot.angle, config.reach(slot.radius) * UNIT)
    }

    @Test
    fun the_banks_split_at_the_star() {
        assertEquals(0 until 13, ring.keys(Bank.LEFT))
        assertEquals(13 until 26, ring.keys(Bank.RIGHT))
        assertEquals(Bank.LEFT, ring.bankOf(key('A')))
        assertEquals(Bank.RIGHT, ring.bankOf(key('*')))
    }

    @Test
    fun every_key_is_on_a_ring() {
        for (k in ORDER.indices) {
            assertNotNull("no ring for ${ORDER[k]}", ring.ringOf(k))
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
                    val slot = ring.slotOf(k, after)!!
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
        for (after in -1 until ORDER.length) {
            for (bank in Bank.values()) {
                for (k in ring.live(bank, after)) {
                    assertEquals(
                        "$bank after $after aiming at ${ORDER[k]}",
                        k, toward(bank, after, ORDER[k])
                    )
                }
            }
        }
    }

    /** The earliest live key of a ring sits at that ring's start angle. */
    @Test
    fun a_ring_starts_at_its_start_angle() {
        assertEquals(key('#'), slide(Bank.LEFT, -1, config.startFarLeft, far))
        assertEquals(key('P'), slide(Bank.LEFT, key('K'), config.startFarLeft, far))
        // Six keys follow K, so the second of them is a sixth of the way round.
        assertEquals(key('W'), slide(Bank.LEFT, key('K'), config.startFarLeft + 60f, far))
    }

    /**
     * One ring, so how far a slide went says nothing beyond whether it happened.
     *
     * Distance was the channel that went wrong. Angle answers on its own, and the
     * only thing a length decides now is that a resting thumb has not asked for
     * anything.
     */
    @Test
    fun with_one_ring_only_the_angle_decides() {
        val after = key('K')
        val p = ring.slotOf(key('P'), after)!!.angle
        assertNull(slide(Bank.LEFT, after, p, config.minSlide * UNIT - 1f))
        for (reach in listOf(1.01f, 1.5f, 3f, 8f, 30f)) {
            assertEquals(
                "at ${reach}x the minimum",
                key('P'), slide(Bank.LEFT, after, p, config.minSlide * UNIT * reach)
            )
        }
    }

    /**
     * Every live key of a ring holds the same slice of it, clockwise in steno order
     * from the start angle.
     */
    @Test
    fun a_shared_ring_is_shared_evenly() {
        for (after in -1 until ORDER.length) {
            for (bank in Bank.values()) {
                for (radius in Radius.values()) {
                    val band = ring.live(bank, after).filter { ring.ringOf(it) == radius }
                    if (band.isEmpty()) continue
                    val step = 360f / band.size
                    band.forEachIndexed { i, k ->
                        assertEquals(
                            "$bank $radius after $after: ${ORDER[k]}",
                            config.startAngle(bank, radius) + i * step,
                            ring.slotOf(k, after)!!.angle,
                            0.001f,
                        )
                    }
                }
            }
        }
    }

    /**
     * What sharing the ring out buys. The average region is 360 over the count
     * either way, so the gain is in the worst one: a key with a live neighbour on
     * each side is squeezed to the gap between them when the slots are fixed.
     */
    @Test
    fun sharing_the_ring_out_widens_the_smallest_region() {
        fun smallest(r: Ring): Float = r.sectors(Bank.RIGHT, key('E'))
            .filter { it.radius == Radius.NEAR }
            .minOf { it.to - it.from }

        // After E the near ring holds U, d and z, and d has a live neighbour on
        // each side of it.
        assertEquals(45f, smallest(Ring(ORDER, twoRings { it.spread = false })), 2f)
        assertEquals(120f, smallest(Ring(ORDER, twoRings())), 2f)
    }

    /**
     * A thumb resting on a boundary is nearer one key and then the other on every
     * touch sample. The key already chosen keeps the answer inside the margin.
     */
    @Test
    fun the_key_already_chosen_survives_a_wobble() {
        val after = key('K')
        val p = key('P')
        val w = key('W')
        // Two keys next to each other on the ring, and the boundary between them.
        val edge = (ring.slotOf(p, after)!!.angle + ring.slotOf(w, after)!!.angle) * 0.5f
        assertEquals(p, slide(Bank.LEFT, after, edge - 1f, far))
        assertEquals(w, slide(Bank.LEFT, after, edge + 1f, far))
        // Holding P, a wobble past the boundary is still P.
        assertEquals(p, slide(Bank.LEFT, after, edge + 1f, far, held = p))
        // A real turn is not a wobble.
        assertEquals(w, slide(Bank.LEFT, after, edge + config.hysteresisDegrees + 2f, far, held = p))
    }

    /**
     * The boundary sits between the rings, not on one of them, so a slide that
     * falls short of a far key still reads as far and not as its near neighbour.
     *
     * Undershooting was what made the consonants unreachable: after one consonant
     * the near ring holds only the vowels, so every short flick became A or O.
     */
    /** With two rings asked for, the boundary is biased toward the far one. */
    @Test
    fun a_slide_most_of_the_way_to_a_far_key_is_still_far() {
        val two = twoRings()
        val r = Ring(ORDER, two)
        val after = key('K')
        val p = r.slotOf(key('P'), after)!!.angle
        fun slide(distance: Float): Int? {
            val (x, y) = at(p, distance)
            return r.pick(Bank.LEFT, after, 0f, 0f, x, y, UNIT)
        }
        assertTrue("the boundary is too far out", two.ringSplit() < two.farReach * 0.7f)
        assertEquals(key('P'), slide(two.ringSplit() * UNIT + 1f))
        assertEquals(key('P'), slide(two.farReach * UNIT * 0.7f))
        // Short of the boundary is the near ring, whatever the thumb was aiming at.
        assertEquals(Radius.NEAR, r.ringOf(slide(two.ringSplit() * UNIT - 1f)!!))
    }

    /**
     * `KAOEPT` writes "keep the". Each thumb taps its first key and slides for the
     * rest, so this is the four slides that follow the two taps.
     */
    @Test
    fun the_worked_example_from_the_design() {
        var after = key('K')
        for (wanted in "AO") {
            val got = toward(Bank.LEFT, after, wanted)
            assertEquals("left wanted $wanted", key(wanted), got)
            after = got!!
        }
        after = key('E')
        for (wanted in "pt") {
            val got = toward(Bank.RIGHT, after, wanted)
            assertEquals("right wanted $wanted", key(wanted), got)
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

    /**
     * The drawn cell is the target, so a tap has to land inside the shape it is
     * answered by. Over a shrunken lattice too, where the cells at the edge grow to
     * cover the field the lattice no longer reaches.
     */
    @Test
    fun a_tap_lands_inside_the_cell_it_is_answered_by() {
        for (tall in listOf(1f, 0.5f)) {
            val r = Ring(ORDER, Config().also { it.entryTall = tall })
            for (x in 4 until field.width.toInt() step 13) {
                for (y in 4 until field.height.toInt() step 17) {
                    val at = Point(x.toFloat(), y.toFloat())
                    val got = r.entryAt(Bank.LEFT, field, at.x, at.y, -1)
                    assertNotNull("nothing at $x $y", got)
                    val cell = r.cellOf(got!!, Bank.LEFT, field, -1)!!
                    assertTrue("$x $y is outside ${ORDER[got]} at tall $tall", inside(cell, at))
                }
            }
        }
    }

    /** Convex, and wound one way, so one side of every edge is the inside. */
    private fun inside(polygon: List<Point>, at: Point): Boolean {
        var sign = 0
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[(i + 1) % polygon.size]
            val cross = (b.x - a.x) * (at.y - a.y) - (b.y - a.y) * (at.x - a.x)
            val side = if (cross > 0.01f) 1 else if (cross < -0.01f) -1 else 0
            if (side == 0) continue
            if (sign == 0) sign = side else if (sign != side) return false
        }
        return true
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
        for (rings in listOf(config, twoRings())) {
            val r = Ring(ORDER, rings)
            for (radius in Radius.values()) {
                if (rings.keys(Bank.LEFT, radius).isEmpty()) continue
                val sweep = r.sectors(Bank.LEFT, key('K'))
                    .filter { it.radius == radius }
                    .sumOf { (it.to - it.from).toDouble() }
                assertEquals("$radius", 360.0, sweep, 0.001)
            }
        }
    }

    /**
     * The regions are the decision, not a picture of it. A region wide where the
     * slot is narrow is the point: three live keys share the whole circle.
     */
    @Test
    fun a_region_is_where_a_slide_of_its_angle_goes() {
        for (rings in listOf(config, twoRings())) {
            val r = Ring(ORDER, rings)
            for (after in -1 until ORDER.length) {
                for (bank in Bank.values()) {
                    for (sector in r.sectors(bank, after)) {
                        val mid = (sector.from + sector.to) * 0.5f
                        val (x, y) = at(mid, rings.reach(sector.radius) * UNIT)
                        assertEquals(
                            "$bank after $after at $mid",
                            sector.key, r.pick(bank, after, 0f, 0f, x, y, UNIT)
                        )
                    }
                }
            }
        }
        // One live key owns every direction there is.
        val alone = ring.sectors(Bank.LEFT, key('A'))
        assertEquals(1, alone.size)
        assertEquals(360f, alone[0].to - alone[0].from, 0.001f)
    }

    @Test
    fun separation_takes_the_short_way_round() {
        assertEquals(10f, Ring.separation(-5f, 5f), 0.001f)
        assertEquals(20f, Ring.separation(350f, 10f), 0.001f)
        assertEquals(180f, Ring.separation(0f, 180f), 0.001f)
    }
}
