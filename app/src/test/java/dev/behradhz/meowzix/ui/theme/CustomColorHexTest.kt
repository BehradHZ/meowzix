package dev.behradhz.meowzix.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import org.junit.Test

class CustomColorHexTest {
    @Test fun logoUsesExactRgbWithoutTonalFiltering() {
        val custom = 0xFF123ABC.toInt()
        assertEquals(0xFFE5948A.toInt(), ColorThemePreference.MEOWZIX.logoBackgroundArgb(custom))
        assertEquals(0xFF187CB9.toInt(), ColorThemePreference.OCEAN.logoBackgroundArgb(custom))
        assertEquals(custom, ColorThemePreference.CUSTOM.logoBackgroundArgb(custom))
    }

    @Test fun acceptsOnlyOpaqueSixDigitRgb() {
        assertEquals(0xFFFFB38A.toInt(), parseCustomColorHex("#ffb38a"))
        assertEquals(0xFF123ABC.toInt(), parseCustomColorHex(" 123AbC "))
        assertNull(parseCustomColorHex("#FFF"))
        assertNull(parseCustomColorHex("#80FFB38A"))
        assertNull(parseCustomColorHex("not-a-color"))
        assertNull(parseCustomColorHex(""))
    }
}
