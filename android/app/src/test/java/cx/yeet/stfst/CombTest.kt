package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout format and the geometry it fixes.
 *
 * Everything here is about what a grid of characters determines, so none of it
 * needs a device and none of it needs the engine.
 */
class CombTest {

    private val order = "#@^+STKPWHRAO*EUfrpblgtsdz"
    private val field = Field(0f, 0f, 400f, 1200f)

    private fun comb(config: Config = Config()) = Comb(order, config)

    private fun spot(comb: Comb, bank: Bank, v: Char): Spot =
        comb.buttons.getValue(bank).first { it.v == v }.spots.first()

    /**
     * The claim the whole layout rests on: a key touches the next two in the
     * steno order, so the common step is one hop to a neighbouring cell.
     */
    @Test
    fun a_key_touches_the_next_two_in_the_order() {
        val comb = comb()
        for (bank in Bank.values()) {
            val chain = comb.chain.getValue(bank)
            for (i in 0 until chain.size - 2) {
                val here = spot(comb, bank, order[chain[i]])
                for (ahead in 1..2) {
                    val next = spot(comb, bank, order[chain[i + ahead]])
                    val touching = Side.values().any {
                        Comb.step(here, it) == (next.col to next.row)
                    }
                    assertTrue(
                        "${order[chain[i]]} does not touch ${order[chain[i + ahead]]} in $bank",
                        touching,
                    )
                }
            }
        }
    }

    /** Every key of the order lands in exactly one bank. */
    @Test
    fun the_two_combs_hold_the_whole_order() {
        val comb = comb()
        val placed = Bank.values().flatMap { comb.chain.getValue(it) }
        assertEquals(order.length, placed.size)
        assertEquals(order.length, placed.distinct().size)
    }

    /** Steno order is enforced: a key already passed can never come again. */
    @Test
    fun the_order_only_goes_up() {
        val comb = comb()
        val chain = comb.chain.getValue(Bank.LEFT)
        val middle = chain[5]
        assertTrue(comb.live(chain[6], listOf(middle)))
        assertTrue(!comb.live(chain[4], listOf(middle)))
        assertTrue(!comb.live(middle, listOf(middle)))
    }

    /** A run of touching cells with one value is one button, and no more. */
    @Test
    fun touching_cells_of_one_value_meld() {
        val comb = comb(Config(combLeft = "SS./.T./...", combRight = "E"))
        val buttons = comb.buttons.getValue(Bank.LEFT)
        assertEquals(2, buttons.size)
        assertEquals(2, buttons.first { it.v == 'S' }.spots.size)
        assertEquals(1, buttons.first { it.v == 'T' }.spots.size)
    }

    /** Cells that do not touch are separate buttons answering one key. */
    @Test
    fun cells_apart_are_two_buttons_for_one_key() {
        val comb = comb(Config(combLeft = "S.S", combRight = "E"))
        val buttons = comb.buttons.getValue(Bank.LEFT).filter { it.v == 'S' }
        assertEquals(2, buttons.size)
        assertEquals(1, comb.chain.getValue(Bank.LEFT).size)
    }

    /**
     * A tap lands inside the cell it is answered by.
     *
     * The drawing asks [Comb.region] and the touch asks [Comb.hit], so this is
     * the claim that keeps the picture from disagreeing with the decision.
     */
    @Test
    fun a_tap_lands_inside_the_cell_it_is_answered_by() {
        for (tilt in listOf(0f, 12f, -30f)) {
            val comb = comb()
            for (bank in Bank.values()) {
                for (spot in comb.spots(bank)) {
                    val region = comb.region(bank, field, tilt, spot)
                    if (region.size < 3) continue
                    val x = region.sumOf { it.x.toDouble() }.toFloat() / region.size
                    val y = region.sumOf { it.y.toDouble() }.toFloat() / region.size
                    assertEquals(
                        "tilt $tilt, cell (${spot.col}, ${spot.row})",
                        spot, comb.hit(bank, field, tilt, x, y),
                    )
                }
            }
        }
    }

