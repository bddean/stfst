package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The motor model: what a thumb motion costs, in seconds, misses, and effort.
 *
 * One mechanism answers all three. A motion is a path with tolerances, the
 * model synthesises the speed a thumb would take along it, and the outputs
 * are readings off that one profile. Speed at each point is the lowest of
 * four ceilings:
 *
 *  - a top speed, reached under a finite acceleration
 *  - curvature: v = bend / cbrt(k), the two-thirds power law
 *  - tolerance: scatter grows with speed, so a channel of half-width h
 *    allows what keeps z sigmas inside it. Over a length this is the
 *    steering law.
 *  - homing: closing on a landing, v <= (distance left + h) / home, which
 *    integrated is Fitts' logarithm
 *
 * Scatter is sigma = jitter + noise * v + drift * sqrt(glide), and that one
 * line ties the outputs together: go faster and the same channel is missed
 * more. The drift term is the glass itself: a slide feels no key edges, so
 * it runs open loop and wanders like a random walk, and only a touch-down
 * re-anchors it. Speed and accuracy are one dial. Only effort is a separate
 * reading.
 *
 * A sharp corner is not on the profile. It is a fresh muscle burst, paid as
 * [Tune.snap] seconds scaled by how far past [Tune.snapAt] the path bends.
 * That is what makes a squiggle dear: not its length, its corners.
 *
 * Units are millimetres and seconds. Nothing here knows a hex, a key, or a
 * screen; a layout is judged by handing its candidate paths in.
 */
class Motor(val tune: Tune = Tune(), val hand: Hand = Hand()) {

    /**
     * Tracing this channel on the glass, starting at rest.
     *
     * The first gate is where the thumb already is, so its slack is the
     * flight's problem and only the gates after it are scored for misses.
     */
    fun slide(gates: List<Gate>): Cost {
        val s = sample(gates) ?: return Cost.NONE
        val v = profile(s)
        val miss = channelMiss(s, v, gates)
        return motion(s, v).copy(pMiss = miss) + snapCost(gates)
    }

    /**
     * Repositioning in the air and landing inside the gate.
     *
     * The only ceiling besides the top speed is the homing one, and the
     * landing is scored as a two-dimensional miss of the gate's slack.
     */
    fun flight(from: Point, to: Gate): Cost {
        val gates = listOf(Gate(from.x, from.y, Float.POSITIVE_INFINITY), to)
        val s = sample(gates)
            ?: return Cost.NONE.copy(pMiss = landMiss(to.slack, tune.jitter))
        val v = FloatArray(s.n) { i ->
            val left = (s.n - 1 - i) * s.step
            min(tune.vMax, (left + to.slack) / tune.home)
        }
        v[0] = 0f
        clampAccel(v, s.step)
        val sigma = tune.jitter + tune.noise * v[s.n - 1]
        return motion(s, v).copy(pMiss = landMiss(to.slack, sigma))
    }

    /**
     * One thumb's whole production: fly to each slide, trace it, lift.
     * A tap is a slide of one gate, so its cost is the flight and the touch.
     */
    fun stroke(slides: List<List<Gate>>, from: Point): Cost {
        var at = from
        var total = Cost.NONE
        for (gates in slides) {
            if (gates.isEmpty()) continue
            total += flight(at, gates.first())
            total += slide(gates)
            total += Cost.NONE.copy(seconds = tune.touch)
            at = Point(gates.last().x, gates.last().y)
        }
        return total
    }

    /**
     * How separable two intended paths are to a recogniser that sees the
     * noisy trace: the mean gap between them, the scatter a production of
     * the first carries, and the chance that production reads as the second.
     *
     * This is accuracy for a soft recogniser, where [slide]'s gates are
     * accuracy for a hard one. A swipe layout wants wide slacks here.
     */
    fun confusion(a: List<Gate>, b: List<Gate>): Confusion {
        val pa = even(a, TEMPLATE)
        val pb = even(b, TEMPLATE)
        val gap = pa.indices
            .sumOf { hypot(pa[it].x - pb[it].x, pa[it].y - pb[it].y).toDouble() }
            .toFloat() / TEMPLATE
        val sigma = meanSigma(a)
        return Confusion(gap, sigma, phi(-gap / (2f * sigma)))
    }

