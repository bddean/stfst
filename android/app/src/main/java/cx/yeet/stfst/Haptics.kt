package cx.yeet.stfst

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The buzzes: what the motor says on each of the three occasions, in each
 * thumb's own voice.
 *
 * A voice is a name for one short thing a motor can do. Where the device has
 * composition primitives it is one of those; where it has none the same name
 * comes out as a pulse of the length, the strength and the count that voice
 * carries, because rhythm and amplitude are what survive when pitch does not.
 * So a layout of voices means something on every phone, and [voices] offers
 * only the ones this one can actually make.
 *
 * A phone has one motor, so a voice per thumb is not a left channel and a right
 * channel. It is two buzzes from the same motor, and the two have to differ in
 * something the motor has.
 *
 * [report] says what this device turned out to have, since that is a fact about
 * the hardware and guessing at it is what the touch readout exists to avoid.
 */
class Haptics(context: Context, private var config: Config) {

    private val vibrator: Vibrator?
    private val motors: List<Vibrator>
    private val amplitudes: Boolean

    /** The composition primitives this device answers, of the ones named here. */
    private val has: Set<Int>

    /** When the last buzz went out, so a run of them cannot become one long one. */
    private var last = 0L

    init {
        var one: Vibrator? = null
        var many: List<Vibrator> = emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            if (manager != null) {
                one = manager.defaultVibrator
                many = manager.vibratorIds.toList().mapNotNull { manager.getVibrator(it) }
            }
        } else {
            @Suppress("DEPRECATION")
            one = context.getSystemService(Vibrator::class.java)
        }
        vibrator = one?.takeIf { it.hasVibrator() }
        motors = many.filter { it.hasVibrator() }
        amplitudes = vibrator?.hasAmplitudeControl() == true
        has = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || vibrator == null) emptySet()
        else CATALOGUE.map { it.primitive }.filter { it != PLAIN }
            .filter { vibrator.areAllPrimitivesSupported(it) }
            .toSet()
    }

    fun apply(fresh: Config) {
        config = fresh
    }

    /** The key under the thumb changed, which is the buzz that matters. */
    fun tick(bank: Bank) {
        val now = SystemClock.uptimeMillis()
        if (now - last < config.hapticGapMs) return
        say(Buzz.TICK, bank)
    }

    /** A key was taken. */
    fun take(bank: Bank) = say(Buzz.TAKE, bank)

    /** A stroke went to the editor, which is the one worth feeling clearly. */
    fun stroke(bank: Bank) = say(Buzz.STROKE, bank)

    /** One voice, played on its own, for a settings screen to answer a tap with. */
    fun sample(name: String, power: Float, bank: Bank) {
        last = SystemClock.uptimeMillis()
        play(voiceOf(name), power, bank)
    }

    /**
     * The voices this device can make and this occasion has room for.
     *
     * A tick fires on every cell a thumb crosses, so it can only be given
     * something short; a stroke is made a few times a second at most, and can
     * take a rise or a spin. The order is the catalogue's, plainest first.
     */
    fun voices(on: Buzz): List<String> = CATALOGUE
        .filter { it.at.ordinal <= on.ordinal }
        .filter { it.primitive == PLAIN || it.primitive in has }
        .map { it.name }

    private fun say(on: Buzz, bank: Bank) {
        if (!config.buzzes(on)) return
        last = SystemClock.uptimeMillis()
        play(voiceOf(config.voice(on, bank)), config.power(on), bank)
    }

    /** The named voice, or the plainest one, which every motor can make. */
    private fun voiceOf(name: String): Voice =
        CATALOGUE.firstOrNull { it.name == name } ?: CATALOGUE.first()

    private fun play(voice: Voice, power: Float, bank: Bank) {
        val strength = (power * voice.weight).coerceIn(0.05f, 1f)
        // More than one motor is the only way a phone can put a buzz on a side,
        // so where there are two the thumb gets its own.
        val device = when {
            motors.size > 1 -> motors[if (bank == Bank.RIGHT) motors.size - 1 else 0]
            else -> vibrator ?: return
        }
        if (voice.primitive in has && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val composition = VibrationEffect.startComposition()
            repeat(voice.beats) { beat ->
                composition.addPrimitive(
                    voice.primitive, strength, if (beat == 0) 0 else BEAT.toInt()
                )
            }
            device.vibrate(composition.compose())
            return
        }
        val amplitude = (strength * 255).toInt().coerceIn(1, 255)
        val timing = ArrayList<Long>()
        repeat(voice.beats) { beat ->
            timing.add(if (beat == 0) 0L else BEAT)
            timing.add(voice.millis)
        }
        device.vibrate(
            if (amplitudes) {
                val levels = timing.indices.map { if (it % 2 == 0) 0 else amplitude }.toIntArray()
                VibrationEffect.createWaveform(timing.toLongArray(), levels, -1)
            } else VibrationEffect.createWaveform(timing.toLongArray(), -1)
        )
    }

    /** What this device can do, for the settings screen to print. */
    fun report(): String {
        if (vibrator == null) return "no vibrator on this device"
        val parts = listOfNotNull(
            "${motors.size} motors, one per thumb".takeIf { motors.size > 1 },
            "amplitude control".takeIf { amplitudes },
            "${has.size} of the ${CATALOGUE.count { it.primitive != PLAIN }} primitives"
                .takeIf { has.isNotEmpty() },
        )
        return if (parts.isEmpty()) "one motor, and nothing to vary but rhythm"
        else parts.joinToString(", ")
    }

    /**
     * One buzz: what to ask the motor for, and what it comes to on a motor that
     * cannot be asked.
     *
     * `at` is the shortest occasion the voice suits, which is what keeps a
     * 130ms spin off the buzz that fires on every cell.
     */
    private class Voice(
        val name: String,
        val primitive: Int,
        val at: Buzz,
        /** How long one beat of the plain pulse is. */
        val millis: Long = 16L,
        /** What the voice does to the strength asked for. */
        val weight: Float = 1f,
        /** How many beats: the one difference a bare motor can carry. */
        val beats: Int = 1,
    )

    private companion object {
        /** No primitive: a plain pulse, which every motor can make. */
        const val PLAIN = -1

        /** How long between the beats of a voice that has more than one. */
        const val BEAT = 45L

        val CATALOGUE = listOf(
            Voice("pulse", PLAIN, Buzz.TICK),
            Voice("soft pulse", PLAIN, Buzz.TICK, weight = 0.45f),
            Voice("low tick", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            }, Buzz.TICK, weight = 0.8f),
            Voice("tick", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_TICK
            }, Buzz.TICK),
            Voice("click", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_CLICK
            }, Buzz.TICK, millis = 20L),
            Voice("two pulses", PLAIN, Buzz.TAKE, millis = 14L, beats = 2),
            Voice("two ticks", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_TICK
            }, Buzz.TAKE, millis = 14L, beats = 2),
            Voice("rise", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
            }, Buzz.STROKE, millis = 40L),
            Voice("fall", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
            }, Buzz.STROKE, millis = 40L),
            Voice("spin", primitive(Build.VERSION_CODES.R) {
                VibrationEffect.Composition.PRIMITIVE_SPIN
            }, Buzz.STROKE, millis = 60L),
        )

        /**
         * A primitive's number where the platform has it, and [PLAIN] where the
         * constant itself is newer than this phone.
         */
        inline fun primitive(since: Int, id: () -> Int): Int =
            if (Build.VERSION.SDK_INT >= since) id() else PLAIN
    }
}
