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

    /**
     * A cell steno order has passed is ground, which is the turn back a chord
     * makes to reach a key the ring keeps on the far side.
     */
    @Test
    fun a_cell_the_order_passed_is_ground() {
        touch(Bank.LEFT, 0, 'W')
        walk(Bank.LEFT, 'K')
        assertEquals("K cannot come again", "W", keys(Bank.LEFT))
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
     * A key armed under a thumb that is still down is held, not armed: `*`
     * stays over a run of left-hand taps, and goes when the right thumb lifts.
     */
    @Test
    fun a_key_armed_under_a_thumb_still_down_outlives_the_stroke() {
        val corner = spot(Bank.RIGHT, 1.2f, 45f)
        chorder.down(Bank.RIGHT, 1, corner.x, corner.y)
        chorder.stick(key('*'))

        touch(Bank.LEFT, 0, 'P')
        chorder.up(0)
        touch(Bank.LEFT, 2, 'H')
        chorder.up(2)
        assertEquals(listOf(mask("*P"), mask("*H")), written)

        chorder.up(1)
        assertEquals(2, written.size)
        assertEquals("", keys(Bank.RIGHT))
        assertTrue("the lift takes the key with it", chorder.sticky.isEmpty())
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
     * A cell holding a key already in hand flips its ring's key rather than
     * taking anything: the thumb has been through it, so a chord never enters
     * it again.
     */
    @Test
    fun entering_a_key_in_hand_flips_its_ring() {
        touch(Bank.LEFT, 0, 'K')
        walk(Bank.LEFT, 'P')
        walk(Bank.LEFT, 'K')
        assertEquals("ring 1 flips the bank's #", "KP#", keys(Bank.LEFT))
        walk(Bank.LEFT, 'P')
        assertEquals("a second entry flips it back", "KP", keys(Bank.LEFT))
    }

    /** Ring 2 flips its own key, which is the turn back that used to be a gesture. */
    @Test
    fun turning_back_onto_a_vowel_in_hand_flips_ring_two() {
        touch(Bank.LEFT, 0, 'P')
        walk(Bank.LEFT, 'A')
        walk(Bank.LEFT, 'O')
        walk(Bank.LEFT, 'A')
        assertEquals("PAO^", keys(Bank.LEFT))
    }

    /** Going out and taking a vowel is not a flip, whatever it crosses. */
    @Test
    fun a_walk_out_to_the_vowels_takes_no_modifier() {
        touch(Bank.LEFT, 0, 'P')
        walk(Bank.LEFT, 'A')
        walk(Bank.LEFT, 'O')
        assertEquals("PAO", keys(Bank.LEFT))
    }

    /** A tap that lands on ground takes the key it was aimed at. */
    @Test
    fun a_tap_short_of_its_key_takes_it_anyway() {
        val at = ring.centre(Bank.LEFT, ring.cellsOf(Bank.LEFT, key('P')).first())
        // Short of the key, in the ground between ring 1 and the hub.
        val p = spot(Bank.LEFT, at.r - 0.13f, at.deg)
        chorder.down(Bank.LEFT, 0, p.x, p.y)
        assertEquals("P", keys(Bank.LEFT))
    }

    /** Outside the rings there is nothing to have been aimed at. */
    @Test
    fun a_tap_outside_the_rings_takes_nothing() {
        val p = spot(Bank.LEFT, 1.2f, 45f)
        chorder.down(Bank.LEFT, 0, p.x, p.y)
        assertEquals("", keys(Bank.LEFT))
    }

    /**
     * One hand writes a whole stroke by turning the board round: the crossing
     * writes nothing, the keys stay in hand, and the next touch in the same
     * half of the glass walks the other bank.
     */
    @Test
    fun crossing_the_middle_swaps_the_two_halves() {
        touch(Bank.RIGHT, 0, 'p')
        val over = at(Bank.LEFT, 'K')
        chorder.move(0, over.x, over.y)
        chorder.swap(0)
        assertTrue("a crossing is not a stroke", written.isEmpty())
        assertEquals("p", keys(Bank.RIGHT))

        // The right half of the glass is the left bank now.
        val there = ring.middle(Bank.LEFT, chorder.thumb(Bank.LEFT).field, 
            ring.cellsOf(Bank.LEFT, key('K')).first())
        chorder.down(Bank.LEFT, 1, there.x, there.y)
        assertTrue("the half a thumb starts in is the right one", there.x > 400f)
        chorder.up(1)
        assertEquals(listOf(mask("Kp")), written)
        assertTrue("the swap lasts one stroke", !chorder.swapped)
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