    // ---- the profile -------------------------------------------------------

    /** The speed of tracing this channel: the caps, made reachable. */
    private fun profile(s: Sampled): FloatArray {
        val v = FloatArray(s.n) { i ->
            min(tune.vMax, min(bendCap(s, i), channelCap(s.slack[i])))
        }
        v[0] = 0f
        clampAccel(v, s.step)
        return v
    }

    private class Sampled(
        val x: FloatArray,
        val y: FloatArray,
        val slack: FloatArray,
        val step: Float,
    ) {
        val n get() = x.size
        val heading = FloatArray(n - 1) { atan2(y[it + 1] - y[it], x[it + 1] - x[it]) }
    }

    /** The channel at an even step, or null when it goes nowhere. */
    private fun sample(gates: List<Gate>): Sampled? {
        val pts = dedupe(gates)
        if (pts.size < 2) return null
        val cum = FloatArray(pts.size)
        for (i in 1 until pts.size) {
            cum[i] = cum[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
        }
        val total = cum.last()
        if (total < tune.ds) return null
        val n = max(2, (total / tune.ds).roundToInt() + 1)
        val step = total / (n - 1)
        val x = FloatArray(n)
        val y = FloatArray(n)
        val slack = FloatArray(n)
        var seg = 0
        for (i in 0 until n) {
            val at = min(i * step, total)
            while (seg < pts.size - 2 && cum[seg + 1] < at) seg++
            val span = cum[seg + 1] - cum[seg]
            val t = if (span > 0f) (at - cum[seg]) / span else 0f
            x[i] = pts[seg].x + (pts[seg + 1].x - pts[seg].x) * t
            y[i] = pts[seg].y + (pts[seg + 1].y - pts[seg].y) * t
            slack[i] = lerpSlack(pts[seg].slack, pts[seg + 1].slack, t)
        }
        return Sampled(x, y, slack, step)
    }

    /** Between a gate and a shape point the tighter slack rules the stretch. */
    private fun lerpSlack(a: Float, b: Float, t: Float): Float = when {
        a.isInfinite() && b.isInfinite() -> a
        a.isInfinite() -> b
        b.isInfinite() -> a
        else -> a + (b - a) * t
    }

    private fun dedupe(gates: List<Gate>): List<Gate> {
        val out = mutableListOf<Gate>()
        for (g in gates) {
            val last = out.lastOrNull()
            if (last != null && hypot(g.x - last.x, g.y - last.y) < 1e-4f) {
                // Two gates on one spot are one gate at the tighter slack.
                out[out.size - 1] = last.copy(slack = min(last.slack, g.slack))
            } else out.add(g)
        }
        return out
    }

    /**
     * The two-thirds ceiling, with the heading change read over a window of
     * about a millimetre each way, so a polyline joint reads as the curve it
     * samples rather than as a spike whose size is the sampling step's.
     */
    private fun bendCap(s: Sampled, i: Int): Float {
        if (s.n < 3) return tune.vMax
        val w = max(1, (1.2f / s.step).roundToInt())
        val lo = max(0, i - w)
        val hi = min(s.n - 2, i + w - 1)
        if (hi <= lo) return tune.vMax
        val k = abs(angdiff(s.heading[hi], s.heading[lo])) / ((hi - lo) * s.step)
        if (k < 1e-6f) return tune.vMax
        return tune.bend / cbrt(k)
    }

    /** What keeps z sigmas inside the channel, never below a careful creep. */
    private fun channelCap(slack: Float): Float =
        if (slack.isInfinite()) tune.vMax
        else max(tune.creep, (slack / tune.z - tune.jitter) / tune.noise)

    /** Hold neighbouring speeds within reach of one another. */
    private fun clampAccel(v: FloatArray, step: Float) {
        for (i in 1 until v.size) {
            v[i] = min(v[i], sqrt(v[i - 1] * v[i - 1] + 2f * tune.aMax * step))
        }
        for (i in v.size - 2 downTo 0) {
            v[i] = min(v[i], sqrt(v[i + 1] * v[i + 1] + 2f * tune.aMax * step))
        }
    }

    /** Time and effort read off the profile. Misses are the caller's to add. */
    private fun motion(s: Sampled, v: FloatArray): Cost {
        var seconds = 0f
        var reach = 0f
        var radial = 0f
        var turn = 0f
        var r0 = hypot(s.x[0] - hand.anchor.x, s.y[0] - hand.anchor.y)
        reach += strain(r0) * s.step * 0.5f
        for (i in 1 until s.n) {
            seconds += s.step / max(0.5f * (v[i - 1] + v[i]), 0.5f * tune.creep)
            val r = hypot(s.x[i] - hand.anchor.x, s.y[i] - hand.anchor.y)
            radial += abs(r - r0)
            reach += strain(r) * s.step
            r0 = r
        }
        reach -= strain(r0) * s.step * 0.5f
        for (j in 1 until s.n - 1) turn += abs(angdiff(s.heading[j], s.heading[j - 1]))
        return Cost(seconds, 0f, (s.n - 1) * s.step, turn, reach, radial, 0f)
    }

    /** How far outside the easy band a point sits, in bands, squared. */
    private fun strain(r: Float): Float {
        val out = max(hand.near - r, 0f) + max(r - hand.far, 0f)
        val bands = out / (hand.far - hand.near)
        return bands * bands
    }

    /** The chance of leaving the channel at any gate after the first. */
    private fun channelMiss(s: Sampled, v: FloatArray, gates: List<Gate>): Float {
        val pts = dedupe(gates)
        var pass = 1f
        var cum = 0f
        for (k in 1 until pts.size) {
            cum += hypot(pts[k].x - pts[k - 1].x, pts[k].y - pts[k - 1].y)
            if (pts[k].slack.isInfinite()) continue
            val i = (cum / s.step).roundToInt().coerceIn(0, s.n - 1)
            val sigma = tune.jitter + tune.noise * v[i] + tune.drift * sqrt(cum)
            pass *= erf(pts[k].slack / (sigma * SQRT2))
        }
        return 1f - pass
    }

    /** Missing a round landing: the tail of a two-dimensional scatter. */
    private fun landMiss(slack: Float, sigma: Float): Float =
        if (slack.isInfinite()) 0f else exp(-0.5f * (slack / sigma) * (slack / sigma))

    /** The corners of the channel, paid as muscle bursts. */
    private fun snapCost(gates: List<Gate>): Cost {
        val pts = dedupe(gates)
        var snaps = 0f
        for (j in 1 until pts.size - 1) {
            val a = atan2(pts[j].y - pts[j - 1].y, pts[j].x - pts[j - 1].x)
            val b = atan2(pts[j + 1].y - pts[j].y, pts[j + 1].x - pts[j].x)
            snaps += max(0f, abs(angdiff(b, a)) - tune.snapAt) / (PIF - tune.snapAt)
        }
        return Cost.NONE.copy(seconds = tune.snap * snaps, snaps = snaps)
    }

    /** The mean scatter a production of this channel carries. */
    private fun meanSigma(gates: List<Gate>): Float {
        val s = sample(gates) ?: return tune.jitter
        val v = profile(s)
        // The mean of drift * sqrt(s) over the length is two thirds of its end.
        return tune.jitter + tune.noise * (v.sum() / s.n) +
            tune.drift * (2f / 3f) * sqrt((s.n - 1) * s.step)
    }

    /** The path at n points, evenly spaced along its length. */
    private fun even(gates: List<Gate>, n: Int): List<Point> {
        val s = sample(gates)
            ?: return List(n) { Point(gates.firstOrNull()?.x ?: 0f, gates.firstOrNull()?.y ?: 0f) }
        return (0 until n).map { k ->
            val i = ((s.n - 1).toFloat() * k / (n - 1)).roundToInt()
            Point(s.x[i], s.y[i])
        }
    }

    companion object {
        private val SQRT2 = sqrt(2f)
        private val PIF = Math.PI.toFloat()
        private const val TEMPLATE = 32

        private fun angdiff(a: Float, b: Float): Float {
            var d = a - b
            while (d > Math.PI) d -= 2f * Math.PI.toFloat()
            while (d < -Math.PI) d += 2f * Math.PI.toFloat()
            return d
        }

        /** Abramowitz and Stegun 7.1.26, good to a few parts in ten million. */
        private fun erf(x: Float): Float {
            val t = 1f / (1f + 0.3275911f * abs(x))
            val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t -
                0.284496736f) * t + 0.254829592f) * t * exp(-x * x)
            return if (x < 0f) -y else y
        }

