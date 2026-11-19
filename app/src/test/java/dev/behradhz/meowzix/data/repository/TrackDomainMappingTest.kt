package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.db.TrackEntity
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDomainMappingTest {
    @Test
    fun mapsEveryTrackFieldWithoutChangingSemantics() {
        val id = UUID.randomUUID()
        val source = TrackEntity(
            id = id.toString(),
            title = "Song",
            normalizedTitle = "song",
            artist = "Artist",
            normalizedArtist = "artist",
            album = "Album",
            durationMs = 205_000L,
            trackNumber = 7,
            year = 2024,
            artworkRef = "content://artwork",
            favorite = true,
            hidden = false,
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_001_000L,
            addedToLibrary = true,
        )

        val result = source.toDomain()

        assertEquals(id, result.id)
        assertEquals("Song", result.title)
        assertEquals("song", result.normalizedTitle)
        assertEquals("Artist", result.artist)
        assertEquals("artist", result.normalizedArtist)
        assertEquals("Album", result.album)
        assertEquals(205_000L, result.durationMs)
        assertEquals(7, result.trackNumber)
        assertEquals(2024, result.year)
        assertEquals("content://artwork", result.artworkRef)
        assertTrue(result.favorite)
        assertFalse(result.hidden)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), result.createdAt)
        assertEquals(Instant.ofEpochMilli(1_700_000_001_000L), result.updatedAt)
    }
}
