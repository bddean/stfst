package cx.yeet.stfst

/**
 * What the keyboard has just done, newest last.
 *
 * Two facts arrive per stroke, and at different times. The outline and the text
 * it wrote arrive together. A briefer outline for that same text arrives later,
 * once the reverse lookups come back off the stroke's thread. The second is
 * about the first, so a brief attaches to the stroke that provoked it rather
 * than living in a bar of its own.
 *
 * How much of this is worth showing is a question about room, so it is asked at
 * the point of rendering: one line of it is the briefs, because a stroke you
 * just made is not news and a shorter way to make it is.
 */
class Notes(private val keep: Int = 60) {

    class Stroke(val outline: String, val wrote: String) {
        val briefs: MutableList<String> = mutableListOf()
    }

    private val strokes = ArrayDeque<Stroke>()

    /** Briefs already offered. The same one twice is not news the second time. */
    private val told = LinkedHashSet<String>()

    val empty: Boolean get() = strokes.isEmpty()

    fun stroke(outline: String, wrote: String) {
        strokes.addLast(Stroke(outline, wrote))
        while (strokes.size > keep) strokes.removeFirst()
    }

    /** Hang briefs on the newest stroke. True when any of them were new. */
    fun brief(found: List<String>): Boolean {
        val last = strokes.lastOrNull() ?: return false
        val fresh = found.filter { it.isNotBlank() && told.add(it) }
        last.briefs.addAll(fresh)
        while (told.size > keep) told.remove(told.first())
        return fresh.isNotEmpty()
    }

    fun clear() {
        strokes.clear()
        told.clear()
    }

    /** The briefs alone, newest last: what one line of room is worth spending on. */
    fun briefs(most: Int): String =
        strokes.flatMap { it.briefs }.takeLast(most).joinToString("   ")

    /** The whole record, a line per stroke and a line per brief under it. */
    fun log(rows: Int): String {
        if (rows <= 0) return ""
        val lines = strokes.flatMap { stroke ->
            val wrote = stroke.wrote.replace("\n", "⏎")
            listOf(if (wrote.isEmpty()) stroke.outline else "${stroke.outline}  $wrote") +
                stroke.briefs.map { "  ↳ $it" }
        }
        return lines.takeLast(rows).joinToString("\n")
    }
}
