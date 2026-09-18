package cx.yeet.stfst

/**
 * The compiled runtime. Strokes go in as one bit per key in steno order, and the
 * whole text comes back; the keyboard sends the editor whatever changed.
 */
object Native {
    init {
        System.loadLibrary("stfst")
    }

    /**
     * Zero on failure, with the reason in [lastError]. `editable` is a directory
     * of JSON dictionaries this keyboard can write to, as the `editable.toml`
     * there lists them.
     */
    external fun open(dicts: String, editable: String, system: String, morph: String): Long

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

    /** The last `strokes` strokes, as the outline they are written as. */
    external fun tail(handle: Long, strokes: Long): String

    /** How many strokes the last match consumed. */
    external fun unit(handle: Long): Long

    /**
     * A command a dictionary asked the keyboard for, as `name<TAB>argument`,
     * taken once. Empty when there is none.
     */
    external fun command(handle: Long): String

    /**
     * Write into a second buffer, and back again. Defining a translation types it
     * in steno, and the board is the only thing that knows what a stroke writes.
     */
    external fun scratch(handle: Long, on: Long): String

    /** Start the buffer again from text already written, without stroking it. */
    external fun seed(handle: Long, text: String): String

    /** What an editable dictionary says this outline writes, or empty. */
    external fun defined(handle: Long, dict: String, outline: String): String

    /**
     * Define an outline and make the last `back` strokes again, so what they
     * wrote becomes what they now mean. An empty translation deletes the entry,
     * and `back` of zero leaves the text alone. Returns the reason it did not
     * happen, or empty.
     */
    external fun define(
        handle: Long, dict: String, outline: String, translation: String, back: Long
    ): String

    external fun reset(handle: Long)
    external fun close(handle: Long)
    external fun lastError(): String
}
