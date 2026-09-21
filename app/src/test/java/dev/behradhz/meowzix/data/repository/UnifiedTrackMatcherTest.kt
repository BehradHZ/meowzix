package dev.behradhz.meowzix.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnifiedTrackMatcherTest {
    @Test
    fun `exact hash wins despite different metadata`() {
        val result = UnifiedTrackMatcher.match(
            incoming = IncomingTrackIdentity("other", "artist", 1L, "abc"),
            candidates = listOf(candidate("track-1", hashes = setOf("abc"))),
        )

        assertEquals("track-1", result)
    }

    @Test
    fun `matching title artist and duration merges provisionally`() {
        val result = UnifiedTrackMatcher.match(
            incoming = IncomingTrackIdentity("numb", "linkin park", 185_500L),
            candidates = listOf(candidate("track-1")),
        )

        assertEquals("track-1", result)
    }

    @Test
    fun `same title alone never merges`() {
        val result = UnifiedTrackMatcher.match(
            incoming = IncomingTrackIdentity("home", "artist two", 185_000L),
            candidates = listOf(candidate("track-1", artist = "artist one")),
        )

        assertNull(result)
    }

    @Test
    fun `ambiguous metadata match stays separate`() {
        val incoming = IncomingTrackIdentity("numb", "linkin park", 185_000L)

        assertNull(UnifiedTrackMatcher.match(incoming, listOf(candidate("one"), candidate("two"))))
    }

    private fun candidate(
        id: String,
        artist: String = "linkin park",
        hashes: Set<String> = emptySet(),
    ) = TrackMatchCandidate(id, "numb", artist, 185_000L, hashes)
}
