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

    /** Every key of the order lands in exactly one bank, on the walk or off it. */
    @Test
    fun the_two_combs_hold_the_whole_order() {
        val comb = comb()
        val placed = Bank.values().flatMap {
            comb.chain.getValue(it) + comb.floats.getValue(it)
        }
        assertEquals(order.length, placed.size)
        assertEquals(order.length, placed.distinct().size)
    }

    /**
     * A key that does not touch the next key of the walk is a float, and steno
     * order says nothing about one: the modifiers stay takeable however far the
     * walk has gone, even where a float sits against the end cluster.
     */
    @Test
    fun a_button_clear_of_the_chain_is_a_float() {
        val comb = comb()
        val loose = Bank.values().flatMap { comb.floats.getValue(it) }.map { order[it] }
        assertEquals(listOf('#', '@', '+', '*'), loose.sortedBy { order.indexOf(it) })
        val bank = Bank.LEFT
        val late = comb.chain.getValue(bank).last()
        for (key in comb.floats.getValue(bank)) {
            assertTrue("${order[key]} should survive the whole walk", comb.live(key, listOf(late)))
            assertTrue("${order[key]} is spent once taken", !comb.live(key, listOf(key)))
        }
    }

    /** Taking a float does not move the walk on. */
    @Test
    fun a_float_does_not_spend_the_chain() {
        val comb = comb()
        val bank = Bank.LEFT
        val first = comb.chain.getValue(bank).first()
        val float = comb.floats.getValue(bank).first()
        assertTrue(comb.live(first, listOf(float)))
    }

    /**
     * The last two keys and both floats close a ring around a hole, and a
     * portal can land in it, so one jump puts every key of the end cluster a
     * single hop away. Closed, because a portal reaches the hole and nothing
     * ever needs to pass through it.
     */
    @Test
    fun the_end_cluster_closes_a_ring_a_portal_can_reach() {
        val comb = comb()
        for (bank in Bank.values()) {
            val hole = comb.spots(bank).first { it.v == Comb.HOLE }
            val ring = Side.values()
                .map { Comb.step(hole, it) }
                .map { (col, row) -> comb.spotAt(bank, col, row) }
            assertTrue(
                "nothing should be able to walk through the hole in $bank",
                ring.all { it == null || comb.keyOf(it) >= 0 },
            )
            assertEquals(
                "the ring in $bank should hold the last two keys and both floats",
                (comb.chain.getValue(bank).takeLast(2) + comb.floats.getValue(bank)).toSet(),
                ring.filterNotNull().map { comb.keyOf(it) }.toSet(),
            )

            val chain = comb.chain.getValue(bank)
            val landings = chain.indices.flatMap { i ->
                Side.values().mapNotNull {
                    comb.portal(bank, spot(comb, bank, order[chain[i]]), it, chain.take(i + 1))
                }
            }
            assertTrue(
                "a portal in $bank should be able to land in the hole",
                landings.any { it.spot == hole },
            )
        }
    }

    /**
     * Two portals out of one cell never come to rest on the same ground, or on
     * touching ground: a side that means the same place as another side is a
     * side spent for nothing.
     */
    @Test
    fun the_landings_of_a_cell_stand_apart() {
        val comb = comb()
        for (bank in Bank.values()) {
            val chain = comb.chain.getValue(bank)
            for (i in chain.indices) {
                val here = spot(comb, bank, order[chain[i]])
                val rests = Side.values()
                    .mapNotNull { comb.portal(bank, here, it, chain.take(i + 1)) }
                    .filter { !it.onKey }
                    .map { it.spot }
                for (a in rests.indices) for (b in a + 1 until rests.size) {
                    assertTrue(
                        "${order[chain[i]]} in $bank rests at (${rests[a].col}, ${rests[a].row})" +
                            " and (${rests[b].col}, ${rests[b].row})",
                        comb.hops(rests[a], rests[b]) >= 2,
                    )
                }
            }
        }
    }

    /**
     * Every key a thumb could still want is still available: from any key,
     * walking and jumping reaches every key ahead of it and every float,
     * without taking anything on the way.
     *
     * This is the claim the whole layout has to keep. A grid can satisfy steno
     * order and still strand a key behind a wall of keys the chord does not
     * want, and no amount of tuning the strides would show it.
     */
    @Test
    fun every_key_ahead_is_still_reachable() {
        val comb = comb()
        for (bank in Bank.values()) {
            val chain = comb.chain.getValue(bank)
            val loose = comb.floats.getValue(bank)
            for (i in chain.indices) {
                val reached = reachable(comb, bank, chain[i])
                for (want in chain.drop(i + 1) + loose) {
                    assertTrue(
                        "${order[want]} is out of reach from ${order[chain[i]]} in $bank",
                        want in reached,
                    )
                }
            }
        }
    }

    /**
     * The portals reach as far as the grid allows.
     *
     * Two hops is what a thumb should need for a key it still wants: one into a
     * resting place, one onto the key. Whether every key is within two is a
     * fact about the grid and not about the strides, so the test asks the grid:
     * it works out the best four resting places on the board and holds the
     * strides to that. Reaching is not enough, and a number in a test would go
     * stale the first time a grid changed.
     */
    @Test
    fun the_portals_reach_as_far_as_the_grid_allows() {
        val comb = comb()
        for (bank in Bank.values()) {
            val chain = comb.chain.getValue(bank)
            val loose = comb.floats.getValue(bank)
            for (i in chain.indices) {
                val taken = chain.take(i + 1)
                val home = spot(comb, bank, order[chain[i]])
                fun around(s: Spot) = Side.values().mapNotNull {
                    val (col, row) = Comb.step(s, it)
                    comb.spotAt(bank, col, row)
                }
                fun keysBy(s: Spot) = around(s).mapNotNull { comb.resolve(it, taken) }.toSet()
                val want = (chain.drop(i + 1) + loose).toSet()
                val rests = comb.spots(bank).filter { comb.resolve(it, taken) == null }
                // A resting cell beside the thumb is a hop it does not pay for.
                val free = keysBy(home) + around(home).filter { it in rests }.flatMap { keysBy(it) }
                val brings = rests.map { (keysBy(it) - free) intersect want }
                    .filter { it.isNotEmpty() }.distinct()
                val ceiling = (free intersect want).size +
                    unions(brings, 4).maxOf { it.size }
                val got = free + Side.values()
                    .mapNotNull { comb.portal(bank, home, it, taken) }
                    .flatMap { if (it.onKey) setOf(it.aim) else keysBy(it.spot) }
                assertEquals(
                    "${order[chain[i]]} in $bank reaches less than the grid allows",
                    ceiling, (got intersect want).size,
                )
            }
        }
    }

    /**
     * A ridden portal reaches every key the thumb still wants.
     *
     * Four sides can only name four distances, and on the right comb that left
     * two keys further than two hops: `-E` could not see `-T`, and `U` could
     * not see `-S`. A stride that is not spent by one jump turns each side into
     * a run of landings, and the runs cover what the four could not.
     */
    @Test
    fun a_ridden_portal_reaches_every_key_ahead() {
        val comb = comb()
        for (bank in Bank.values()) {
            val chain = comb.chain.getValue(bank)
            val loose = comb.floats.getValue(bank)
            for (i in chain.indices) {
                val taken = chain.take(i + 1)
                val home = spot(comb, bank, order[chain[i]])
                fun around(s: Spot) = Side.values().mapNotNull {
                    val (col, row) = Comb.step(s, it)
                    comb.spotAt(bank, col, row)
                }
                fun keysBy(s: Spot) = around(s).mapNotNull { comb.resolve(it, taken) }.toSet()
                val reach = (keysBy(home) + around(home)
                    .filter { comb.resolve(it, taken) == null }.flatMap { keysBy(it) })
                    .toMutableSet()
                for (side in Side.values()) {
                    var land = comb.portal(bank, home, side, taken) ?: continue
                    while (true) {
                        reach += if (land.onKey) setOf(land.aim) else keysBy(land.spot)
                        land = comb.onward(bank, land, taken) ?: break
                    }
                }
                for (want in chain.drop(i + 1) + loose) {
                    assertTrue(
                        "${order[want]} is more than one slide from ${order[chain[i]]} in $bank",
                        want in reach,
                    )
                }
            }
        }
    }

    /** Every union of up to `most` of these sets, the empty one included. */
    private fun unions(from: List<Set<Int>>, most: Int): List<Set<Int>> {
        var level = listOf(emptySet<Int>() to -1)
        val out = mutableListOf(emptySet<Int>())
        repeat(most) {
            level = level.flatMap { (u, last) ->
                (last + 1 until from.size).map { (u + from[it]) to it }
            }
            out.addAll(level.map { it.first })
        }
        return out
    }

    /** The keys one thumb can still take from here without taking another. */
    private fun reachable(comb: Comb, bank: Bank, from: Int): Set<Int> {
        val taken = listOf(from)
        val queue = ArrayDeque(comb.spots(bank).filter { comb.keyOf(it) == from })
        val seen = queue.toMutableSet()
        val out = mutableSetOf<Int>()
        while (queue.isNotEmpty()) {
            val here = queue.removeFirst()
            val next = Side.values().flatMap {
                val (col, row) = Comb.step(here, it)
                listOfNotNull(
                    comb.spotAt(bank, col, row),
                    comb.portal(bank, here, it, taken)?.spot,
                )
            }
            for (spot in next) {
                // Entering a live cell takes it, which ends the errand there.
                comb.resolve(spot, taken)?.let { out.add(it) }
                    ?: if (seen.add(spot)) queue.addLast(spot) else Unit
            }
        }
        return out
    }

    /** A cell never spills past its own hexagon, so the comb ends where it is drawn. */
    @Test
    fun a_cell_keeps_inside_its_hexagon() {
        val comb = comb()
        for (tilt in listOf(0f, 12f, -30f)) {
            for (bank in Bank.values()) {
                val at = comb.place(bank, field, tilt)
                for (spot in comb.spots(bank)) {
                    val middle = comb.point(spot, at)
                    for (corner in comb.region(bank, field, tilt, spot)) {
                        val far = kotlin.math.hypot(corner.x - middle.x, corner.y - middle.y)
                        assertTrue(
                            "cell (${spot.col}, ${spot.row}) reaches $far past ${at.radius}",
                            far <= at.radius * 1.001f,
                        )
                    }
                }
            }
        }
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
        val comb = comb(Config(combLeft = "S.S/T..", combRight = "E"))
        val buttons = comb.buttons.getValue(Bank.LEFT).filter { it.v == 'S' }
        assertEquals(2, buttons.size)
        assertEquals(listOf('S', 'T'), comb.chain.getValue(Bank.LEFT).map { order[it] })
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

    /** Two thumbs sweep mirrored arcs, so one angle turns the banks opposite ways. */
    @Test
    fun the_two_banks_turn_opposite_ways() {
        val comb = comb(Config(tilt = 20f))
        val left = comb.place(Bank.LEFT, field, 20f)
        val right = comb.place(Bank.RIGHT, field, 20f)
        assertEquals(left.ct, right.ct, 1e-5f)
        assertEquals(left.st, -right.st, 1e-5f)
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
     * A portal rests on open ground and takes nothing, unless no ground beside
     * the key it aims at is worth the hop. Then it lands on the key and takes
     * it: one hop instead of a choice.
     */
    @Test
    fun a_portal_rests_on_ground_or_takes_the_key() {
        val comb = comb()
        val first = spot(comb, Bank.LEFT, order[comb.chain.getValue(Bank.LEFT)[0]])
        val early = Side.values().mapNotNull { comb.portal(Bank.LEFT, first, it, emptyList()) }
        assertTrue("an early portal should rest on ground", early.any { !it.onKey })

        val all = Bank.values().flatMap { bank ->
            comb.chain.getValue(bank).flatMap { key ->
                Side.values().mapNotNull {
                    comb.portal(bank, spot(comb, bank, order[key]), it, listOf(key))
                }
            }
        }
        assertTrue("a portal should land on a key where no ground earns it", all.any { it.onKey })
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

    /**
     * Four sides are left over, and at the head of the order each takes one of
     * the four strides and aims somewhere of its own. Further down a stride can
     * run out of order to aim at, and that side then carries no jump.
     */
    @Test
    fun the_other_four_sides_are_portals() {
        val comb = comb()
        val bank = Bank.LEFT
        val first = spot(comb, bank, order[comb.chain.getValue(bank)[0]])
        val jumps = Side.values().mapNotNull { comb.portal(bank, first, it, emptyList()) }
        assertEquals(4, jumps.size)
        assertEquals(4, jumps.map { it.aim }.distinct().size)
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

    /** A cell outside the lattice is still answered by the nearest one. */
    @Test
    fun a_touch_past_the_edge_means_the_cell_it_overshot() {
        val comb = comb()
        assertNotNull(comb.hit(Bank.LEFT, field, 0f, -500f, -500f))
    }
}
