package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"

/** Two thumbs walking their combs, and the rule that turns them into strokes. */
class ChorderTest {

    private val config = Config()
    private val comb = Comb(ORDER, config)
    private val written = mutableListOf<Long>()
    private val chorder = Chorder(comb, config) { written.add(it) }

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
        val button = comb.buttons.getValue(bank).first { it.v == symbol }
        return comb.center(bank, field(bank), 0f, button)
    }

    private fun touch(bank: Bank, pointer: Int, symbol: Char) {
        val p = at(bank, symbol)
        chorder.down(bank, pointer, p.x, p.y)
    }

    /** Step to the next key along, which the comb puts one cell away. */
    private fun walk(bank: Bank, symbol: Char) {
        val p = at(bank, symbol)
        chorder.move(chorder.thumb(bank).pointer, p.x, p.y)
    }

    /** `KAOEpt`, written the way two thumbs write it: a tap then steps. */
    private fun keepThe() {
        touch(Bank.LEFT, 0, 'K')
        walk(Bank.LEFT, 'W')
        walk(Bank.LEFT, 'H')
        touch(Bank.RIGHT, 1, 'E')
        walk(Bank.RIGHT, 'U')
        walk(Bank.RIGHT, 'f')
    }

    @Test
    fun a_tap_takes_the_key_it_lands_on() {
        touch(Bank.LEFT, 0, 'T')
        assertEquals("T", keys(Bank.LEFT))
    }

    /** Every key of the walk, one cell at a time, in the order it is written. */
    @Test
    fun a_walk_down_the_chain_takes_every_key_on_it() {
        touch(Bank.LEFT, 0, 'S')
        for (symbol in "TKPWHRAO") walk(Bank.LEFT, symbol)
        assertEquals("STKPWHRAO", keys(Bank.LEFT))
    }

    /** Standing in a cell does not take through it twice. */
    @Test
    fun staying_in_a_cell_takes_it_once() {
        touch(Bank.LEFT, 0, 'S')
        walk(Bank.LEFT, 'T')
        walk(Bank.LEFT, 'T')
        walk(Bank.LEFT, 'T')
        assertEquals("ST", keys(Bank.LEFT))
    }

    /**
     * A cell steno order has passed is a portal, not a key.
     *
     * Stepping back into one can never write it again, and the four sides that
     * are not the walk are exactly the portals, so this is a jump ahead.
     */
    @Test
    fun a_cell_already_passed_is_a_portal() {
        touch(Bank.LEFT, 0, 'T')
        walk(Bank.LEFT, 'K')
        walk(Bank.LEFT, 'T')
        val thumb = chorder.thumb(Bank.LEFT)
        assertEquals("T cannot come again", "TK", keys(Bank.LEFT))
        assertNotEquals(
            "the stride should have thrown the focus off the thumb",
            0f to 0f, thumb.offX to thumb.offY,
        )
        assertNotEquals(
            "the focus should not be sitting in the cell that was stepped into",
            'T', thumb.spot?.v,
        )
    }

    @Test
    fun two_thumbs_build_one_stroke() {
        keepThe()
        assertEquals("KWH", keys(Bank.LEFT))
        assertEquals("EUf", keys(Bank.RIGHT))
        assertTrue("nothing is written until a thumb lifts", written.isEmpty())
    }

    @Test
    fun the_first_lift_writes_the_stroke() {
        keepThe()
        chorder.up(0)
        assertEquals(listOf(mask("KWHEUf")), written)
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
        assertEquals("EUf", keys(Bank.RIGHT))

        touch(Bank.LEFT, 2, 'S')
        chorder.up(2)
        assertEquals(listOf(mask("KWHEUf"), mask("SEUf")), written)
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

    /**
     * A portal parts the focus from the thumb: the thumb goes on from where it
     * is, and the focus goes on from where the jump put it.
     */
    @Test
    fun a_portal_throws_the_focus_ahead() {
        touch(Bank.LEFT, 0, 'S')
        val thumb = chorder.thumb(Bank.LEFT)
        val from = thumb.spot!!
        val side = Side.values().first {
            val step = Comb.step(from, it)
            comb.spotAt(Bank.LEFT, step.first, step.second) != null &&
                comb.portal(Bank.LEFT, from, it, thumb.keys) != null
        }
        val step = Comb.step(from, side)
        val target = comb.spotAt(Bank.LEFT, step.first, step.second)!!
        val p = comb.point(target, comb.place(Bank.LEFT, left, 0f))
        chorder.move(0, p.x, p.y)
        assertNotEquals(
            "the focus should have left the thumb", 0f to 0f, thumb.offX to thumb.offY
        )
        assertNotEquals("the focus should not be in the cell stepped into", target, thumb.spot)
    }

    /** Holding still brings a thrown focus back, and writes nothing. */
    @Test
    fun a_stall_brings_the_focus_back() {
        touch(Bank.LEFT, 0, 'S')
        val thumb = chorder.thumb(Bank.LEFT)
        thumb.offX = 90f
        thumb.offY = 90f
        chorder.stall(Bank.LEFT)
        assertEquals(0f, thumb.offX, 0f)
        assertEquals(0f, thumb.offY, 0f)
        assertEquals("S", keys(Bank.LEFT))
        assertTrue(written.isEmpty())
    }

    /** A lift on the armed keys alone arms them; lifting on them again sends them. */
    @Test
    fun armed_keys_join_the_next_stroke() {
        touch(Bank.LEFT, 0, '#')
        chorder.up(0)
        assertTrue("arming writes nothing", written.isEmpty())
        assertEquals(setOf(key('#')), chorder.sticky)

        touch(Bank.LEFT, 1, 'S')
        chorder.up(1)
        assertEquals(listOf(mask("#S")), written)
        assertTrue("a written stroke clears what was armed", chorder.sticky.isEmpty())
    }

    /**
     * A float is off the walk, so the order cannot pass it: the modifier is
     * still there after the thumb has been to the end of the comb.
     */
    @Test
    fun a_float_is_takeable_after_the_whole_walk() {
        touch(Bank.LEFT, 0, 'S')
        for (symbol in "TKPWHRAO") walk(Bank.LEFT, symbol)
        walk(Bank.LEFT, '+')
        assertEquals("STKPWHRAO+", keys(Bank.LEFT))
    }

    /**
     * A portal aimed at a hole leaves the focus in it, beside the cluster the
     * hole rings. Falling through as well would undo the jump.
     */
    @Test
    fun a_portal_into_a_hole_leaves_the_focus_in_it() {
        val config = Config(combHole = true)
        val comb = Comb(ORDER, config)
        val chorder = Chorder(comb, config) {}
        chorder.place(Bank.LEFT, left)
        val at = comb.place(Bank.LEFT, left, 0f)
        val hole = comb.spots(Bank.LEFT).first { it.v == Comb.HOLE }
        val chain = comb.chain.getValue(Bank.LEFT)
        val cell = { key: Int -> comb.buttons.getValue(Bank.LEFT).first { it.key == key }.spots.first() }
        val (stop, side) = chain.indices.firstNotNullOf { i ->
            Side.values().firstOrNull {
                comb.portal(Bank.LEFT, cell(chain[i]), it, chain.take(i + 1))?.spot == hole
            }?.let { i to it }
        }
        val step = { spot: Spot ->
            val p = comb.point(spot, at)
            chorder.move(0, p.x, p.y)
        }
        comb.point(cell(chain[0]), at).let { chorder.down(Bank.LEFT, 0, it.x, it.y) }
        for (i in 1..stop) step(cell(chain[i]))
        val out = Comb.step(cell(chain[stop]), side)
        step(comb.spotAt(Bank.LEFT, out.first, out.second)!!)
        assertEquals(hole, chorder.thumb(Bank.LEFT).spot)
    }

    /**
     * A stride is not spent by one jump. Sliding on the way the jump went sends
     * it again, which is what puts a key eight of the order ahead inside one
     * slide of the thumb.
     */
    @Test
    fun a_stride_ridden_on_goes_the_same_way_again() {
        val (bank, home, side, land) = rideable()
        val at = comb.place(bank, field(bank), 0f)
        val next = comb.onward(bank, land, listOf(comb.keyOf(home)))!!
        val thumb = chorder.thumb(bank)

        comb.point(home, at).let { chorder.down(bank, 0, it.x, it.y) }
        comb.point(cellOf(bank, Comb.step(home, side)), at).let { chorder.move(0, it.x, it.y) }
        assertEquals("the jump should land where it says", land.spot, thumb.spot)

        val gate = comb.point(
            cellOf(bank, Comb.step(land.spot, comb.toward(land.spot, next.spot))), at
        )
        chorder.move(0, gate.x - thumb.offX, gate.y - thumb.offY)
        assertEquals("riding on should jump again", next.spot, thumb.spot)
        assertNotEquals("the ride should reach further", land.aim, next.aim)
    }

    /** Leaving a landing any other way puts the focus back on foot. */
    @Test
    fun a_landing_left_any_other_way_stops_riding() {
        val (bank, home, side, land) = rideable()
        val at = comb.place(bank, field(bank), 0f)
        val next = comb.onward(bank, land, listOf(comb.keyOf(home)))!!
        val ride = comb.toward(land.spot, next.spot)
        val other = Side.values().first {
            it != ride && comb.spotAt(
                bank, Comb.step(land.spot, it).first, Comb.step(land.spot, it).second
            ) != null
        }
        val thumb = chorder.thumb(bank)
        comb.point(home, at).let { chorder.down(bank, 0, it.x, it.y) }
        comb.point(cellOf(bank, Comb.step(home, side)), at).let { chorder.move(0, it.x, it.y) }
        val away = comb.point(cellOf(bank, Comb.step(land.spot, other)), at)
        chorder.move(0, away.x - thumb.offX, away.y - thumb.offY)
        assertNotEquals("only the way the jump went rides on", next.spot, thumb.spot)
    }

    private fun cellOf(bank: Bank, p: Pair<Int, Int>) = comb.spotAt(bank, p.first, p.second)!!

    private data class Ride(val bank: Bank, val home: Spot, val side: Side, val land: Landing)

    /** The first key of either comb with a jump that can be ridden on. */
    private fun rideable(): Ride = Bank.values().firstNotNullOf { bank ->
        comb.chain.getValue(bank).firstNotNullOfOrNull { key ->
            val home = comb.buttons.getValue(bank).first { it.key == key }.spots.first()
            Side.values().firstNotNullOfOrNull { side ->
                comb.portal(bank, home, side, listOf(key))
                    ?.takeIf { comb.onward(bank, it, listOf(key)) != null }
                    ?.let { Ride(bank, home, side, it) }
            }
        }
    }

    /** A stroke goes out once, however many thumbs were holding keys. */
    @Test
    fun a_lift_with_nothing_taken_writes_nothing() {
        touch(Bank.LEFT, 0, 'S')
        chorder.up(0)
        chorder.up(0)
        assertEquals(1, written.size)
    }
}
