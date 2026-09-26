package cx.yeet.stfst

/**
 * A steno machine's protocol, from `stfst-machine`. The keyboard owns the USB
 * transport and the clock; this turns what arrives, and time passing, into
 * strokes, and says what to write back.
 *
 * A result is the cue's ordinal in [Cue] or -1, then the strokes, one bit per
 * key in steno order; null means the machine is lost, and [error] says why.
 */
object NativeMachine {
    init {
        System.loadLibrary("stfst")
    }

    const val HID = 0
    const val GEMINI = 1
    const val STENTURA = 2

    /** The hold's sounds, in the order a result numbers them. */
    enum class Cue { ARM, CANCEL, DISARM, SEND }

    /** A machine for `protocol`, configured by `plover.cfg`'s text, for a system whose keys are `names`. */
    external fun open(protocol: Int, cfg: String, names: String): Long
    external fun close(handle: Long)

    /** Bytes read at `now`, in milliseconds. For Plover HID, one report. */
    external fun feed(handle: Long, now: Long, data: ByteArray, length: Int): LongArray?

    /** Nothing arrived before [wake] ran out. */
    external fun idle(handle: Long, now: Long): LongArray?

    /** Milliseconds until [idle] has something to do, or -1 for none. */
    external fun wake(handle: Long, now: Long): Long

    /** Bytes to write to the machine now, taken once. */
    external fun outgoing(handle: Long, now: Long): ByteArray?

    /** A hold sound (0 arm, 1 disarm, 2 send) as 44.1 kHz mono PCM, or null if the machine does not hold. */
    external fun sound(handle: Long, which: Int): ShortArray?

    /** What `plover.cfg` said that could not be used, one per line. */
    external fun warnings(handle: Long): String
    external fun error(handle: Long): String

    /** Whether a HID report descriptor is a Plover HID machine's. */
    external fun declares(descriptor: ByteArray): Boolean

    /**
     * The serial machine and its port: protocol, baud rate, data bits, parity
     * as a character code, and stop bits times two. `protocol` of [GEMINI] or
     * [STENTURA] chooses; anything else follows `machine_type`.
     */
    external fun serial(cfg: String, protocol: Int): IntArray
}
