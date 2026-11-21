package dev.behradhz.meowzix.feature.telegramauth

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramDialCountriesTest {
    private val countries = telegramDialCountries(Locale.ENGLISH)

    @Test fun `country list includes shared calling codes and localized names`() {
        assertEquals("+1", countries.first { it.region == "US" }.dialCode)
        assertEquals("+1", countries.first { it.region == "CA" }.dialCode)
        assertEquals("+98", countries.first { it.region == "IR" }.dialCode)
        assertTrue(telegramDialCountries(Locale.forLanguageTag("fa-IR"))
            .first { it.region == "IR" }.name.isNotBlank())
    }

    @Test fun `search by dial code or country name or region ignoring case`() {
        assertTrue(searchTelegramCountries(countries, "+98").any { it.region == "IR" })
        assertTrue(searchTelegramCountries(countries, "98").any { it.region == "IR" })
        assertTrue(searchTelegramCountries(countries, "iran").any { it.region == "IR" })
        assertTrue(searchTelegramCountries(countries, "IR").any { it.region == "IR" })
        assertFalse(searchTelegramCountries(countries, "nonexistentland").isNotEmpty())
    }

    @Test fun `national phone uses selected country without duplicate prefix`() {
        val iran = countries.first { it.region == "IR" }
        val germany = countries.first { it.region == "DE" }
        assertEquals("+989123456789", telegramInternationalPhone(iran, "۰۹۱۲ ۳۴۵ ۶۷۸۹"))
        assertEquals("+989123456789", telegramInternationalPhone(iran, "9123456789"))
        assertEquals("+4915112345678", telegramInternationalPhone(germany, "0151 12345678"))
        assertNull(telegramInternationalPhone(iran, "+989123456789"))
        assertNull(telegramInternationalPhone(iran, "123"))
    }
}
