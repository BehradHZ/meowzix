package dev.behradhz.meowzix.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryRepositoryTest {
    @Test
    fun englishPrefixQueryUsesAllTokens() {
        assertEquals(
            "\"radio\"* AND \"head\"*",
            buildTrackFtsMatchExpression("radio head"),
        )
    }

    @Test
    fun persianPrefixQueryPreservesUnicodeTokens() {
        assertEquals(
            "\"موسی\"* AND \"شب\"*",
            buildTrackFtsMatchExpression("موسی شب"),
        )
    }

    @Test
    fun repeatedWhitespaceDoesNotProduceEmptyTerms() {
        assertEquals(
            "\"one\"* AND \"two\"*",
            buildTrackFtsMatchExpression("one   two"),
        )
    }
}
