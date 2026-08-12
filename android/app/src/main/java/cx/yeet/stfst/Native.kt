package cx.yeet.stfst

/**
 * The compiled runtime. Strokes go in as one bit per key in steno order, and the
 * whole text comes back; the keyboard sends the editor whatever changed.
 */
object Native {
    init {
        System.loadLibrary("stfst")
    }

    /** Zero on failure, with the reason in [lastError]. */
    external fun open(dicts: String, system: String, morph: String): Long

    external fun push(handle: Long, keys: Long): String
    external fun undo(handle: Long): String
    external fun text(handle: Long): String

    /**
     * Briefer outlines for what was just written, one `text=OUTLINE` line per
     * trailing word or phrase. Empty when nothing saves strokes.
     */
    external fun hints(handle: Long): String

    /** The key symbols in steno order, one character each. */
    external fun order(handle: Long): String

    external fun reset(handle: Long)
    external fun close(handle: Long)
    external fun lastError(): String
}