        private fun phi(x: Float): Float = 0.5f * (1f + erf(x / SQRT2))
    }
}

/**
 * A point the path must pass, and how far to either side still passes.
 *
 * Slack is a lateral half-width in millimetres, or the radius a landing must
 * fall in. Infinite slack is a shape point that constrains nothing. What a
 * gate's slack should be is the layout's question: the room to the nearest
 * cell whose key the stroke must not take.
 */
data class Gate(val x: Float, val y: Float, val slack: Float)

/**
 * The parameters of the thumb. Every one is measurable from recorded traces,
 * and until then these are literature-shaped defaults.
 */
data class Tune(
    /** Top speed, mm/s. */
    val vMax: Float = 400f,
    /** Acceleration, mm/s^2. */
    val aMax: Float = 8000f,
    /** Two-thirds power law gain: v = bend / cbrt(kappa), mm^(2/3)/s. */
    val bend: Float = 80f,
    /** Lateral scatter grown per mm/s of speed, so seconds. */
    val noise: Float = 0.012f,
    /** Lateral scatter at rest, mm. */
    val jitter: Float = 0.6f,
    /** Scatter grown per root millimetre of glide, mm. Glass has no key
     *  edges, so a slide runs open loop; a touch-down re-anchors. */
    val drift: Float = 0.1f,
    /** Sigmas of margin the thumb budgets inside a channel. */
    val z: Float = 2.5f,
    /** Homing time constant of a landing, s. Fitts' b is about home * ln 2. */
    val home: Float = 0.15f,
    /** The speed a thumb will not plan below, however tight the channel. */
    val creep: Float = 20f,
    /** Putting a thumb down and lifting it, s. */
    val touch: Float = 0.10f,
    /** One full reversal of direction, s. */
    val snap: Float = 0.08f,
    /** The bend a corner starts at, radians. */
    val snapAt: Float = 0.5236f,
    /** Sampling step along the path, mm. Part of the model: corner sharpness
     *  is read at thumb scale, so it is not a knob to converge. */
    val ds: Float = 0.4f,
)

