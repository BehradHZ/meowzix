package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramPhoneNumberNormalizerTest {
    @Test
    fun `converts domestic Iranian mobile numbers to international format`() {
        assertEquals("+989123456789", normalizeTelegramPhoneNumber("09123456789"))
        assertEquals("+989123456789", normalizeTelegramPhoneNumber(" 0912-345-6789 "))
        assertEquals("+989123456789", normalizeTelegramPhoneNumber("۰۹۱۲۳۴۵۶۷۸۹"))
        assertEquals("+989123456789", normalizeTelegramPhoneNumber("٠٩١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun `preserves international and incomplete numbers`() {
        assertEquals("+989123456789", normalizeTelegramPhoneNumber("+989123456789"))
        assertEquals("989123456789", normalizeTelegramPhoneNumber("989123456789"))
        assertEquals("+4912345678", normalizeTelegramPhoneNumber("+4912345678"))
        assertEquals("0912345678", normalizeTelegramPhoneNumber("0912345678"))
        assertEquals("091234567890", normalizeTelegramPhoneNumber("091234567890"))
        assertEquals("", normalizeTelegramPhoneNumber(""))
    }
}
