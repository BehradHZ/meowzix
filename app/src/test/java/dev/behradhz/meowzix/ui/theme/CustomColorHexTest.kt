package dev.behradhz.meowzix.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomColorHexTest {
    @Test fun acceptsOnlyOpaqueSixDigitRgb() {
        assertEquals(0xFFFFB38A.toInt(), parseCustomColorHex("#ffb38a"))
        assertEquals(0xFF123ABC.toInt(), parseCustomColorHex(" 123AbC "))
        assertNull(parseCustomColorHex("#FFF"))
        assertNull(parseCustomColorHex("#80FFB38A"))
        assertNull(parseCustomColorHex("not-a-color"))
        assertNull(parseCustomColorHex(""))
    }
}
