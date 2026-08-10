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

    private var config = Config()
    private var ring = Ring(ORDER, config)
    private val written = mutableListOf<Long>()
    private var chorder = Chorder(ring, config) { written.add(it) }

    init {
        place()
    }

    private fun place() {
        chorder.place(Bank.LEFT, Field(0f, 0f, 400f, 900f))
        chorder.place(Bank.RIGHT, Field(400f, 0f, 800f, 900f))
    }

    /**
     * Ask for the two ring layout, which the default is not any more.
     *
     * A short slide and a long one meant different keys, and metering a distance
     * under a thumb is the channel that went wrong. What the tests below hold on to
     * is that the layout still works when it is asked for.
     */
    private fun twoRings() {
        config.farLeft = "STKPWHR"
        config.nearLeft = "AO#@^+"
        ring = Ring(ORDER, config)
        chorder = Chorder(ring, config) { written.add(it) }
        place()
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

    /**
     * Slide toward where a key is drawn on the ring, then stop there.
     *
     * Which angle that is depends on what the stroke has taken, because the live
     * keys share the ring out between them. Aim at a key and you get that key.
     */
    private fun slideTo(bank: Bank, symbol: Char) {
        val thumb = chorder.thumb(bank)
        val slot = ring.slotOf(key(symbol), thumb.after)!!
        slide(bank, slot.angle, config.reach(slot.radius) * UNIT)
    }

    /** Where a key sits on the tap layout, which is what a touch-down aims at. */
    private fun cell(bank: Bank, symbol: Char): Cell =
        ring.entry(bank, chorder.thumb(bank).field).first { ORDER[it.key] == symbol }

    /** Tap a key. A touch-down takes the key it lands on, whatever the rings say. */
    private fun touch(bank: Bank, pointer: Int, symbol: Char) {
        val cell = cell(bank, symbol)
        chorder.down(bank, pointer, cell.x, cell.y)
    }

    /** Hold still until whatever was waiting has been taken. */
    private fun rest(bank: Bank) {
        now += config.settleMs + 1
        chorder.settle(bank, UNIT, now)
    }

    private val far = config.reach(Radius.FAR) * UNIT
    private val near = config.reach(Radius.NEAR) * UNIT

    /** `KAOEPT`, written the way two thumbs write it: a tap then slides. */
    private fun keepThe() {
        touch(Bank.LEFT, 0, 'K')
        slideTo(Bank.LEFT, 'A')
        slideTo(Bank.LEFT, 'O')
        touch(Bank.RIGHT, 1, 'E')
        slideTo(Bank.RIGHT, 'p')
        slideTo(Bank.RIGHT, 't')
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

        touch(Bank.LEFT, 2, 'S')
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
        touch(Bank.LEFT, 0, 'R')
        touch(Bank.RIGHT, 1, 't')
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
        twoRings()
        touch(Bank.LEFT, 0, 'K')
        assertEquals("K", keys(Bank.LEFT))
        // Out again toward P, sampled every 8 ms as a finger is, from inside the
        // near band to past the boundary.
        val out = ring.slotOf(key('P'), key('K'))!!.angle
        for (distance in listOf(near * 0.6f, near, config.ringSplit() * UNIT * 1.1f, far)) {
            val thumb = chorder.thumb(Bank.LEFT)
            val (x, y) = from(thumb.markX, thumb.markY, out, distance)
            now += 8
            chorder.move(thumb.pointer, x, y, UNIT, now)
        }
        assertEquals("the vowels were collected on the way out", "KP", keys(Bank.LEFT))
    }

    @Test
    fun stopping_on_the_near_ring_takes_the_vowel() {
        twoRings()
        touch(Bank.LEFT, 0, 'K')
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
        touch(Bank.LEFT, 0, 'S')
        val (x, y) = toward(Bank.LEFT, 'K', far)
        chorder.move(0, x, y, UNIT, now)
        assertEquals("SK", keys(Bank.LEFT))
    }

    /** A point that far from the mark, in the direction a key is drawn. */
    private fun toward(bank: Bank, symbol: Char, distance: Float): Pair<Float, Float> {
        val thumb = chorder.thumb(bank)
        val slot = ring.slotOf(key(symbol), thumb.after)!!
        return from(thumb.markX, thumb.markY, slot.angle, distance)
    }

    /**
     * A tap takes the key it lands on, and the tap layout is the whole field, so
     * every key of a bank can start a stroke without a slide.
     */
    @Test
    fun a_tap_takes_the_key_it_lands_on() {
        for (symbol in "#@^+STKPWHRAO") {
            chorder.reset()
            touch(Bank.LEFT, 0, symbol)
            assertEquals(symbol.toString(), keys(Bank.LEFT))
            chorder.up(0)
        }
    }

    @Test
    fun a_tap_is_a_one_key_stroke() {
        touch(Bank.RIGHT, 0, 't')
        assertEquals(mask("t"), chorder.up(0))
    }

    /**
     * One flick is one key. The next slide is measured from where this one ended,
     * so the distance already travelled cannot be spent again.
     */
    @Test
    fun one_flick_takes_one_key() {
        touch(Bank.LEFT, 0, 'S')
        val thumb = chorder.thumb(Bank.LEFT)
        val (x, y) = toward(Bank.LEFT, 'K', far)
        now += 8
        chorder.move(0, x, y, UNIT, now)
        assertEquals("SK", keys(Bank.LEFT))
        assertEquals(0f, hypot(thumb.markX - x, thumb.markY - y), 0.001f)
        // The same position again is no movement at all, so it takes nothing.
        chorder.move(0, x, y, UNIT, now)
        assertEquals("SK", keys(Bank.LEFT))
    }

    /** A long slide is still one key, however far past the ring it goes. */
    @Test
    fun a_long_flick_takes_one_key() {
        touch(Bank.LEFT, 0, 'S')
        val (x, y) = toward(Bank.LEFT, 'K', far * 4f)
        chorder.move(0, x, y, UNIT, now)
        assertEquals("SK", keys(Bank.LEFT))
    }

    @Test
    fun the_slides_of_one_stroke_do_not_walk_the_thumb_far() {
        val start = cell(Bank.LEFT, 'K')
        keepThe()
        val thumb = chorder.thumb(Bank.LEFT)
        val travel = hypot(thumb.markX - start.x, thumb.markY - start.y)
        assertTrue("drifted $travel", travel <= 3f * far)
    }

    @Test
    fun a_stall_brings_the_ring_to_the_resting_thumb() {
        touch(Bank.LEFT, 0, 'S')
        val thumb = chorder.thumb(Bank.LEFT)
        // Not far enough to have asked for anything, and stopped there.
        val (x, y) = from(thumb.markX, thumb.markY, 0f, config.minSlide * UNIT * 0.8f)
        now += 8
        chorder.move(0, x, y, UNIT, now)
        chorder.stall(Bank.LEFT)
        assertEquals(x, thumb.markX, 0.001f)
        assertEquals(y, thumb.markY, 0.001f)
        // And the gap it closed cannot be spent: the thumb is on its own mark now.
        chorder.move(0, x, y, UNIT, now)
        assertEquals("S", keys(Bank.LEFT))
    }

    /** A thumb that is up has no position, so its ring waits at the field's home. */
    @Test
    fun a_stall_with_the_thumb_up_goes_home() {
        chorder.place(Bank.LEFT, Field(0f, 100f, 200f, 300f))
        chorder.stall(Bank.LEFT)
        val thumb = chorder.thumb(Bank.LEFT)
        assertEquals(100f, thumb.markX, 0.001f)
        assertEquals(100f + 200f * config.homeAt, thumb.markY, 0.001f)
    }

    /** What the ring lights up: the key a slide would take if it stopped now. */
    @Test
    fun the_hover_key_is_shown_before_it_is_taken() {
        touch(Bank.LEFT, 0, 'S')
        slideTo(Bank.LEFT, 'K')
        val thumb = chorder.thumb(Bank.LEFT)
        // Further out along the same ray, which is the same reach and not another
        // one, so the turn rule holds the key back. It is still shown.
        val (x, y) = from(thumb.markX, thumb.markY, thumb.heading, config.farReach * UNIT)
        now += 8
        chorder.move(0, x, y, UNIT, now)
        assertEquals("SK", keys(Bank.LEFT))
        assertTrue("nothing to show", chorder.hover(Bank.LEFT) != Thumb.NO_KEY)
    }
}
