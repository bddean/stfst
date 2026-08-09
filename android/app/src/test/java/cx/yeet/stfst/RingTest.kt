package cx.yeet.stfst

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

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    /** A point that distance and heading away from the origin. */
    private fun at(angle: Float, distance: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angle.toDouble())
        return Pair((cos(radians) * distance).toFloat(), (sin(radians) * distance).toFloat())
    }

    /** Slide from the origin at a heading and see which key it takes. */
    private fun slide(bank: Bank, after: Int, angle: Float, distance: Float): Int? {
        val (x, y) = at(angle, distance)
        return ring.pick(bank, after, 0f, 0f, x, y, UNIT)
    }

    @Test
    fun the_banks_split_at_the_star() {
        assertEquals(0 until 13, ring.keys(Bank.LEFT))
        assertEquals(13 until 26, ring.keys(Bank.RIGHT))
        assertEquals(Bank.LEFT, ring.bankOf(key('A')))
        assertEquals(Bank.RIGHT, ring.bankOf(key('*')))
    }

    @Test
    fun every_key_has_a_slot() {
        for (k in ORDER.indices) {
            assertNotNull("no slot for ${ORDER[k]}", ring.slotOf(k))
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
                    val slot = ring.slotOf(k)!!
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
        // Straight up is S on the far ring and nothing on the near one, so a short
        // slide up falls through to the far key rather than choosing at random.
        assertEquals(key('S'), slide(Bank.LEFT, -1, -90f, far))
        assertEquals(key('A'), slide(Bank.LEFT, -1, 0f, near))
        assertEquals(key('K'), slide(Bank.LEFT, -1, 0f, far))
    }

    /**
     * The boundary sits between the rings, not on one of them, so a slide that
     * falls short of a far key still reads as far and not as its near neighbour.
     *
     * Undershooting was what made the consonants unreachable: after one consonant
     * the near ring holds only the vowels, so every short flick became A or O.
     */
    @Test
    fun a_slide_most_of_the_way_to_a_far_key_is_still_far() {
        assertEquals(key('P'), slide(Bank.LEFT, key('K'), 45f, config.ringSplit() * UNIT + 1f))
        assertEquals(key('O'), slide(Bank.LEFT, key('K'), 45f, config.ringSplit() * UNIT - 1f))
        // Two thirds of the way to a far key is a far key, so a thumb aiming at a
        // consonant has room to fall short of it.
        assertTrue("the boundary is too far out", config.ringSplit() < config.farReach * 0.7f)
        assertEquals(key('W'), slide(Bank.LEFT, key('K'), 90f, far * 0.7f))
        assertEquals(key('R'), slide(Bank.LEFT, key('K'), 180f, far * 2f))
    }

    /** `KAOEPT` writes "keep the", and this is the six slides that make it. */
    @Test
    fun the_worked_example_from_the_design() {
        var after = -1
        for ((angle, distance, expected) in listOf(
            Triple(0f, far, 'K'),
            Triple(0f, near, 'A'),
            Triple(45f, near, 'O'),
        )) {
            val got = slide(Bank.LEFT, after, angle, distance)
            assertEquals("left wanted $expected", key(expected), got)
            after = got!!
        }
        after = -1
        for ((angle, distance, expected) in listOf(
            Triple(-45f, near, 'E'),
            Triple(0f, far, 'p'),
            Triple(180f, far, 't'),
        )) {
            val got = slide(Bank.RIGHT, after, angle, distance)
            assertEquals("right wanted $expected", key(expected), got)
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

    @Test
    fun separation_takes_the_short_way_round() {
        assertEquals(10f, Ring.separation(-5f, 5f), 0.001f)
        assertEquals(20f, Ring.separation(350f, 10f), 0.001f)
        assertEquals(180f, Ring.separation(0f, 180f), 0.001f)
    }
}
