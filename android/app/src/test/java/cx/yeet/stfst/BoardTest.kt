package cx.yeet.stfst

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"
private const val UNIT = 1000f

/**
 * The board: fixed cells read by position, take order with the island first,
 * floats past the outer edge, and the dead island as the teleport.
 */
class BoardTest {

    private val config = Config()
    private val board = Board(ORDER, config)
    private val field = Field(0f, 0f, 420f, 900f)

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    private fun cell(bank: Bank, symbol: Char): Cell =
        board.cells(bank, field).first { it.key == key(symbol) }

    private fun takes(bank: Bank) = board.keys(bank).map { ORDER[it] }.joinToString("")

    @Test
    fun the_board_strings_are_the_banks() {
        assertEquals("AO^STKPWHR#+", takes(Bank.LEFT))
        assertEquals("EUfrpblgtsdz@*", takes(Bank.RIGHT))
    }

    /** A key named as a float leaves the ring, so old saved layouts keep working. */
    @Test
    fun a_float_wins_a_key_named_twice() {
        val stale = Board(ORDER, Config().also {
            it.boardIslandRight = "@*EU"
            it.boardRingLeft = "#^+STKPWHR"
        })
        assertEquals("AO^STKPWHR#+", stale.keys(Bank.LEFT).map { ORDER[it] }.joinToString(""))
        assertTrue(stale.halo(ORDER.indexOf('#')))
        assertTrue(stale.halo(ORDER.indexOf('*')))
        assertFalse(stale.island(ORDER.indexOf('@')))
    }

    /** Moved from the left, where the machine's number bar is not. */
    @Test
    fun the_at_sign_lives_on_the_right() {
        assertEquals(Bank.RIGHT, board.bankOf(key('@')))
        assertEquals(Bank.LEFT, board.bankOf(key('#')))
    }

    private fun taken(symbols: String) = symbols.map { key(it) }

    /**
     * The island is unordered, the way the machine's thumb cluster is pressed:
     * before the ring its keys go in any order, and only a repeat is barred.
     */
    @Test
    fun the_island_is_unordered() {
        assertTrue(board.live(key('A'), taken("O")))
        assertTrue(board.live(key('O'), taken("A")))
        assertFalse(board.live(key('A'), taken("A")))
        assertEquals(key('A'), board.resolve(key('A'), taken("O")))
        assertTrue(board.live(key('*'), taken("EU")))
    }

    @Test
    fun the_first_ring_key_closes_the_island() {
        assertFalse(board.live(key('A'), taken("S")))
        assertFalse(board.live(key('O'), taken("T")))
        assertFalse(board.live(key('U'), taken("Ef")))
    }

    /** The number bar joins whenever the machine's would: floats never close it. */
    @Test
    fun a_float_does_not_close_the_island() {
        assertTrue(board.live(key('A'), taken("#")))
        assertTrue(board.live(key('E'), taken("@*")))
    }

    /** The machine presses everything together, so the ring has no order. */
    @Test
    fun the_ring_has_no_order() {
        assertTrue(board.live(key('S'), taken("T")))
        assertTrue(board.live(key('f'), taken("z")))
        assertTrue(board.live(key('K'), taken("AOS")))
        assertFalse(board.live(key('T'), taken("T")))
    }

    @Test
    fun everything_is_live_at_the_start() {
        for (bank in Bank.values()) {
            for (k in board.keys(bank)) {
                assertTrue("${ORDER[k]} is dead at the start", board.live(k, emptyList()))
            }
        }
    }

    @Test
    fun a_live_cell_yields_itself() {
        assertEquals(key('K'), board.resolve(key('K'), taken("T")))
        assertEquals(key('A'), board.resolve(key('A'), emptyList()))
    }

    /** The doorway fallback, alive only while the teleport is off. */
    private val doors = Board(ORDER, Config().also { it.boardTeleport = false })

