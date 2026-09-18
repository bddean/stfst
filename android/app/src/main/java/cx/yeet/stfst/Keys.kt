package cx.yeet.stfst

/**
 * The steno order as keys rather than as characters.
 *
 * The runtime numbers the keys by their place in the order, and a stroke is a
 * bit per number. One key is outside the basic plane, so a string index is not a
 * key number: `"🦐#@^+STKPWHRAO*EUfrpblgtsdz".indexOf('T')` is 7 and `T` is key
 * 6. Off by one from there on, and `T-` writes "can".
 */
class Keys(val symbols: List<String>) {

    val size: Int get() = symbols.size

    operator fun get(key: Int): String = symbols.getOrElse(key) { "" }

    fun indexOf(symbol: String): Int = symbols.indexOf(symbol)

    fun indexOf(symbol: Char): Int = symbols.indexOf(symbol.toString())

    /** The keys a stroke holds, in steno order. */
    fun spell(bits: Long): String =
        symbols.filterIndexed { key, _ -> (bits shr key) and 1L == 1L }.joinToString("")

    /** The same for keys already picked out, which is what a cell holds. */
    fun spell(keys: List<Int>): String = keys.joinToString("") { get(it) }

    companion object {
        fun of(order: String): Keys = Keys(split(order))

        /** One string per code point, because a key is a code point. */
        fun split(text: String): List<String> {
            val out = ArrayList<String>(text.length)
            var at = 0
            while (at < text.length) {
                val width = Character.charCount(text.codePointAt(at))
                out.add(text.substring(at, at + width))
                at += width
            }
            return out
        }
    }
}
