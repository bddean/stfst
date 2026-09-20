package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The order the app ships, `🦐` and all: a key outside the basic plane is
// what tells a key number from a string index.
private const val ORDER = "🦐#@^+STKPWHRAO*EUfrpblgtsdz"
private val KEYS = Keys.of(ORDER)

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

    private fun key(symbol: Char) = KEYS.indexOf(symbol)

    private fun mask(symbols: String) =
        symbols.fold(0L) { bits, c -> bits or (1L shl key(c)) }

    private fun keys(bank: Bank) =
        chorder.thumb(bank).keys.joinToString("") { KEYS[it] }

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

    /** The studs the upgrade stands in, between each pair of the keys it lifts. */
    private fun studs(bank: Bank): List<Point> =
        ring.cells(bank).filter { ring.isUpgrade(bank, it) }
            .map { ring.middle(bank, field(bank), it) }

    private fun step(bank: Bank, to: Point) {
        chorder.move(chorder.thumb(bank).pointer, to.x, to.y)
    }

    /**
     * The upgrade is one bit over the whole stroke: while it is on, `-T`
     * writes `-D`, and writing the stroke ends it.
     */
    @Test
    fun the_upgrade_writes_the_lifted_key() {
        val stud = studs(Bank.RIGHT).first()
        chorder.down(Bank.RIGHT, 0, stud.x, stud.y)
        assertTrue("the toggle is on", chorder.upgrade)
        walk(Bank.RIGHT, 't')
        chorder.up(0)
        assertEquals(listOf(mask("d")), written)
        assertTrue("a written stroke ends the upgrade", !chorder.upgrade)
    }

    /** `-T` and `-S` under the upgrade are `-D` and `-Z`, which is the point of it. */
    @Test
    fun the_upgrade_lifts_the_whole_stroke() {
        val stud = studs(Bank.RIGHT).first()
        chorder.down(Bank.RIGHT, 0, stud.x, stud.y)
        walk(Bank.RIGHT, 't')
        walk(Bank.RIGHT, 's')
        chorder.up(0)
        assertEquals(listOf(mask("dz")), written)
    }

    /**
     * A second entry turns it off again, so a stroke can hold one lifted key
     * and one plain one: `-DS` is the toggle, `-T`, the toggle, `-S`.
     */
    @Test
    fun the_upgrade_turns_off_again() {
        val stud = studs(Bank.RIGHT).first()
        chorder.down(Bank.RIGHT, 0, stud.x, stud.y)
        walk(Bank.RIGHT, 't')
        step(Bank.RIGHT, stud)
        assertTrue("the toggle is off", !chorder.upgrade)
        walk(Bank.RIGHT, 's')
        chorder.up(0)
        assertEquals(listOf(mask("ds")), written)
    }

    /** A tap on the toggle alone has nothing to lift, so it writes both keys. */
    @Test
    fun a_tap_on_the_toggle_writes_both_keys() {
        val stud = studs(Bank.RIGHT).first()
        chorder.down(Bank.RIGHT, 0, stud.x, stud.y)
        chorder.up(0)
        assertEquals(listOf(mask("dz")), written)
    }

    /** A place on the rings, in the field's own pixels. */
    private fun spot(bank: Bank, r: Float, deg: Float): Point =
        ring.point(bank, field(bank), Polar(r, deg))

    /**
     * A turn back past a whole cell of the outer ring takes the bank's
     * modifier, and takes no key to get there.
     *
     * The ground inside the ring is where the turn is made: the keys stand in
     * the middle of the band, so a thumb short of them crosses their slots
     * without entering them.
     */
    @Test
    fun a_turn_back_round_the_outer_ring_takes_the_modifier() {
        val bank = Bank.LEFT
        val span = 360f / ring.slots(bank, ring.outer(bank))
        // Short of the keys, where the ring is ground all the way round.
        val inner = 0.70f
        val from = 3f * span
        chorder.down(bank, 0, spot(bank, inner, from).x, spot(bank, inner, from).y)
        assertEquals("", keys(bank))
        step(bank, spot(bank, inner, from - span))
        assertEquals("one slot back is how a stud is reached", "", keys(bank))
        step(bank, spot(bank, inner, from - 2f * span))
        assertEquals("^", keys(bank))
    }

    /** A pull back in off the outer ring takes the other one. */
    @Test
    fun a_pull_in_off_the_outer_ring_takes_the_modifier() {
        val p = spot(Bank.LEFT, 0.70f, 36f)
        chorder.down(Bank.LEFT, 0, p.x, p.y)
        assertEquals("", keys(Bank.LEFT))
        step(Bank.LEFT, spot(Bank.LEFT, 0.50f, 36f))
        assertEquals("#", keys(Bank.LEFT))
    }

    /** Going out and taking a vowel is not a gesture, whatever it crosses. */
    @Test
    fun a_walk_out_to_the_vowels_takes_no_modifier() {
        touch(Bank.LEFT, 0, 'P')
        walk(Bank.LEFT, 'A')
        walk(Bank.LEFT, 'O')
        assertEquals("PAO", keys(Bank.LEFT))
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
