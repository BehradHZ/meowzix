package dev.behradhz.meowzix.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextNormalizerTest {
    @Test fun `normalizes whitespace and case`() {
        assertEquals("linkin park", TextNormalizer.normalize("  LINKIN   PARK  "))
    }

    @Test fun `blank values become null`() {
        assertNull(TextNormalizer.normalize("   "))
    }

    @Test fun `normalizes Arabic and Persian letter variants`() {
        assertEquals("کی ی", TextNormalizer.normalize("كی ي"))
        assertEquals("علی", TextNormalizer.normalize("علي"))
    }

    @Test fun `normalizes half space diacritics and tatweel`() {
        assertEquals("می روم", TextNormalizer.normalize("مـی‌ رَوم"))
    }

    @Test fun `normalizes Persian and Arabic digits`() {
        assertEquals("track 123 456", TextNormalizer.normalize("Track ۱۲۳ ٤٥٦"))
    }

    @Test fun `normalizes compatibility unicode`() {
        assertEquals("hello 1", TextNormalizer.normalize("ＨＥＬＬＯ １"))
    }
}