    @Test
    fun a_dead_island_cell_yields_its_doorway_key() {
        // After S the left island is closed, and A's doorway leads to R.
        assertEquals(key('R'), doors.resolve(key('A'), taken("S")))
        assertEquals(key('H'), doors.resolve(key('O'), taken("S")))
        // The right island, once the walk is on the ring.
        assertEquals(key('d'), doors.resolve(key('E'), taken("Ef")))
        assertEquals(key('g'), doors.resolve(key('U'), taken("f")))
    }

    @Test
    fun a_doorway_shuts_when_its_key_is_spent() {
        assertNull(doors.resolve(key('A'), taken("R")))
        assertNull(doors.resolve(key('O'), taken("H")))
    }

    /** With the teleport on, a dead island cell is plain ground. */
    @Test
    fun the_teleport_replaces_the_doorways() {
        assertNull(board.portal(key('A')))
        assertNull(board.resolve(key('A'), taken("S")))
    }

    /** An island that swallowed the ring would teleport on every approach. */
    @Test
    fun the_island_never_reaches_the_ring() {
        val wild = Board(ORDER, Config().also { it.boardIsle = 9f })
        assertTrue(wild.isle(field) <= wild.inner(field))
    }

    @Test
    fun a_taken_cell_with_no_doorway_is_plain_ground() {
        assertNull(board.resolve(key('T'), taken("T")))
        assertNull(board.resolve(key('+'), taken("+S")))
    }

    /** A doorway across the banks would hand one thumb the other's key. */
    @Test
    fun a_doorway_cannot_cross_the_banks() {
        val crossed = Board(ORDER, Config().also {
            it.boardPortals = "A>t"
            it.boardTeleport = false
        })
        assertNull(crossed.resolve(ORDER.indexOf('A'), listOf(ORDER.indexOf('S'))))
    }

    // The geometry: one staggered ring of slices round a corridor round the
    // island, roads inside and outside, lanes in the stagger.

    private fun sweep(bank: Bank, r: Float): Set<Char> {
        val middle = board.center(field)
        val got = mutableSetOf<Char>()
        for (tenth in 0 until 3600 step 5) {
            val angle = Math.toRadians(tenth / 10.0)
            board.hit(
                bank, field,
                middle.x + (Math.cos(angle) * r).toFloat(),
                middle.y + (Math.sin(angle) * r).toFloat(),
            )?.let { got.add(ORDER[it]) }
        }
        return got
    }

    /** Mid ring the slices overlap: every direction is a key, none an island's. */
    @Test
    fun the_middle_of_the_ring_covers_every_direction() {
        val mid = (board.inner(field) + board.outer(field)) * 0.5f
        assertEquals(config.boardRingLeft.toSet(), sweep(Bank.LEFT, mid))
        assertEquals(config.boardRingRight.toSet(), sweep(Bank.RIGHT, mid))
    }

    /** A sweep along a lane crosses every other key, which is a machine row. */
    @Test
    fun each_lane_takes_every_other_key() {
        val notch = config.boardStagger * board.pitch(field) * 0.5f
        for ((bank, ring) in listOf(
            Bank.LEFT to config.boardRingLeft, Bank.RIGHT to config.boardRingRight
        )) {
            val inward = ring.filterIndexed { i, _ -> i % 2 == 0 }.toSet()
            val outward = ring.filterIndexed { i, _ -> i % 2 == 1 }.toSet()
            assertEquals(inward, sweep(bank, board.inner(field) + notch))
            assertEquals(outward, sweep(bank, board.outer(field) - notch))
        }
    }

    @Test
    fun a_cell_holds_its_own_reference_point() {
        for (bank in Bank.values()) {
            for (cell in board.cells(bank, field)) {
                assertEquals(cell.key, board.hit(bank, field, cell.x, cell.y))
            }
        }
    }

