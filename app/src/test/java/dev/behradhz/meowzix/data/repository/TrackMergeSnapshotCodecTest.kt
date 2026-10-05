package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMergeSnapshotCodecTest {
    @Test
    fun roundTripPreservesMergeProvenanceAndEscapedMetadata() {
        val survivor = UUID.randomUUID()
        val merged = UUID.randomUUID()
        val snapshot = TrackMergeSnapshot(
            survivorFavorite = true,
            survivorHidden = false,
            mergedFavorite = false,
            mergedHidden = false,
            survivorOverride = TrackMetadataOverrideEntity(
                trackId = survivor.toString(),
                title = "A\tTitle%",
                artist = "Line\nArtist",
                album = "~",
                artworkRef = "content://art/1",
                updatedAtEpochMs = 12L,
            ),
            sourceIds = listOf("source-1", "source-2"),
            playlistEntries = listOf(
                PlaylistSnapshotEntry(PlaylistTrackEntity("playlist", merged.toString(), 4, 99L), true),
            ),
            lyricIds = listOf("lyric-1"),
            historyEventIds = listOf("event-1", "event-2"),
            survivorFeedback = listOf(
                RecommendationFeedback(
                    trackId = survivor,
                    action = RecommendationFeedbackAction.MORE_LIKE_THIS,
                    createdAt = Instant.ofEpochMilli(100L),
                ),
            ),
            mergedFeedback = listOf(
                RecommendationFeedback(
                    trackId = merged,
                    action = RecommendationFeedbackAction.SNOOZE,
                    createdAt = Instant.ofEpochMilli(200L),
                    expiresAt = Instant.ofEpochMilli(500L),
                ),
            ),
        )

        assertEquals(snapshot, TrackMergeSnapshotCodec.decode(TrackMergeSnapshotCodec.encode(snapshot)))
    }
}