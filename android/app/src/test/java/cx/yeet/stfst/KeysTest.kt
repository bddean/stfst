package cx.yeet.stfst

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The keyboard numbers the keys the way the runtime does, or every stroke means
 * something else.
 *
 * The order is read from the system file the app ships, so a key added there is
 * a key these tests see.
 */
class KeysTest {

    private val order: String = SYSTEM.readLines()
        .first { it.trimStart().startsWith("order") }
        .substringAfter('"').substringBefore('"')

    private val keys = Keys.of(order)

    @Test
    fun a_key_is_a_code_point_and_not_a_character() {
        assertEquals(order.codePointCount(0, order.length), keys.size)
        for (key in 0 until keys.size) {
            assertEquals(key, keys.indexOf(keys[key]))
        }
    }

    @Test
    fun a_key_outside_the_basic_plane_does_not_shift_the_keys_after_it() {
        // `🦐` is two characters and one key, so a string index is one too many
        // from there on. It used to be, and `T-` wrote "can".
        val shrimp = keys.symbols.indexOfFirst { it.length > 1 }
        if (shrimp < 0) return
        val after = keys[shrimp + 1]
        assertEquals(shrimp + 1, keys.indexOf(after))
        assertNotEquals(order.indexOf(after), keys.indexOf(after))
    }

    @Test
    fun a_stroke_spells_the_keys_it_holds_in_order() {
        val bits = (1L shl keys.indexOf('T')) or (1L shl keys.indexOf('R'))
        assertEquals("TR", keys.spell(bits))
    }

    companion object {
        private val SYSTEM = File("../../systems/stenotype-extended.toml")
    }
}
