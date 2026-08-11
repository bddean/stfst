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
 * and dead island cells as doorways.
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
        assertEquals("AO#^+STKPWHR", takes(Bank.LEFT))
        assertEquals("@*EUfrpblgtsdz", takes(Bank.RIGHT))
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
        assertFalse(board.live(key('O'), taken("#")))
        assertFalse(board.live(key('*'), taken("Ef")))
        // And the ring itself is still a rising run.
        assertTrue(board.live(key('K'), taken("AOS")))
        assertFalse(board.live(key('S'), taken("T")))
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

    @Test
    fun a_dead_island_cell_yields_its_doorway_key() {
        // After S the left island is closed, and A's doorway leads to R.
        assertEquals(key('R'), board.resolve(key('A'), taken("S")))
        assertEquals(key('H'), board.resolve(key('O'), taken("S")))
        // The right island, once the walk is on the ring.
        assertEquals(key('d'), board.resolve(key('E'), taken("Ef")))
        assertEquals(key('g'), board.resolve(key('U'), taken("f")))
    }

    @Test
    fun a_doorway_shuts_when_its_key_is_spent() {
        assertNull(board.resolve(key('A'), taken("R")))
        assertNull(board.resolve(key('O'), taken("H")))
    }

    @Test
    fun a_dead_cell_with_no_doorway_is_plain_ground() {
        assertNull(board.resolve(key('+'), taken("S")))
        assertNull(board.resolve(key('T'), taken("K")))
    }

    /** A doorway across the banks would hand one thumb the other's key. */
    @Test
    fun a_doorway_cannot_cross_the_banks() {
        val crossed = Board(ORDER, Config().also { it.boardPortals = "A>t" })
        assertNull(crossed.resolve(ORDER.indexOf('A'), listOf(ORDER.indexOf('S'))))
    }

    // The geometry: contiguous slices around a corridor around the island.

    /** The slices leave no gap: past the inner edge every direction is a key. */
    @Test
    fun the_slices_cover_every_direction() {
        val middle = board.center(field)
        for (bank in Bank.values()) {
            for (tenth in 0 until 3600) {
                val angle = Math.toRadians(tenth / 10.0)
                val r = board.inner(field) * 1.2f
                val got = board.hit(
                    bank, field,
                    middle.x + (Math.cos(angle) * r).toFloat(),
                    middle.y + (Math.sin(angle) * r).toFloat(),
                )
                assertTrue("nothing at ${tenth / 10f}", got != null && !board.island(got))
            }
        }
    }

    @Test
    fun a_slice_is_centred_on_its_reference_point() {
        for (bank in Bank.values()) {
            for (cell in board.cells(bank, field).filter { !board.island(it.key) }) {
                assertEquals(cell.key, board.hit(bank, field, cell.x, cell.y))
            }
        }
    }

    /** Outward the ring has no edge: only undershooting misses a slice. */
    @Test
    fun past_the_outer_edge_is_still_the_slice() {
        val middle = board.center(field)
        val k = cell(Bank.LEFT, 'K')
        val far = 4f
        assertEquals(
            key('K'),
            board.hit(
                Bank.LEFT, field,
                middle.x + (k.x - middle.x) * far, middle.y + (k.y - middle.y) * far,
            )
        )
    }

    /**
     * The corridor is the board's whole guard: it circles the island, so every
     * live slice can be walked around, and the island discs never touch.
     */
    @Test
    fun a_corridor_separates_the_island_from_the_ring() {
        for (bank in Bank.values()) {
            val middle = board.center(field)
            val radius = board.radius(field)
            val discs = board.cells(bank, field).filter { board.island(it.key) }
            for (disc in discs) {
                val reach = hypot(disc.x - middle.x, disc.y - middle.y) + radius
                assertTrue("${ORDER[disc.key]} crowds the ring", reach < board.inner(field))
            }
            for (i in discs.indices) {
                for (j in i + 1 until discs.size) {
                    val gap = hypot(discs[i].x - discs[j].x, discs[i].y - discs[j].y)
                    assertTrue(
                        "${ORDER[discs[i].key]} and ${ORDER[discs[j].key]} touch",
                        gap > 2f * radius
                    )
                }
            }
        }
    }

    @Test
    fun the_corridor_hits_nothing() {
        val middle = board.center(field)
        // Straight down from the middle: the left island lies east-west, so this
        // radius is corridor from the discs' edge to the slices' inner edge.
        val r = (board.inner(field) + board.radius(field)) * 0.5f
        assertNull(board.hit(Bank.LEFT, field, middle.x, middle.y + r))
    }

    /** A touch-down reads the nearest cell instead, so no tap lands on nothing. */
    @Test
    fun a_touch_down_misses_nothing() {
        for (x in 0 until field.width.toInt() step 13) {
            for (y in 0 until field.height.toInt() step 17) {
                val got = board.nearest(Bank.LEFT, field, x.toFloat(), y.toFloat())
                assertTrue("nothing at $x $y", got != null)
                assertEquals(Bank.LEFT, board.bankOf(got!!))
            }
        }
    }

    /** An undershot tap short of a slice still reads as that slice. */
    @Test
    fun a_tap_just_short_of_a_slice_reads_as_the_slice() {
        val middle = board.center(field)
        val k = cell(Bank.LEFT, 'K')
        val angle = Math.atan2((k.y - middle.y).toDouble(), (k.x - middle.x).toDouble())
        val r = board.inner(field) * 0.9f
        assertEquals(
            key('K'),
            board.nearest(
                Bank.LEFT, field,
                middle.x + (Math.cos(angle) * r).toFloat(),
                middle.y + (Math.sin(angle) * r).toFloat(),
            )
        )
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
        val road = (board.inner(f) + board.radius(f)) * 0.5f
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
    fun a_dead_cell_is_safe_ground() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, '+')
        assertEquals("the walk crossed a spent key", "S", keys(Bank.LEFT))
        slideTo(Bank.LEFT, 'T')
        assertEquals("ST", keys(Bank.LEFT))
    }

    /** The measured win of the vowels-first order: doorways mid-walk. */
    @Test
    fun a_dead_island_cell_takes_through_its_doorway() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, 'A')
        assertEquals("SR", keys(Bank.LEFT))
    }

    @Test
    fun a_doorway_shuts_when_its_key_is_spent() {
        touch(Bank.LEFT, 0, 'R')
        slideTo(Bank.LEFT, 'A')
        assertEquals("R", keys(Bank.LEFT))
    }

    /** Leaving and coming back is a new entry, and the cell answers afresh. */
    @Test
    fun reentering_a_cell_asks_again() {
        touch(Bank.RIGHT, 1, 'E')
        slideTo(Bank.RIGHT, 'f')
        slideTo(Bank.RIGHT, 'E')
        assertEquals("Efd", keys(Bank.RIGHT))
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

    /** A tap is read as the nearest cell, so the corridor swallows no touch-down. */
    @Test
    fun a_tap_in_the_corridor_takes_the_nearest_cell() {
        val a = cell(Bank.LEFT, 'A')
        val radius = board.radius(chorder.thumb(Bank.LEFT).field)
        chorder.down(Bank.LEFT, 0, a.x - radius * 1.3f, a.y)
        assertEquals("A", keys(Bank.LEFT))
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
