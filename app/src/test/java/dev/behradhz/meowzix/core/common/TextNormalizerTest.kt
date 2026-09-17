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
}
