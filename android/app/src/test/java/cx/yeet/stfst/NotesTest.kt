package cx.yeet.stfst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The record of what the keyboard has done, and how much of it fits. */
class NotesTest {

    @Test
    fun a_stroke_reads_as_its_outline_and_what_it_wrote() {
        val notes = Notes()
        notes.stroke("KWHEUf", " keep the")
        assertEquals("KWHEUf   keep the", notes.log(10))
    }

    /** A brief is about the stroke that provoked it, so it hangs under one. */
    @Test
    fun a_brief_lands_under_its_own_stroke() {
        val notes = Notes()
        notes.stroke("STK", " said")
        notes.stroke("TPHR", " the")
        notes.brief(listOf("said the=STKPHR"))
        assertEquals(
            listOf("STK   said", "TPHR   the", "  ↳ said the=STKPHR"),
            notes.log(10).lines(),
        )
    }

    /** The same brief twice is not news the second time. */
    @Test
    fun a_brief_already_offered_is_not_offered_again() {
        val notes = Notes()
        notes.stroke("TPHR", " the")
        assertTrue(notes.brief(listOf("the=-T")))
        notes.stroke("TPHR", " the")
        assertTrue("a repeat should add nothing", !notes.brief(listOf("the=-T")))
        assertEquals(1, notes.log(10).lines().count { it.startsWith("  ↳") })
    }

    /** The panel shows what it has room for, newest last. */
    @Test
    fun the_log_keeps_the_newest_lines() {
        val notes = Notes()
        for (i in 1..10) notes.stroke("S$i", "")
        assertEquals(listOf("S9", "S10"), notes.log(2).lines())
        assertEquals("", notes.log(0))
    }

    /** One line of room goes to the briefs: a stroke just made is not news. */
    @Test
    fun one_line_of_room_shows_the_briefs() {
        val notes = Notes()
        notes.stroke("TPHR", " the")
        notes.brief(listOf("the=-T"))
        notes.stroke("KWH", " keep")
        notes.brief(listOf("keep=KP"))
        assertEquals("the=-T   keep=KP", notes.briefs(8))
        assertEquals("keep=KP", notes.briefs(1))
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
        val notes = Notes(3)
        for (i in 1..6) notes.stroke("S$i", "")
        assertEquals(listOf("S4", "S5", "S6"), notes.log(99).lines())
    }
}
