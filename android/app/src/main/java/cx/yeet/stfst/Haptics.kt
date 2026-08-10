package cx.yeet.stfst

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The buzzes, and which of them this device can actually tell apart.
 *
 * A phone has one vibration motor, so there is no left channel and no right
 * channel and no frequency to set. What there is, in the order this asks for it:
 *
 * 1. more than one motor, on the few devices that have them, addressed separately
 * 2. composition primitives, where a low tick and a sharp tick feel like two
 *    different things from the same motor
 * 3. amplitude control, so weak against strong
 * 4. neither, so one pulse against two, because rhythm always survives
 *
 * [report] says which one happened, since the answer is a fact about the hardware
 * and guessing at it is what the touch readout exists to avoid.
 */
class Haptics(context: Context, private var config: Config) {

    private val vibrator: Vibrator?
    private val motors: List<Vibrator>
    private val primitives: Boolean
    private val amplitudes: Boolean

    /** What the last tick was, so a run of them cannot become one long buzz. */
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
        primitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_TICK,
                VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            )
    }

    fun apply(fresh: Config) {
        config = fresh
    }

    /** The key under the thumb changed. The lightest thing the motor can do. */
    fun tick(bank: Bank) {
        if (!config.haptics || !config.hapticTicks) return
        val now = SystemClock.uptimeMillis()
        if (now - last < config.hapticGapMs) return
        last = now
        play(bank, 0.55f, 12L)
    }

    /** A key was taken. */
    fun take(bank: Bank) {
        if (!config.haptics) return
        last = SystemClock.uptimeMillis()
        play(bank, 1f, 18L)
    }

    /** A stroke went to the editor, which is the one worth feeling clearly. */
    fun stroke(bank: Bank) {
        if (!config.haptics) return
        last = SystemClock.uptimeMillis()
        play(bank, 1f, 28L)
    }

    /**
     * One buzz, in this bank's voice.
     *
     * `weight` scales the strength within an event, and the bank chooses the voice.
     * The right thumb gets the sharper or stronger of each pair, so a buzz says
     * which hand it belongs to without saying anything else.
     */
    private fun play(bank: Bank, weight: Float, millis: Long) {
        val split = config.hapticSplit
        val right = bank == Bank.RIGHT
        val strength = (config.hapticStrength * weight).coerceIn(0.05f, 1f)
        val amplitude = (strength * 255).toInt().coerceIn(1, 255)

        if (split && motors.size > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val id = if (right) motors.size - 1 else 0
            play(motors[id], amplitude, millis)
            return
        }
        val device = vibrator ?: return
        if (split && primitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val primitive = if (right) VibrationEffect.Composition.PRIMITIVE_TICK
            else VibrationEffect.Composition.PRIMITIVE_LOW_TICK
            device.vibrate(
                VibrationEffect.startComposition().addPrimitive(primitive, strength).compose()
            )
            return
        }
        if (split && amplitudes) {
            val quiet = (amplitude * 0.45f).toInt().coerceAtLeast(1)
            play(device, if (right) amplitude else quiet, millis)
            return
        }
        if (split) {
            // No voice to choose, so the difference goes into the rhythm.
            val pattern = if (right) longArrayOf(0L, millis, millis, millis) else longArrayOf(0L, millis)
            @Suppress("DEPRECATION")
            device.vibrate(VibrationEffect.createWaveform(pattern, -1))
            return
        }
        play(device, amplitude, millis)
    }

    private fun play(device: Vibrator, amplitude: Int, millis: Long) {
        device.vibrate(
            VibrationEffect.createOneShot(
                millis, if (amplitudes) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            )
        )
    }

    /** What this device can do, for the settings screen to print. */
    fun report(): String {
        if (vibrator == null) return "no vibrator on this device"
        val voice = when {
            motors.size > 1 -> "${motors.size} motors, one per thumb"
            primitives -> "one motor, a low tick for the left thumb and a sharp one for the right"
            amplitudes -> "one motor, weak for the left thumb and strong for the right"
            else -> "one motor, one pulse for the left thumb and two for the right"
        }
        val extras = listOfNotNull(
            "amplitude control".takeIf { amplitudes },
            "composition primitives".takeIf { primitives },
        )
        return voice + if (extras.isEmpty()) ". No amplitude control and no primitives"
        else ". Has ${extras.joinToString(" and ")}"
    }
}