/** Where the thumb hinges, and the band of reach that costs nothing. */
data class Hand(
    val anchor: Point = Point(0f, 0f),
    val near: Float = 25f,
    val far: Float = 65f,
)

/**
 * What a motion cost. Seconds and misses are calibrated things; the rest are
 * the comfort components, kept apart because their exchange rate is taste.
 *
 * `travel` is path length in mm. `turn` is heading change in radians.
 * `reach` is millimetres weighted by how far outside the easy band they were
 * walked. `radial` is millimetres of thumb extension and retraction. `snaps`
 * is corners, each scaled by its sharpness.
 */
data class Cost(
    val seconds: Float,
    val pMiss: Float,
    val travel: Float,
    val turn: Float,
    val reach: Float,
    val radial: Float,
    val snaps: Float,
) {
    operator fun plus(o: Cost) = Cost(
        seconds + o.seconds,
        1f - (1f - pMiss) * (1f - o.pMiss),
        travel + o.travel,
        turn + o.turn,
        reach + o.reach,
        radial + o.radial,
        snaps + o.snaps,
    )

    /** Two thumbs at once: the slower sets the pace, everything else adds. */
    fun alongside(o: Cost) = (this + o).copy(seconds = max(seconds, o.seconds))

    /** One number for comfort. */
    fun effort(w: Weights = Weights()): Float =
        w.travel * travel + w.turn * turn + w.reach * reach +
            w.radial * radial + w.snap * snaps

    companion object {
        val NONE = Cost(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/** The exchange rates between the comfort components. Taste until fitted. */
data class Weights(
    val travel: Float = 0.01f,
    val turn: Float = 0.15f,
    val reach: Float = 0.05f,
    val radial: Float = 0.005f,
    val snap: Float = 0.4f,
)

/** How separable two paths are: the gap, the scatter, and the mixup chance. */
data class Confusion(val gap: Float, val sigma: Float, val p: Float)
