package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"

/** Two thumbs walking their rings, and the rule that turns them into strokes. */
class ChorderTest {

    private val config = Config()
    private val ring = Ring(ORDER, config)
    private val written = mutableListOf<Long>()
    private val chorder = Chorder(ring, config) { written.add(it) }

    private val left = Field(0f, 0f, 400f, 1200f)
    private val right = Field(400f, 0f, 800f, 1200f)

    init {
        chorder.place(Bank.LEFT, left)
        chorder.place(Bank.RIGHT, right)
    }

    private fun field(bank: Bank) = if (bank == Bank.LEFT) left else right

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    private fun mask(symbols: String) =
        symbols.fold(0L) { bits, c -> bits or (1L shl key(c)) }

    private fun keys(bank: Bank) =
        chorder.thumb(bank).keys.joinToString("") { ORDER[it].toString() }

    /** Where a key is drawn, which is the point a thumb aims at. */
    private fun at(bank: Bank, symbol: Char): Point {
        val cell = ring.cellsOf(bank, key(symbol)).first()
        return ring.middle(bank, field(bank), cell)
    }

    private fun touch(bank: Bank, pointer: Int, symbol: Char) {
        val p = at(bank, symbol)
        chorder.down(bank, pointer, p.x, p.y)
    }

    /** Step into the cell a key is drawn in, which is what takes it. */
    private fun walk(bank: Bank, symbol: Char) {
        val p = at(bank, symbol)
        chorder.move(chorder.thumb(bank).pointer, p.x, p.y)
    }

    /** `KAOEpt`, written the way two thumbs write it: a tap then steps. */
    private fun keepThe() {
        touch(Bank.LEFT, 0, 'K')
        walk(Bank.LEFT, 'A')
        touch(Bank.RIGHT, 1, 'E')
        walk(Bank.RIGHT, 'p')
        walk(Bank.RIGHT, 't')
    }

    @Test
    fun a_tap_takes_the_key_it_lands_on() {
        touch(Bank.LEFT, 0, 'P')
        assertEquals("P", keys(Bank.LEFT))
    }

    /** The hub is a pie of complete answers: landing takes the whole slice. */
    @Test
    fun landing_on_the_hub_takes_the_whole_slice() {
        touch(Bank.LEFT, 0, 'T')
        assertEquals("ST", keys(Bank.LEFT))
    }

    /**
     * The hub is ground once you have left it, so the middle is a shortcut and
     * not a key: two cells too far apart on a ring are joined through it.
     */
    @Test
    fun crossing_the_hub_takes_nothing() {
        touch(Bank.LEFT, 0, 'K')
        val middle = ring.point(Bank.LEFT, left, Polar(0f, 0f))
        chorder.move(0, middle.x, middle.y)
        walk(Bank.LEFT, 'R')
        assertEquals("KR", keys(Bank.LEFT))
    }

    /** Entering a live cell takes it, one ring at a time, in steno order. */
    @Test
    fun a_walk_out_through_the_rings_takes_every_key_on_the_way() {
        touch(Bank.LEFT, 0, 'P')
        for (symbol in "AO+") walk(Bank.LEFT, symbol)
        assertEquals("PAO+", keys(Bank.LEFT))
    }

    /** Standing in a cell does not take through it twice. */
    @Test
    fun staying_in_a_cell_takes_it_once() {
        touch(Bank.LEFT, 0, 'P')
        walk(Bank.LEFT, 'A')
        walk(Bank.LEFT, 'A')
        assertEquals("PA", keys(Bank.LEFT))
    }

    /** A cell steno order has passed is ground: crossing it back takes nothing. */
    @Test
    fun a_cell_already_passed_is_ground() {
        touch(Bank.LEFT, 0, 'K')
        walk(Bank.LEFT, 'P')
        walk(Bank.LEFT, 'K')
        assertEquals("K cannot come again", "KP", keys(Bank.LEFT))
    }

