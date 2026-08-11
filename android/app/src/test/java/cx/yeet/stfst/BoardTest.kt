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

    /**
     * Take order, not steno order: the left vowels come first, so the island is
     * dead for the whole consonant walk and its cells can be doorways.
     */
    @Test
    fun the_left_vowels_come_first() {
        assertTrue(board.live(key('S'), key('O')))
        assertFalse(board.live(key('A'), key('S')))
        assertTrue(board.live(key('#'), key('A')))
        assertFalse(board.live(key('A'), key('O')))
    }

    @Test
    fun everything_is_live_at_the_start() {
        for (bank in Bank.values()) {
            for (k in board.keys(bank)) {
                assertTrue("${ORDER[k]} is dead at the start", board.live(k, -1))
            }
        }
    }

    @Test
    fun a_live_cell_yields_itself() {
        assertEquals(key('K'), board.resolve(key('K'), key('T')))
        assertEquals(key('A'), board.resolve(key('A'), -1))
    }

    @Test
    fun a_dead_island_cell_yields_its_doorway_key() {
        // After S the left island is dead, and A's doorway leads to R.
        assertEquals(key('R'), board.resolve(key('A'), key('S')))
        assertEquals(key('H'), board.resolve(key('O'), key('S')))
        // The right island, once the walk is on the ring.
        assertEquals(key('d'), board.resolve(key('E'), key('f')))
        assertEquals(key('g'), board.resolve(key('U'), key('f')))
    }

    @Test
    fun a_doorway_shuts_when_its_key_is_spent() {
        assertNull(board.resolve(key('A'), key('R')))
        assertNull(board.resolve(key('O'), key('H')))
    }

    @Test
    fun a_dead_cell_with_no_doorway_is_plain_ground() {
        assertNull(board.resolve(key('+'), key('S')))
        assertNull(board.resolve(key('T'), key('K')))
    }

    /** A doorway across the banks would hand one thumb the other's key. */
    @Test
    fun a_doorway_cannot_cross_the_banks() {
        val crossed = Board(ORDER, Config().also { it.boardPortals = "A>t" })
        assertNull(crossed.resolve(ORDER.indexOf('A'), ORDER.indexOf('S')))
    }

    // The geometry: a ring of discs around the island, ground between them.

    @Test
    fun neighbouring_ring_cells_sit_one_pitch_apart() {
        val pitch = board.pitch(field)
        val ring = config.boardRingLeft.map { cell(Bank.LEFT, it) }
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            assertEquals("${ORDER[a.key]} to ${ORDER[b.key]}", pitch, hypot(a.x - b.x, a.y - b.y), pitch * 0.01f)
        }
    }

    /**
     * The ground is the board's whole guard: no cell touches another, so every
     * key can be walked around.
     */
    @Test
    fun ground_separates_every_pair_of_cells() {
        for (bank in Bank.values()) {
            val cells = board.cells(bank, field)
            val radius = board.radius(field)
            for (i in cells.indices) {
                for (j in i + 1 until cells.size) {
                    val gap = hypot(cells[i].x - cells[j].x, cells[i].y - cells[j].y)
                    assertTrue(
                        "${ORDER[cells[i].key]} and ${ORDER[cells[j].key]} touch",
                        gap > 2f * radius
                    )
                }
            }
        }
    }

    @Test
    fun a_cell_centre_hits_its_own_cell() {
        for (bank in Bank.values()) {
            for (cell in board.cells(bank, field)) {
                assertEquals(cell.key, board.hit(bank, field, cell.x, cell.y))
            }
        }
    }

    @Test
    fun the_ground_between_two_cells_hits_nothing() {
        val s = cell(Bank.LEFT, 'S')
        val t = cell(Bank.LEFT, 'T')
        assertNull(board.hit(Bank.LEFT, field, (s.x + t.x) * 0.5f, (s.y + t.y) * 0.5f))
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

    @Test
    fun the_board_stays_inside_its_field() {
        for (bank in Bank.values()) {
            val radius = board.radius(field)
            for (cell in board.cells(bank, field)) {
                assertTrue("${ORDER[cell.key]} leaks left", cell.x - radius >= field.left)
                assertTrue("${ORDER[cell.key]} leaks right", cell.x + radius <= field.right)
                assertTrue("${ORDER[cell.key]} leaks up", cell.y - radius >= field.top)
                assertTrue("${ORDER[cell.key]} leaks down", cell.y + radius <= field.bottom)
            }
        }
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

    @Test
    fun the_ground_between_cells_takes_nothing() {
        touch(Bank.LEFT, 0, 'S')
        val s = cell(Bank.LEFT, 'S')
        val t = cell(Bank.LEFT, 'T')
        chorder.move(0, (s.x + t.x) * 0.5f, (s.y + t.y) * 0.5f, UNIT)
        assertEquals("S", keys(Bank.LEFT))
        // And crossing it into a live cell takes that cell.
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

    /** A tap is read as the nearest cell, so the ground swallows no touch-down. */
    @Test
    fun a_tap_on_ground_takes_the_nearest_cell() {
        val k = cell(Bank.LEFT, 'K')
        val p = cell(Bank.LEFT, 'P')
        // Just outside K's disc, on the way to P, still nearer K.
        val radius = board.radius(chorder.thumb(Bank.LEFT).field)
        val x = k.x + (p.x - k.x) / hypot(p.x - k.x, p.y - k.y) * radius * 1.1f
        val y = k.y + (p.y - k.y) / hypot(p.x - k.x, p.y - k.y) * radius * 1.1f
        chorder.down(Bank.LEFT, 0, x, y)
        assertEquals("K", keys(Bank.LEFT))
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