    /**
     * No two slices share an angle, and neighbours alternate radius, so no
     * cell ever blocks the radial way in or out of another.
     */
    @Test
    fun alternate_slices_stagger() {
        val middle = board.center(field)
        for ((top, bottom) in listOf('T' to 'K', 'P' to 'W', 'H' to 'R')) {
            val a = cell(Bank.LEFT, top)
            val b = cell(Bank.LEFT, bottom)
            val at = Math.toDegrees(
                Math.atan2((a.y - middle.y).toDouble(), (a.x - middle.x).toDouble())
            ).toFloat()
            val bt = Math.toDegrees(
                Math.atan2((b.y - middle.y).toDouble(), (b.x - middle.x).toDouble())
            ).toFloat()
            assertEquals(
                "$top and $bottom are not one step apart",
                board.step(Bank.LEFT), Ring.separation(at, bt), 0.1f
            )
            assertTrue(
                "$top is not the inward slice",
                hypot(a.x - middle.x, a.y - middle.y) < hypot(b.x - middle.x, b.y - middle.y)
            )
        }
    }

    /** A cell is only the button that is drawn: past the outer edge is road. */
    @Test
    fun past_the_outer_edge_is_ground() {
        val middle = board.center(field)
        val k = cell(Bank.LEFT, 'K')
        val angle = Math.atan2((k.y - middle.y).toDouble(), (k.x - middle.x).toDouble())
        val r = board.outer(field) * 1.15f
        val x = middle.x + (Math.cos(angle) * r).toFloat()
        val y = middle.y + (Math.sin(angle) * r).toFloat()
        assertNull(board.hit(Bank.LEFT, field, x, y))
    }

    /**
     * The corridor is a road all the way around: the island's rim stays short
     * of the inner edge, so every live slice can be walked past.
     */
    @Test
    fun a_corridor_separates_the_island_from_the_ring() {
        assertTrue(board.isle(field) < board.inner(field))
    }

    @Test
    fun the_corridor_hits_nothing() {
        val middle = board.center(field)
        val r = (board.inner(field) + board.isle(field)) * 0.5f
        assertNull(board.hit(Bank.LEFT, field, middle.x, middle.y + r))
    }

    /** The island is one pie: every point inside it belongs to a sector. */
    @Test
    fun the_island_is_a_pie_of_its_keys() {
        val middle = board.center(field)
        val r = board.isle(field) * 0.6f
        assertEquals(key('A'), board.hit(Bank.LEFT, field, middle.x - r, middle.y))
        assertEquals(key('O'), board.hit(Bank.LEFT, field, middle.x + r, middle.y))
        assertEquals(key('E'), board.hit(Bank.RIGHT, field, middle.x - r, middle.y))
        assertEquals(key('U'), board.hit(Bank.RIGHT, field, middle.x + r, middle.y))
    }

    /** The floats hang past the outer edge, the first on top, the second below. */
    @Test
    fun the_floats_hang_past_the_outer_edge() {
        val middle = board.center(field)
        val sharp = cell(Bank.LEFT, '#')
        val plus = cell(Bank.LEFT, '+')
        assertTrue(sharp.y < middle.y)
        assertTrue(plus.y > middle.y)
        assertTrue(hypot(sharp.x - middle.x, sharp.y - middle.y) > board.outer(field))
        // The gap between the outer edge and an arc is still road.
        val road = (board.outer(field) +
            board.pitch(field) * (config.boardOuter + config.boardArcGap)) * 0.5f
        assertNull(board.hit(Bank.LEFT, field, middle.x, middle.y - road))
    }

    /** The gap knob is the floats' distance: more gap, farther floats. */
    @Test
    fun the_float_gap_pushes_the_floats_out() {
        val near = Board(ORDER, Config().also { it.boardArcGap = 0.1f })
        val far = Board(ORDER, Config().also { it.boardArcGap = 0.8f })
        val middle = near.center(field)
        fun distance(b: Board): Float {
            val c = b.cells(Bank.LEFT, field).first { it.key == key('#') }
            return hypot(c.x - middle.x, c.y - middle.y)
        }
        assertEquals(0.7f * near.pitch(field), distance(far) - distance(near), 0.5f)
        // Both keep their own reference point.
        for (b in listOf(near, far)) {
            val c = b.cells(Bank.LEFT, field).first { it.key == key('#') }
            assertEquals(key('#'), b.hit(Bank.LEFT, field, c.x, c.y))
        }
    }