    @Test
    fun two_thumbs_build_one_stroke() {
        keepThe()
        assertEquals("KA", keys(Bank.LEFT))
        assertEquals("Ept", keys(Bank.RIGHT))
        assertTrue("nothing is written until a thumb lifts", written.isEmpty())
    }

    @Test
    fun the_first_lift_writes_the_stroke() {
        keepThe()
        chorder.up(0)
        assertEquals(listOf(mask("KAEpt")), written)
    }

    /**
     * The reason the first lift writes: the thumb still down keeps its keys, so
     * a run of words sharing a right-hand shape costs one right-hand motion.
     */
    @Test
    fun a_thumb_that_stays_down_keeps_its_keys() {
        keepThe()
        chorder.up(0)
        assertEquals("", keys(Bank.LEFT))
        assertEquals("Ept", keys(Bank.RIGHT))

        touch(Bank.LEFT, 2, 'P')
        chorder.up(2)
        assertEquals(listOf(mask("KAEpt"), mask("PEpt")), written)
    }

    /**
     * The bug that made `Rr` impossible: the second thumb still holds its keys
     * when it lifts, and holding keys is not asking for another stroke.
     */
    @Test
    fun lifting_the_second_thumb_writes_nothing() {
        keepThe()
        chorder.up(0)
        chorder.up(1)
        assertEquals(1, written.size)
        assertEquals("", keys(Bank.RIGHT))
    }

    /** A lift on the armed keys alone arms them; lifting on them again sends them. */
    @Test
    fun armed_keys_join_the_next_stroke() {
        touch(Bank.LEFT, 0, '#')
        chorder.up(0)
        assertTrue("arming writes nothing", written.isEmpty())
        assertEquals(setOf(key('#')), chorder.sticky)

        touch(Bank.LEFT, 1, 'P')
        chorder.up(1)
        assertEquals(listOf(mask("#P")), written)
        assertTrue("a written stroke clears what was armed", chorder.sticky.isEmpty())
    }

    /**
     * A float is off the order, so the order cannot pass it: the modifier is
     * still there after the thumb has been all the way out.
     */
    @Test
    fun a_float_is_takeable_after_the_whole_walk() {
        touch(Bank.LEFT, 0, 'P')
        for (symbol in "AO") walk(Bank.LEFT, symbol)
        walk(Bank.LEFT, '#')
        assertEquals("PAO#", keys(Bank.LEFT))
    }

    /** Where a row key is drawn, the upgrade included, which holds no key. */
    private fun rowAt(bank: Bank, slot: Int): Point =
        ring.middle(bank, field(bank), Cell(Cell.ROW, slot))

    /**
     * The upgrade is one bit over the whole stroke: while it is on, `-T`
     * writes `-D`, and writing the stroke ends it.
     */
    @Test
    fun the_upgrade_writes_the_lifted_key() {
        val up = rowAt(Bank.RIGHT, 2)
        chorder.down(Bank.RIGHT, 0, up.x, up.y)
        chorder.up(0)
        assertTrue("the upgrade is armed", chorder.upgrade)
        assertTrue("arming writes nothing", written.isEmpty())

        touch(Bank.RIGHT, 1, 't')
        chorder.up(1)
        assertEquals(listOf(mask("d")), written)
        assertTrue("a written stroke ends the upgrade", !chorder.upgrade)
    }

    /** A second entry turns it off again, so it is a switch and not a latch. */
    @Test
    fun the_upgrade_turns_off_again() {
        val up = rowAt(Bank.RIGHT, 2)
        chorder.down(Bank.RIGHT, 0, up.x, up.y)
        chorder.up(0)
        chorder.down(Bank.RIGHT, 0, up.x, up.y)
        chorder.up(0)
        assertTrue("the upgrade is off", !chorder.upgrade)
    }

    /** A stroke goes out once, however many thumbs were holding keys. */
    @Test
    fun a_lift_with_nothing_taken_writes_nothing() {
        touch(Bank.LEFT, 0, 'P')
        chorder.up(0)
        chorder.up(0)
        assertEquals(1, written.size)
    }
}
