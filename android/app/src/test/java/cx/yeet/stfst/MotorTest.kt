package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The claims the motor model rests on. Each is a shape of behaviour named in
 * the literature, asserted as an inequality rather than a fitted number, so
 * retuning the parameters cannot break a test without breaking the physics.
 */
class MotorTest {

    private val motor = Motor(hand = Hand(anchor = Point(0f, -20f)))

    private fun line(fromX: Float, toX: Float, y: Float, slack: Float) =
        listOf(Gate(fromX, y, slack), Gate(toX, y, slack))

    /** A zigzag of amplitude 3 over six segments: 19 mm of path, 5 corners. */
    private fun squiggle(slack: Float): List<Gate> =
        (0..6).map { Gate(it.toFloat(), if (it % 2 == 0) 30f else 33f, slack) }

    /**
     * The claim the model exists for: a single long motion is cheaper than
     * short precise squiggles, though the squiggles cover far less path.
     */
    @Test
    fun a_long_line_beats_short_squiggles() {
        val long = motor.slide(line(0f, 45f, 30f, 5f))
        val wiggle = motor.slide(squiggle(5f))
        assertTrue("squiggle should be shorter", wiggle.travel < long.travel * 0.5f)
        assertTrue(
            "squiggle ${wiggle.seconds}s vs line ${long.seconds}s",
            wiggle.seconds > long.seconds,
        )
    }

    /** The same holds for a smooth curve: bend is cheap, corners are not. */
    @Test
    fun a_long_curve_beats_short_squiggles() {
        val arc = (0..16).map {
            val a = (PI / 2 * it / 16).toFloat()
            Gate(30f * sin(a), 60f - 30f * cos(a), 5f)
        }
        val curve = motor.slide(arc)
        val wiggle = motor.slide(squiggle(5f))
        assertTrue("arc should be over twice the path", curve.travel > wiggle.travel * 2f)
        assertTrue(
            "curve ${curve.seconds}s vs squiggle ${wiggle.seconds}s",
            curve.seconds < wiggle.seconds,
        )
        assertEquals(0f, curve.snaps, 1e-4f)
        assertTrue(wiggle.snaps > 3f)
    }

    /** The steering law: a channel half as wide takes longer to thread. */
    @Test
    fun a_narrow_channel_is_slower() {
        val wide = motor.slide(line(0f, 40f, 30f, 5f))
        val tight = motor.slide(line(0f, 40f, 30f, 2f))
        assertTrue("${tight.seconds} vs ${wide.seconds}", tight.seconds > wide.seconds * 1.5f)
    }

    /** The two-thirds power law: tighter bend, lower speed, more time. */
    @Test
    fun a_tight_bend_is_slower_than_a_gentle_one() {
        fun arc(radius: Float, length: Float): List<Gate> = (0..24).map {
            val a = length / radius * it / 24
            Gate(radius * sin(a), 30f + radius * (1 - cos(a)), 8f)
        }
        val gentle = motor.slide(arc(40f, 30f))
        val tight = motor.slide(arc(6f, 30f))
        assertTrue("${tight.seconds} vs ${gentle.seconds}", tight.seconds > gentle.seconds)
    }

    /** Fitts: time grows with distance and with the tightness of the target. */
    @Test
    fun a_far_or_tight_target_takes_longer_to_hit() {
        fun t(d: Float, slack: Float) = motor.flight(Point(0f, 30f), Gate(d, 30f, slack)).seconds
        assertTrue(t(40f, 3f) > t(20f, 3f))
        assertTrue(t(20f, 1f) > t(20f, 3f))
    }

    /** Fitts again: halving the target costs the same at any range. */
    @Test
    fun halving_the_target_costs_the_same_at_any_range() {
        fun t(d: Float, slack: Float) = motor.flight(Point(0f, 30f), Gate(d, 30f, slack)).seconds
        val near = t(20f, 1.5f) - t(20f, 3f)
        val far = t(40f, 1.5f) - t(40f, 3f)
        assertTrue("near $near vs far $far", kotlin.math.abs(near - far) < 0.3f * near)
    }