    /**
     * At low curvature a float is as good as a straight bar: hit along its
     * whole length, and gone from the circle the full arc used to cover.
     */
    @Test
    fun a_straightened_float_is_a_bar() {
        val config = Config().also { it.boardArcCurve = 0.05f }
        val flat = Board(ORDER, config)
        val middle = flat.center(field)
        val pitch = flat.pitch(field)
        val mid = config.boardOuter + config.boardArcGap + config.boardArcWide * 0.5f
        // The bar keeps the arc's length, laid out tangent at its middle.
        val length = (mid * Math.toRadians(config.boardArcSweep.toDouble())).toFloat()
        val y = middle.y - mid * pitch
        for (dx in listOf(-0.45f, 0f, 0.45f)) {
            assertEquals(
                key('#'),
                flat.hit(Bank.LEFT, field, middle.x + dx * length * pitch, y)
            )
        }
        // Where the wrapped arc used to reach, 30 degrees round the ring, the
        // bar does not: the full-curvature board still answers there.
        val angle = Math.toRadians(-60.0)
        val x = middle.x + (Math.cos(angle) * mid * pitch).toFloat()
        val bent = middle.y + (Math.sin(angle) * mid * pitch).toFloat()
        assertNull(flat.hit(Bank.LEFT, field, x, bent))
        assertEquals(key('#'), board.hit(Bank.LEFT, field, x, bent))
    }

    /** Every cell still holds its reference point at any curvature. */
    @Test
    fun a_bent_cell_holds_its_own_reference_point() {
        for (curve in listOf(0.05f, 0.4f, 1f)) {
            val bent = Board(ORDER, Config().also { it.boardArcCurve = curve })
            for (bank in Bank.values()) {
                for (cell in bent.cells(bank, field)) {
                    assertEquals(
                        "${ORDER[cell.key]} at curvature $curve",
                        cell.key, bent.hit(bank, field, cell.x, cell.y)
                    )
                }
            }
        }
    }

    @Test
    fun the_board_stays_inside_its_field() {
        val middle = board.center(field)
        val outer = board.outer(field)
        assertTrue(middle.x - outer >= field.left)
        assertTrue(middle.x + outer <= field.right)
        assertTrue(middle.y - outer >= field.top)
        assertTrue(middle.y + outer <= field.bottom)
    }
}

/** The board's chording: take-on-entry, walked through the Chorder. */
class BoardChorderTest {

    private val config = Config()
    private val ring = Ring(ORDER, config)
    private val board = Board(ORDER, config)
    private val written = mutableListOf<Long>()
    private val chorder = Chorder(ring, board, config) { written.add(it) }

    init {
        chorder.place(Bank.LEFT, Field(0f, 0f, 400f, 900f))
        chorder.place(Bank.RIGHT, Field(400f, 0f, 800f, 900f))
    }

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    private fun mask(symbols: String) =
        symbols.fold(0L) { bits, c -> bits or (1L shl key(c)) }

    private fun keys(bank: Bank) =
        chorder.thumb(bank).keys.map { ORDER[it] }.joinToString("")

    private fun cell(bank: Bank, symbol: Char): Cell =
        board.cells(bank, chorder.thumb(bank).field).first { it.key == key(symbol) }

    private fun touch(bank: Bank, pointer: Int, symbol: Char) {
        val cell = cell(bank, symbol)
        chorder.down(bank, pointer, cell.x, cell.y)
    }

    /** One sample at the target cell's centre, which is what entering it means. */
    private fun slideTo(bank: Bank, symbol: Char) {
        val thumb = chorder.thumb(bank)
        val cell = cell(bank, symbol)
        chorder.move(thumb.pointer, cell.x, cell.y, UNIT)
    }

    /** `KAO`, walked in take order: the island first, then the ring. */
    @Test
    fun a_walk_takes_the_cells_it_enters() {
        touch(Bank.LEFT, 0, 'A')
        slideTo(Bank.LEFT, 'O')
        slideTo(Bank.LEFT, 'K')
        assertEquals("AOK", keys(Bank.LEFT))
    }

