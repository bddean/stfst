package cx.yeet.stfst

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ORDER = "#@^+STKPWHRAO*EUfrpblgtsdz"
private const val UNIT = 1000f

class ChorderTest {

    private val config = Config()
    private val ring = Ring(ORDER, config)
    private val written = mutableListOf<Long>()
    private val chorder = Chorder(ring, config) { written.add(it) }

    init {
        chorder.place(Bank.LEFT, 0f, 0f)
        chorder.place(Bank.RIGHT, 0f, 0f)
    }

    private fun key(symbol: Char) = ORDER.indexOf(symbol)

    private fun mask(symbols: String) =
        symbols.fold(0L) { bits, c -> bits or (1L shl key(c)) }

    private fun keys(bank: Bank) =
        chorder.thumb(bank).keys.map { ORDER[it] }.joinToString("")

    /** A clock the test moves on purpose, because a near key waits on one. */
    private var now = 0L

    private fun from(ox: Float, oy: Float, angle: Float, distance: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angle.toDouble())
        return Pair(ox + (cos(radians) * distance).toFloat(), oy + (sin(radians) * distance).toFloat())
    }

    /**
     * Slide, then stop there long enough for a near key to be taken.
     *
     * What a thumb aiming at one key does. A thumb on its way past does not stop,
     * which is what `crossing_the_near_ring_takes_no_vowel` does instead.
     */
    private fun slide(bank: Bank, angle: Float, distance: Float) {
        val thumb = chorder.thumb(bank)
        val (x, y) = from(thumb.markX, thumb.markY, angle, distance)
        chorder.move(thumb.pointer, x, y, UNIT, now)
        rest(bank)
    }

    private fun touch(bank: Bank, pointer: Int, angle: Float, distance: Float) {
        val (x, y) = from(0f, 0f, angle, distance)
        chorder.down(bank, pointer, x, y, UNIT, now)
        rest(bank)
    }

    /** Hold still until whatever was waiting has been taken. */
    private fun rest(bank: Bank) {
        now += config.settleMs + 1
        chorder.settle(bank, UNIT, now)
    }

    private val far = config.reach(Radius.FAR) * UNIT
    private val near = config.reach(Radius.NEAR) * UNIT

    /** `KAOEPT`, written the way two thumbs write it. */
    private fun keepThe() {
        touch(Bank.LEFT, 0, 0f, far)      // K
        slide(Bank.LEFT, 0f, near)        // A
        slide(Bank.LEFT, 45f, near)       // O
        touch(Bank.RIGHT, 1, -45f, near)  // E
        slide(Bank.RIGHT, 0f, far)        // p
        slide(Bank.RIGHT, 180f, far)      // t
    }

    @Test
    fun two_thumbs_build_one_stroke() {
        keepThe()
        assertEquals("KAO", keys(Bank.LEFT))
        assertEquals("Ept", keys(Bank.RIGHT))
        assertEquals(mask("KAOEpt"), chorder.mask())
        assertTrue("nothing is written until a thumb lifts", written.isEmpty())
    }

    @Test
    fun the_first_lift_writes_the_stroke() {
        keepThe()
        chorder.up(0)
        assertEquals(listOf(mask("KAOEpt")), written)
    }

    /**
     * The reason the first lift writes: the thumb still down keeps its keys, so a
     * run of words sharing a right-hand shape costs one right-hand motion.
     */
    @Test
    fun a_thumb_that_stays_down_keeps_its_keys() {
        keepThe()
        chorder.up(0)
        assertEquals("", keys(Bank.LEFT))
        assertEquals("Ept", keys(Bank.RIGHT))

        touch(Bank.LEFT, 2, -90f, far)    // S
        chorder.up(2)
        assertEquals(listOf(mask("KAOEpt"), mask("SEpt")), written)
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
        assertEquals("", keys(Bank.RIGHT))
        assertEquals(listOf(mask("KAOEpt")), written)
    }

    @Test
    fun a_two_key_chord_is_one_stroke_whichever_thumb_lifts_first() {
        touch(Bank.LEFT, 0, 180f, far)     // R
        touch(Bank.RIGHT, 1, 180f, far)    // t
        assertEquals(mask("Rt"), chorder.up(0))
        assertEquals(0L, chorder.up(1))
        assertEquals(listOf(mask("Rt")), written)
    }

    @Test
    fun both_up_waits_for_the_second_thumb() {
        config.commitOnFirstLift = false
        keepThe()
        chorder.up(0)
        assertTrue("one thumb up is not a stroke yet", written.isEmpty())
        chorder.up(1)
        assertEquals(listOf(mask("KAOEpt")), written)
        assertEquals("", keys(Bank.LEFT))
        assertEquals("", keys(Bank.RIGHT))
    }

    /**
     * The bug that made the consonants unreachable: reaching for one crosses the
     * near ring, and a thumb that does not stop there has not asked for a vowel.
     */
    @Test
    fun crossing_the_near_ring_takes_no_vowel() {
        touch(Bank.LEFT, 0, 0f, far)
        assertEquals("K", keys(Bank.LEFT))
        // Out again in the same direction, sampled every 8 ms as a finger is, from
        // inside the near band to past the boundary.
        for (distance in listOf(near * 0.6f, near, config.ringSplit() * UNIT * 1.1f, far)) {
            val thumb = chorder.thumb(Bank.LEFT)
            val (x, y) = from(thumb.markX, thumb.markY, 45f, distance)
            now += 8
            chorder.move(thumb.pointer, x, y, UNIT, now)
        }
        assertEquals("the vowels were collected on the way out", "KP", keys(Bank.LEFT))
    }

    @Test
    fun stopping_on_the_near_ring_takes_the_vowel() {
        touch(Bank.LEFT, 0, 0f, far)
        val thumb = chorder.thumb(Bank.LEFT)
        val (x, y) = from(thumb.markX, thumb.markY, 0f, near)
        now += 8
        chorder.move(thumb.pointer, x, y, UNIT, now)
        assertEquals("not yet: the thumb may be going further", "K", keys(Bank.LEFT))
        rest(Bank.LEFT)
        assertEquals("KA", keys(Bank.LEFT))
    }

    @Test
    fun a_far_key_is_taken_at_once() {
        config.settleMs = 500L
        touch(Bank.LEFT, 0, -90f, far)
        assertEquals("S", keys(Bank.LEFT))
    }

    @Test
    fun a_thumb_with_no_keys_writes_nothing() {
        touch(Bank.LEFT, 0, 0f, 1f)
        assertEquals(0L, chorder.up(0))
        assertTrue(written.isEmpty())
    }

    @Test
    fun a_tap_is_a_one_key_stroke() {
        touch(Bank.RIGHT, 0, 180f, far)
        assertEquals(mask("t"), chorder.up(0))
    }

    /**
     * One flick is one key. The next slide is measured from where this one ended,
     * so the distance already travelled cannot be spent again.
     */
    @Test
    fun one_flick_takes_one_key() {
        touch(Bank.LEFT, 0, 0f, far)
        assertEquals("K", keys(Bank.LEFT))
        val thumb = chorder.thumb(Bank.LEFT)
        assertEquals(0f, hypot(thumb.markX - far, thumb.markY), 0.001f)
        // The same position again is no movement at all, so it takes nothing.
        chorder.move(0, far, 0f, UNIT, now)
        assertEquals("K", keys(Bank.LEFT))
    }

    /** A long slide is still one key, however far past the ring it goes. */
    @Test
    fun a_long_flick_takes_one_key() {
        touch(Bank.LEFT, 0, 0f, far * 4f)
        assertEquals("K", keys(Bank.LEFT))
    }

    @Test
    fun six_keys_walk_the_thumb_no_further_than_six_slides() {
        keepThe()
        val thumb = chorder.thumb(Bank.LEFT)
        val travel = hypot(thumb.markX - thumb.homeX, thumb.markY - thumb.homeY)
        assertTrue("drifted $travel", travel <= 3f * far)
    }

    @Test
    fun a_stall_puts_the_ring_back_home() {
        chorder.place(Bank.LEFT, 100f, 200f)
        touch(Bank.LEFT, 0, 0f, far)
        chorder.stall(Bank.LEFT)
        assertEquals(100f, chorder.thumb(Bank.LEFT).markX, 0.001f)
        assertEquals(200f, chorder.thumb(Bank.LEFT).markY, 0.001f)
    }
}