    /** Speed and accuracy are one dial: what must be threaded slowly is
     *  still missed more once the channel is tighter than the hand. */
    @Test
    fun a_channel_tighter_than_the_hand_is_missed() {
        fun miss(slack: Float) = motor.slide(
            listOf(Gate(0f, 30f, slack), Gate(15f, 30f, slack), Gate(30f, 30f, slack)),
        ).pMiss
        assertTrue(miss(1f) > 5f * miss(5f))
        assertTrue(miss(5f) < 0.15f)
    }

    /** A tight landing is missed more than a roomy one. */
    @Test
    fun a_tight_landing_is_missed_more() {
        fun miss(slack: Float) = motor.flight(Point(0f, 30f), Gate(30f, 30f, slack)).pMiss
        assertTrue(miss(1f) > 5f * miss(3f))
    }

    /** Glass has no key edges: the further a glide has run open loop, the
     *  more the same gate is missed, at the same speed and the same slack. */
    @Test
    fun a_long_glide_wanders() {
        fun missAt(d: Float) = motor.slide(
            listOf(Gate(0f, 30f, Float.POSITIVE_INFINITY), Gate(d, 30f, 4f)),
        ).pMiss
        assertTrue("${missAt(40f)} vs ${missAt(10f)}", missAt(40f) > 1.5f * missAt(10f))
    }

    /** Misses compound over gates, so a longer chord risks more. */
    @Test
    fun misses_compound_over_gates() {
        val few = motor.slide((0..2).map { Gate(it * 10f, 30f, 2f) })
        val many = motor.slide((0..6).map { Gate(it * 5f, 30f, 2f) })
        assertTrue(many.pMiss > few.pMiss)
    }

    /** Comfort reads the hand: the same line further out costs reach. */
    @Test
    fun reach_is_paid_outside_the_easy_band() {
        val near = motor.slide(line(0f, 20f, 30f, 5f))
        val far = motor.slide(line(0f, 20f, 90f, 5f))
        assertEquals(0f, near.reach, 1e-4f)
        assertTrue(far.reach > 1f)
    }

    /** Sweeping the arc of the thumb is not extension: radial tells them apart. */
    @Test
    fun radial_travel_is_extension_not_sweep() {
        val out = motor.slide(listOf(Gate(0f, 20f, 5f), Gate(0f, 44f, 5f)))
        val sweep = motor.slide((0..12).map {
            val a = (PI / 3 * it / 12 + PI / 3).toFloat()
            Gate(40f * cos(a), 40f * sin(a) - 20f, 5f)
        })
        assertTrue(out.radial > 20f)
        assertTrue(sweep.radial < 2f)
    }

    /** A stroke is flights, slides, and touches, and a tap is the small case. */
    @Test
    fun a_tap_is_a_flight_and_a_touch() {
        val from = Point(0f, 30f)
        val tap = motor.stroke(listOf(listOf(Gate(25f, 30f, 4f))), from)
        val flight = motor.flight(from, Gate(25f, 30f, 4f))
        assertEquals(flight.seconds + motor.tune.touch, tap.seconds, 1e-4f)
    }

    /** Two thumbs at once cost the slower thumb's time, not the sum. */
    @Test
    fun thumbs_in_parallel_cost_the_slower_one() {
        val left = motor.slide(line(0f, 40f, 30f, 5f))
        val right = motor.slide(line(0f, 10f, 30f, 5f))
        val both = left.alongside(right)
        assertEquals(left.seconds, both.seconds, 1e-4f)
        assertEquals(left.travel + right.travel, both.travel, 1e-4f)
    }

    /** For a soft recogniser, paths further apart are confused less. */
    @Test
    fun confusion_falls_with_separation() {
        val a = line(0f, 40f, 30f, Float.POSITIVE_INFINITY)
        val close = line(0f, 40f, 32f, Float.POSITIVE_INFINITY)
        val apart = line(0f, 40f, 60f, Float.POSITIVE_INFINITY)
        val pClose = motor.confusion(a, close).p
        val pApart = motor.confusion(a, apart).p
        assertTrue("$pClose vs $pApart", pClose > 10f * pApart)
    }

    /** A slack of infinity means a shape point, and it breaks nothing. */
    @Test
    fun open_gates_constrain_nothing() {
        val open = motor.slide(
            listOf(Gate(0f, 30f, 5f), Gate(20f, 30f, Float.POSITIVE_INFINITY), Gate(40f, 30f, 5f)),
        )
        assertTrue(open.seconds.isFinite())
        assertTrue(open.pMiss < 0.10f)
    }
}