    /**
     * `WAO`, as in `WAOEUL` = while. Ending the island on A puts the walk on
     * the ring's left side, where W is, so the vowels go O then A.
     */
    @Test
    fun the_island_may_be_walked_in_either_order() {
        touch(Bank.LEFT, 0, 'O')
        slideTo(Bank.LEFT, 'A')
        slideTo(Bank.LEFT, 'W')
        assertEquals("OAW", keys(Bank.LEFT))
        assertEquals(mask("WAO"), chorder.mask())
    }

    /** The stroke is a set, so the walked order spells the same outline. */
    @Test
    fun take_order_does_not_change_the_stroke() {
        touch(Bank.LEFT, 0, 'A')
        slideTo(Bank.LEFT, 'O')
        slideTo(Bank.LEFT, 'K')
        touch(Bank.RIGHT, 1, 'E')
        slideTo(Bank.RIGHT, 'p')
        slideTo(Bank.RIGHT, 't')
        assertEquals(mask("KAOEpt"), chorder.up(0))
    }

    /**
     * The skip `SK` over a live T: dip into the corridor, run past T, come out
     * into K. The corridor takes nothing on the way.
     */
    @Test
    fun the_corridor_carries_a_skip_past_a_live_slice() {
        touch(Bank.LEFT, 0, 'S')
        val f = chorder.thumb(Bank.LEFT).field
        val middle = board.center(f)
        // Between the island's rim and the inner edge: the corridor.
        val road = (board.inner(f) + board.isle(f)) * 0.5f
        val t = cell(Bank.LEFT, 'T')
        val angle = Math.atan2((t.y - middle.y).toDouble(), (t.x - middle.x).toDouble())
        chorder.move(
            0,
            middle.x + (Math.cos(angle) * road).toFloat(),
            middle.y + (Math.sin(angle) * road).toFloat(),
            UNIT,
        )
        assertEquals("the corridor took something", "S", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'K')
        assertEquals("SK", keys(Bank.LEFT))
    }

