package dev.behradhz.meowzix.feature.home

import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.ArtistSummary
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeArtistCardsTest {
    @Test
    fun artistKeysPreserveFirstAppearanceAndIgnoreDuplicatesOrMissingArtists() {
        val tracks = listOf(
            track("First", "  Alpha  ", "alpha"),
            track("Second", "Alpha", "alpha"),
            track("Third", null, null),
            track("Fourth", "Beta", "beta"),
        )

        assertEquals(listOf("alpha" to "Alpha", "beta" to "Beta"), homeArtistKeys(tracks))
    }

    @Test
    fun artistKeysAreBoundedToEight() {
        val tracks = (1..15).map { track("Song $it", "Artist $it", "artist $it") }

        assertEquals(8, homeArtistKeys(tracks).size)
        assertEquals("artist 8", homeArtistKeys(tracks).last().first)
    }

    @Test
    fun cardsUseSummariesWithoutHydratingTrackLists() {
        val keys = listOf("alpha" to "Alpha", "beta" to "Beta")
        val summaries = listOf(
            ArtistSummary("Other display name", "beta", 8, "content://beta"),
            ArtistSummary("Alpha", "alpha", 120, "content://alpha"),
        )

        val cards = homeArtistCards(keys, summaries)

        assertEquals(listOf("Alpha", "Beta"), cards.map { it.name })
        assertEquals(listOf("alpha", "beta"), cards.map { it.normalizedName })
        assertEquals(listOf(120, 8), cards.map { it.trackCount })
        assertEquals(listOf("content://alpha", "content://beta"), cards.map { it.artworkRef })
    }

    @Test
    fun cardsExcludeUnavailableArtists() {
        val cards = homeArtistCards(
            listOf("missing" to "Missing", "present" to "Present"),
            listOf(ArtistSummary("Present", "present", 1, null)),
        )
        assertEquals(listOf("Present"), cards.map { it.name })
    }

    private fun track(title: String, artist: String?, normalizedArtist: String?) = Track(
        id = UUID.randomUUID(),
        title = title,
        normalizedTitle = title.lowercase(),
        artist = artist,
        normalizedArtist = normalizedArtist,
        album = null,
        durationMs = 120_000L,
        trackNumber = null,
        year = null,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
