package cx.yeet.stfst

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Which thumb owns a key, and where that key sits.
 *
 * A chord is a rising run through the steno order, so the keys a half-written
 * stroke can still take are exactly the ones later in the order. That is the
 * whole rule. No dictionary is consulted: a fixed key in a fixed direction is
 * what a thumb can learn, and a ring that changed shape with the dictionary
 * could not be learned at all.
 */
class Ring(val order: String, val config: Config) {

    /** Where the right thumb's keys start. Everything before it is the left's. */
    private val split = order.indexOf(config.splitKey).let { if (it < 0) order.length / 2 else it }

    fun bankOf(key: Int): Bank = if (key < split) Bank.LEFT else Bank.RIGHT

    fun keys(bank: Bank): IntRange =
        if (bank == Bank.LEFT) 0 until split else split until order.length

    fun label(key: Int): String = order[key].toString()

    /** A key's slot, or null for a key the configuration left off both rings. */
    fun slotOf(key: Int): Slot? {
        val bank = bankOf(key)
        val symbol = order[key]
        val far = config.far(bank).indexOf(symbol)
        if (far >= 0) return Slot(Radius.FAR, angle(bank, Radius.FAR, far))
        val near = config.near(bank).indexOf(symbol)
        if (near >= 0) return Slot(Radius.NEAR, angle(bank, Radius.NEAR, near))
        return null
    }

    /** Slots run clockwise in steno order from the ring's start angle. */
    private fun angle(bank: Bank, radius: Radius, index: Int): Float {
        val slots = config.slots(radius)
        return config.startAngle(bank, radius) + index * (360f / slots)
    }

    /**
     * The keys a thumb can still take. Empty means the bank is finished, which is
     * what an empty ring tells the thumb.
     */
    fun live(bank: Bank, after: Int): List<Int> =
        keys(bank).filter { it > after && slotOf(it) != null }

    /**
     * The key a slide from (ox, oy) to (px, py) chooses, or null when the slide is
     * too short to mean anything.
     *
     * One function for the first key and for every key after it. The first slide
     * starts from the field's home point with nothing taken yet, so "start at any
     * key" is the same question as "which key next", asked with `after = -1`.
     */
    fun pick(bank: Bank, after: Int, ox: Float, oy: Float, px: Float, py: Float, unit: Float): Int? {
        val dx = px - ox
        val dy = py - oy
        val distance = hypot(dx, dy)
        if (distance < config.minSlide * unit) return null
        val wanted = if (distance < config.farRadius * unit) Radius.NEAR else Radius.FAR
        val heading = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val live = live(bank, after)
        // The wanted ring first, so a short flick cannot be stolen by a far key at
        // a slightly better angle. Falling through to the other ring is what keeps
        // a live key reachable when its own ring is empty.
        return nearest(live, wanted, heading) ?: nearest(live, null, heading)
    }

    private fun nearest(live: List<Int>, radius: Radius?, heading: Float): Int? {
        var best: Int? = null
        var bestOff = Float.MAX_VALUE
        for (key in live) {
            val slot = slotOf(key) ?: continue
            if (radius != null && slot.radius != radius) continue
            val off = separation(slot.angle, heading)
            if (off < bestOff && off <= config.snapDegrees) {
                best = key
                bestOff = off
            }
        }
        return best
    }

    companion object {
        /** The smaller of the two ways round, in degrees. */
        fun separation(a: Float, b: Float): Float {
            val d = abs((a - b) % 360f)
            return if (d > 180f) 360f - d else d
        }
    }
}

enum class Bank { LEFT, RIGHT }

enum class Radius { NEAR, FAR }

data class Slot(val radius: Radius, val angle: Float)