    @Test
    fun a_taken_cell_is_safe_ground() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, 'T')
        slideTo(Bank.LEFT, 'S')
        assertEquals("the walk retook a spent key", "ST", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'K')
        assertEquals("STK", keys(Bank.LEFT))
    }

    /** `TPH-` is a machine row: every other slice, one sweep along a lane. */
    @Test
    fun a_machine_row_is_one_lane() {
        touch(Bank.LEFT, 0, 'T')
        slideTo(Bank.LEFT, 'P')
        slideTo(Bank.LEFT, 'H')
        assertEquals("TPH", keys(Bank.LEFT))
    }

    /** `TK-` is a machine column: one zigzag step across the stagger. */
    @Test
    fun a_machine_column_is_one_step() {
        touch(Bank.LEFT, 0, 'T')
        slideTo(Bank.LEFT, 'K')
        assertEquals("TK", keys(Bank.LEFT))
    }

    /**
     * The stagger's whole point: `K` and `P` are neighbours across it, so the
     * chord the stacked columns made impossible is one short diagonal.
     */
    @Test
    fun no_slice_blocks_the_way_out_of_another() {
        touch(Bank.LEFT, 0, 'K')
        slideTo(Bank.LEFT, 'P')
        assertEquals("KP", keys(Bank.LEFT))
    }

    /** A point on a lane, at this key's angle: over its notch, not in it. */
    private fun laneOver(bank: Bank, symbol: Char) {
        val f = chorder.thumb(bank).field
        val middle = board.center(f)
        val c = cell(bank, symbol)
        val angle = Math.atan2((c.y - middle.y).toDouble(), (c.x - middle.x).toDouble())
        val notch = config.boardStagger * board.pitch(f) * 0.5f
        val inward = hypot(c.x - middle.x, c.y - middle.y) <
            (board.inner(f) + board.outer(f)) * 0.5f
        val r = if (inward) board.outer(f) - notch else board.inner(f) + notch
        chorder.move(
            chorder.thumb(bank).pointer,
            middle.x + (Math.cos(angle) * r).toFloat(),
            middle.y + (Math.sin(angle) * r).toFloat(),
            UNIT,
        )
    }

    /** `SK` along the outer lane: the notch over the live T takes nothing. */
    @Test
    fun a_lane_carries_a_sweep_past_the_other_alternation() {
        touch(Bank.LEFT, 0, 'S')
        laneOver(Bank.LEFT, 'T')
        assertEquals("the notch took something", "S", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'K')
        assertEquals("SK", keys(Bank.LEFT))
    }

    /** No order on the ring: a row sweeps as well backward as forward. */
    @Test
    fun the_ring_may_be_walked_backward() {
        touch(Bank.LEFT, 0, 'H')
        slideTo(Bank.LEFT, 'P')
        slideTo(Bank.LEFT, 'T')
        assertEquals(mask("TPH"), chorder.mask())
    }

    /** The outer road: past the outer edge nothing takes, in any state. */
    @Test
    fun the_outer_road_takes_nothing() {
        touch(Bank.LEFT, 0, 'S')
        val f = chorder.thumb(Bank.LEFT).field
        val middle = board.center(f)
        val k = cell(Bank.LEFT, 'K')
        val angle = Math.atan2((k.y - middle.y).toDouble(), (k.x - middle.x).toDouble())
        val r = board.outer(f) * 1.2f
        chorder.move(
            0,
            middle.x + (Math.cos(angle) * r).toFloat(),
            middle.y + (Math.sin(angle) * r).toFloat(),
            UNIT,
        )
        assertEquals("the road took something", "S", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'K')
        assertEquals("SK", keys(Bank.LEFT))
    }

    /** One sample this many pitches from the board's middle, in field px. */
    private fun slideBy(bank: Bank, dx: Double, dy: Double) {
        val f = chorder.thumb(bank).field
        val middle = board.center(f)
        val pitch = board.pitch(f)
        chorder.move(
            chorder.thumb(bank).pointer,
            middle.x + (dx * pitch).toFloat(),
            middle.y + (dy * pitch).toFloat(),
            UNIT,
        )
    }

    private fun ray(angle: Double, r: Double): Pair<Double, Double> {
        val a = Math.toRadians(angle)
        return Math.cos(a) * r to Math.sin(a) * r
    }

    /**
     * `-BD` as in fibbed: the far key is a dip through the island. Crossing
     * the tombstone throws the focus across the middle and takes nothing, and
     * pushing on walks the focus out into d, at 18 + 180 degrees.
     */
    @Test
    fun the_tombstone_throws_the_focus_across() {
        touch(Bank.RIGHT, 1, 'b')
        val (ix, iy) = ray(18.0, 0.85)
        slideBy(Bank.RIGHT, ix, iy)
        assertEquals("the crossing took something", "b", keys(Bank.RIGHT))
        slideBy(Bank.RIGHT, 0.0, 0.0)
        assertEquals("bd", keys(Bank.RIGHT))
    }

    /** Going back through the island throws the focus home again. */
    @Test
    fun the_tunnel_works_both_ways() {
        touch(Bank.RIGHT, 1, 'b')
        val (ix, iy) = ray(18.0, 0.85)
        slideBy(Bank.RIGHT, ix, iy)
        slideBy(Bank.RIGHT, 0.0, 0.0)
        assertEquals("bd", keys(Bank.RIGHT))
        // The thumb reaches on outward; the focus, 1.7 pitches behind it,
        // re-enters the island and flips back to the near side.
        val (bx, by) = ray(18.0, 2.5)
        slideBy(Bank.RIGHT, bx, by)
        assertEquals("the return crossing took something", "bd", keys(Bank.RIGHT))
        // The offset is now 3.3 pitches: putting the focus in l takes l.
        val (lx, ly) = ray(54.0, 2.0)
        val (ox, oy) = ray(18.0, 3.3)
        slideBy(Bank.RIGHT, lx + ox, ly + oy)
        assertEquals("bdl", keys(Bank.RIGHT))
    }

    /** With the teleport off, the doorways still serve as before. */
    @Test
    fun the_doorways_return_when_the_teleport_is_off() {
        val config = Config().also { it.boardTeleport = false }
        val fallback = Board(ORDER, config)
        val walker = Chorder(Ring(ORDER, config), fallback, config) {}
        val field = Field(0f, 0f, 400f, 900f)
        walker.place(Bank.LEFT, field)
        fun spot(symbol: Char) =
            fallback.cells(Bank.LEFT, field).first { it.key == key(symbol) }
        walker.down(Bank.LEFT, 0, spot('S').x, spot('S').y)
        walker.move(0, spot('A').x, spot('A').y, UNIT)
        assertEquals(
            "SR",
            walker.thumb(Bank.LEFT).keys.map { ORDER[it] }.joinToString("")
        )
    }

    /** Staying inside a cell is one entry, however many samples arrive. */
    @Test
    fun standing_in_a_cell_takes_it_once() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, 'T')
        val t = cell(Bank.LEFT, 'T')
        chorder.move(0, t.x + 2f, t.y - 2f, UNIT)
        chorder.move(0, t.x - 2f, t.y + 2f, UNIT)
        assertEquals("ST", keys(Bank.LEFT))
    }

    /** A key is tappable where it is drawn: a tap on ground starts a slide. */
    @Test
    fun a_tap_on_ground_takes_nothing_but_starts_the_slide() {
        val f = chorder.thumb(Bank.LEFT).field
        val middle = board.center(f)
        val corridor = (board.isle(f) + board.inner(f)) * 0.5f
        chorder.down(Bank.LEFT, 0, middle.x - corridor, middle.y)
        assertEquals("", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'A')
        assertEquals("A", keys(Bank.LEFT))
    }

    /** A tap that lifts on a float alone arms it for the next stroke. */
    @Test
    fun a_tapped_float_joins_the_next_stroke() {
        touch(Bank.LEFT, 0, '#')
        chorder.up(0)
        assertEquals("the tap wrote a stroke", 0, written.size)
        touch(Bank.LEFT, 0, 'T')
        chorder.up(0)
        assertEquals(listOf(mask("#T")), written)
    }

    /** Lifting on an armed float again sends it alone. */
    @Test
    fun a_second_tap_sends_the_float_alone() {
        touch(Bank.LEFT, 0, '#')
        chorder.up(0)
        touch(Bank.LEFT, 0, '#')
        chorder.up(0)
        assertEquals(listOf(mask("#")), written)
    }

    /** What a double tap does: the armed key rides the stroke being formed. */
    @Test
    fun an_armed_key_rides_the_other_thumbs_stroke() {
        touch(Bank.LEFT, 0, 'T')
        chorder.stick(key('*'))
        chorder.up(0)
        assertEquals(listOf(mask("T*")), written)
    }

    /** A held tap goes out when flushed, and a cancel forgets it. */
    @Test
    fun a_held_tap_waits_for_its_window() {
        touch(Bank.LEFT, 0, 'T')
        chorder.up(0, tap = true)
        assertEquals("the held stroke went out early", 0, written.size)
        chorder.flush()
        assertEquals(listOf(mask("T")), written)
        touch(Bank.LEFT, 0, 'P')
        chorder.up(0, tap = true)
        chorder.cancelHeld()
        chorder.flush()
        assertEquals(listOf(mask("T")), written)
    }

    @Test
    fun every_key_can_start_a_stroke() {
        for (bank in Bank.values()) {
            for (k in board.keys(bank)) {
                chorder.reset()
                touch(bank, 0, ORDER[k])
                assertEquals(ORDER[k].toString(), keys(bank))
                chorder.up(0)
            }
        }
    }

    @Test
    fun the_first_lift_writes_the_stroke() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, 'T')
        touch(Bank.RIGHT, 1, '*')
        chorder.up(1)
        assertEquals(listOf(mask("ST*")), written)
    }
}
