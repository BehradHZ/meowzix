package dev.behradhz.meowzix.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyTrackSearchTest {
    @Test fun `accepts one typo in a normal length title token`() {
        assertEquals(4, FuzzyTrackSearch.score("radiahead", "radiohead", null, null))
    }

    @Test fun `accepts two typos only for long tokens`() {
        assertTrue(FuzzyTrackSearch.score("interstllar", "interstellar", null, null) != null)
        assertNull(FuzzyTrackSearch.score("abcd", "abxy", null, null))
    }

    @Test fun `normalizes Persian variants before fuzzy comparison`() {
        assertEquals(0, FuzzyTrackSearch.score("موسيقی", "موسیقی", null, null))
    }

    @Test fun `short queries never enter fuzzy matching`() {
        assertNull(FuzzyTrackSearch.score("abc", "abd", null, null))
    }

    @Test fun `title evidence ranks ahead of artist then album`() {
        val title = FuzzyTrackSearch.score("radihead", "radiohead", null, null)!!
        val artist = FuzzyTrackSearch.score("radihead", "song", "radiohead", null)!!
        val album = FuzzyTrackSearch.score("radihead", "song", null, "radiohead")!!
        assertTrue(title < artist && artist < album)
    }
}
