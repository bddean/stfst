package cx.yeet.stfst

import kotlin.math.abs
import kotlin.random.Random

/**
 * A ranked point set on the unit square, spread the way blue noise is: every
 * point as far as it can be from the ones before it.
 *
 * Two properties are what the drawing uses. Any prefix of the list is itself a
 * blue-noise set, so a density is a count; and distance wraps at the edges, so
 * the square tiles the plane without a seam.
 */
object Noise {
    /** How many points the square holds, which is the densest fill available. */
    const val COUNT = 1200

    val points: List<Point> by lazy { build() }

    /** Candidates per point. Mitchell's best-candidate, with a fixed budget. */
    private const val TRIES = 12
    private const val GRID = 32

    private fun build(): List<Point> {
        val random = Random(7)
        val out = ArrayList<Point>(COUNT)
        val buckets = Array(GRID * GRID) { mutableListOf<Point>() }
        fun bucket(x: Float, y: Float) =
            (y * GRID).toInt().coerceIn(0, GRID - 1) * GRID + (x * GRID).toInt().coerceIn(0, GRID - 1)
        repeat(COUNT) {
            var best = Point(0f, 0f)
            var bestGap = -1f
            repeat(TRIES) {
                val x = random.nextFloat()
                val y = random.nextFloat()
                val gap = nearest(buckets, x, y)
                if (gap > bestGap) {
                    bestGap = gap
                    best = Point(x, y)
                }
            }
            out.add(best)
            buckets[bucket(best.x, best.y)].add(best)
        }
        return out
    }

    /** How far the nearest point already placed is, squared, which ranks the same. */
    private fun nearest(buckets: Array<MutableList<Point>>, x: Float, y: Float): Float {
        val cx = (x * GRID).toInt().coerceIn(0, GRID - 1)
        val cy = (y * GRID).toInt().coerceIn(0, GRID - 1)
        var best = 1f
        for (dy in -REACH..REACH) for (dx in -REACH..REACH) {
            val bx = (cx + dx + GRID) % GRID
            val by = (cy + dy + GRID) % GRID
            for (p in buckets[by * GRID + bx]) {
                val ux = wrap(p.x - x)
                val uy = wrap(p.y - y)
                val d = ux * ux + uy * uy
                if (d < best) best = d
            }
        }
        return best
    }

    private const val REACH = 3

    private fun wrap(d: Float): Float = abs(d).let { if (it > 0.5f) 1f - it else it }
}
