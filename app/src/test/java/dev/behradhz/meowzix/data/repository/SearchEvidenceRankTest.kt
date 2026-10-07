package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.Track
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEvidenceRankTest {
    @Test
    fun exactTitleOutranksPrefix() {
        val exact = track(title = "Numb")
        val prefix = track(title = "Numb live")
        assertTrue(searchEvidenceRank("numb", exact) < searchEvidenceRank("numb", prefix))
    }

    @Test
    fun titleAndArtistCompoundEvidenceOutranksAlbumOnlyEvidence() {
        val titleArtist = track(title = "Numb", artist = "Linkin Park")
        val albumOnly = track(title = "Other", album = "Numb Linkin")
        assertTrue(
            searchEvidenceRank("numb linkin", titleArtist) <
                searchEvidenceRank("numb linkin", albumOnly),
        )
    }

    @Test
    fun titleEvidenceOutranksWeakArtistAndAlbumEvidence() {
        val title = track(title = "Radiohead")
        val artist = track(title = "Other", artist = "Radiohead")
        val album = track(title = "Other", album = "Radiohead")
        val titleRank = searchEvidenceRank("radiohead", title)
        assertTrue(titleRank < searchEvidenceRank("radiohead", artist))
        assertTrue(searchEvidenceRank("radiohead", artist) < searchEvidenceRank("radiohead", album))
    }

    @Test
    fun persianAlbumUsesSharedNormalizer() {
        val album = track(title = "Other", album = "موسیقی")
        assertTrue(searchEvidenceRank("موسيقي", album) < Int.MAX_VALUE)
    }

    private fun track(
        title: String,
        artist: String? = null,
        album: String? = null,
    ) = Track(
        id = UUID.randomUUID(),
        title = title,
        normalizedTitle = title.lowercase(),
        artist = artist,
        normalizedArtist = artist?.lowercase(),
        album = album,
        durationMs = 100_000L,
        trackNumber = null,
        year = null,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
