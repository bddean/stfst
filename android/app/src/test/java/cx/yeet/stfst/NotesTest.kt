package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The record of what the keyboard has done, and the briefs it offers from it. */
class NotesTest {

    /** The line goes to the briefs: a stroke just made is not news. */
    @Test
    fun the_line_shows_the_briefs() {
        val notes = Notes()
        notes.stroke("TPHR", " the")
        notes.brief(listOf("the=-T"))
        notes.stroke("KWH", " keep")
        notes.brief(listOf("keep=KP"))
        assertEquals("the=-T   keep=KP", notes.briefs(8))
        assertEquals("keep=KP", notes.briefs(1))
    }

    /** The same brief twice is not news the second time. */
    @Test
    fun a_brief_already_offered_is_not_offered_again() {
        val notes = Notes()
        notes.stroke("TPHR", " the")
        assertTrue(notes.brief(listOf("the=-T")))
        notes.stroke("TPHR", " the")
        assertTrue("a repeat should add nothing", !notes.brief(listOf("the=-T")))
        assertEquals("the=-T", notes.briefs(8))
    }

    /** A brief with no stroke to hang on is dropped, not stored loose. */
    @Test
    fun a_brief_before_any_stroke_is_dropped() {
        val notes = Notes()
        assertTrue(!notes.brief(listOf("the=-T")))
        assertEquals("", notes.briefs(8))
        assertTrue(notes.empty)
    }

    /** Only what is old drops out: the record is bounded. */
    @Test
    fun the_record_is_bounded() {
        val notes = Notes(2)
        for (i in 1..4) {
            notes.stroke("S$i", "")
            notes.brief(listOf("$i=S$i"))
        }
        assertEquals("3=S3   4=S4", notes.briefs(9))
    }
}
