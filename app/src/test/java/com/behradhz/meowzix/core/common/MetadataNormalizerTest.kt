package com.behradhz.meowzix.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataNormalizerTest {
    @Test
    fun display_trimsAndCollapsesWhitespace() {
        assertEquals("Artist Name", MetadataNormalizer.display("  Artist   Name  "))
    }

    @Test
    fun display_treatsAndroidUnknownValueAsMissing() {
        assertNull(MetadataNormalizer.display("<unknown>"))
    }

    @Test
    fun comparison_usesUnicodeCompatibilityNormalizationAndStableLowercase() {
        assertEquals("foo bar", MetadataNormalizer.comparison("  ＦＯＯ   BAR "))
    }

    @Test
    fun title_fallsBackToFilenameWithoutExtension() {
        assertEquals("My Song", MetadataNormalizer.title(null, "My Song.flac"))
    }

    @Test
    fun title_fallsBackToUnknownTrackWhenMetadataIsEmpty() {
        assertEquals("Unknown Track", MetadataNormalizer.title(" ", " "))
    }
}
