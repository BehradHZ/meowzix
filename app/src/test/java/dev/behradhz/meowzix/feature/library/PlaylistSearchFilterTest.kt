package dev.behradhz.meowzix.feature.library

import dev.behradhz.meowzix.core.model.Track
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSearchFilterTest {
    private fun track(title: String, artist: String?, album: String?) = Track(
        id = UUID.randomUUID(),
        title = title,
        normalizedTitle = title.lowercase(),
        artist = artist,
        normalizedArtist = artist?.lowercase(),
        album = album,
        durationMs = 120_000,
        trackNumber = null,
        year = null,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test fun matchesOnlyTracksFromTheGivenPlaylistAndKeepsTheirOrder() {
        val first = track("Midnight City", "M83", "Hurry Up, We're Dreaming")
        val second = track("Midnight", "Coldplay", "Ghost Stories")
        val third = track("Intro", "M83", "Hurry Up, We're Dreaming")
        val playlist = listOf(first, second, third)

        assertEquals(listOf(first, third), filterPlaylistTracks(playlist, "m83"))
        assertEquals(listOf(second), filterPlaylistTracks(playlist, "midnight coldplay"))
        assertEquals(listOf(first, second, third), playlist)
    }

    @Test fun blankRestoresAllAndUnknownQueryShowsNoResults() {
        val song = track("Song", null, "Album")
        assertEquals(listOf(song), filterPlaylistTracks(listOf(song), "   "))
        assertEquals(emptyList<Track>(), filterPlaylistTracks(listOf(song), "not present"))
        assertEquals(listOf(song), filterPlaylistTracks(listOf(song), "album"))
    }
}