    /** Tilt turns the comb and nothing else: the same cells, the same order. */
    @Test
    fun tilt_moves_the_cells_and_keeps_the_layout() {
        val comb = comb()
        val flat = comb.place(Bank.LEFT, field, 0f)
        val tipped = comb.place(Bank.LEFT, field, 25f)
        val s = spot(comb, Bank.LEFT, 'S')
        assertTrue(
            "a tilted comb should not place a cell where a flat one does",
            comb.point(s, flat) != comb.point(s, tipped),
        )
        assertEquals(comb.chain, Comb(order, Config()).chain)
    }

    /**
     * Early in the order a portal lands on open ground and takes nothing; late
     * in the order that ground is gone and it lands on the key itself.
     */
    @Test
    fun a_portal_lands_on_ground_early_and_on_a_key_late() {
        val comb = comb()
        val bank = Bank.LEFT
        val chain = comb.chain.getValue(bank)
        val first = spot(comb, bank, order[chain[0]])
        val early = Side.values().mapNotNull { comb.portal(bank, first, it, emptyList()) }
        assertTrue("the first key should have portals", early.isNotEmpty())
        assertTrue("an early portal should land on ground", early.any { !it.onKey })

        val last = chain[chain.size - 2]
        val taken = chain.take(chain.size - 2)
        val late = Side.values().mapNotNull {
            comb.portal(bank, spot(comb, bank, order[last]), it, taken)
        }
        assertTrue("a late portal should land on a key", late.all { it.onKey })
    }

    /** The sides facing the keys the walk can still reach carry no jump. */
    @Test
    fun the_walk_is_not_a_portal() {
        val comb = comb()
        val bank = Bank.LEFT
        val chain = comb.chain.getValue(bank)
        val here = spot(comb, bank, order[chain[2]])
        val taken = chain.take(3)
        for (ahead in 1..2) {
            val next = spot(comb, bank, order[chain[2 + ahead]])
            val side = Side.values().first { Comb.step(here, it) == (next.col to next.row) }
            assertNull(
                "the side facing ${order[chain[2 + ahead]]} should be the walk",
                comb.portal(bank, here, side, taken),
            )
        }
    }

    /** Four sides are left over, and each takes one of the four strides. */
    @Test
    fun the_other_four_sides_are_portals() {
        val comb = comb()
        val bank = Bank.LEFT
        val chain = comb.chain.getValue(bank)
        val here = spot(comb, bank, order[chain[2]])
        val jumps = Side.values().mapNotNull { comb.portal(bank, here, it, chain.take(3)) }
        assertEquals(4, jumps.size)
    }

    /** Ground and holes answer with no key, whatever the stroke has taken. */
    @Test
    fun ground_takes_nothing() {
        val comb = comb(Config(combLeft = ".o", combRight = "E"))
        for (spot in comb.spots(Bank.LEFT)) {
            assertEquals(-1, comb.keyOf(spot))
            assertNull(comb.resolve(spot, emptyList()))
        }
    }

    /** A grid reads the same whether its rows are split on slashes or newlines. */
    @Test
    fun a_slash_and_a_newline_are_the_same_row_break() {
        val slashed = Comb(order, Config(combLeft = "S./.T", combRight = "E"))
        val lined = Comb(order, Config(combLeft = "S.\n.T", combRight = "E"))
        assertEquals(slashed.spots(Bank.LEFT), lined.spots(Bank.LEFT))
    }

    /** The strides are read from the config, and short lists keep the defaults. */
    @Test
    fun the_strides_come_from_the_config() {
        assertEquals(listOf(2, 4, 6, 8), Comb(order, Config(combStrides = "2 4 6 8")).strides)
        assertEquals(listOf(2, 5, 7, 9), Comb(order, Config(combStrides = "2")).strides)
        assertEquals(Comb.DEFAULT_STRIDES, Comb(order, Config(combStrides = "junk")).strides)
    }

    /** A cell outside the lattice is still answered by the nearest one. */
    @Test
    fun a_touch_past_the_edge_means_the_cell_it_overshot() {
        val comb = comb()
        assertNotNull(comb.hit(Bank.LEFT, field, 0f, -500f, -500f))
    }
}
