package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.db.PlaylistTrackEntity
import dev.behradhz.meowzix.data.db.TrackMetadataOverrideEntity
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedback
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
                title = "A\tTitle%\rعنوان",
                artist = "Line\nArtist",
                album = "~",
                artworkRef = "content://art/%7E/1",
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

    @Test
    fun nullAndLiteralLegacySentinelRoundTripDistinctly() {
        val trackId = UUID.randomUUID().toString()
        val snapshot = emptySnapshot(
            TrackMetadataOverrideEntity(
                trackId = trackId,
                title = null,
                artist = "~",
                album = "",
                artworkRef = null,
                updatedAtEpochMs = 7L,
            ),
        )

        val decoded = TrackMergeSnapshotCodec.decode(TrackMergeSnapshotCodec.encode(snapshot))

        assertEquals(null, decoded.survivorOverride?.title)
        assertEquals("~", decoded.survivorOverride?.artist)
        assertEquals("", decoded.survivorOverride?.album)
        assertEquals(null, decoded.survivorOverride?.artworkRef)
    }

    @Test
    fun legacyV1JournalRemainsDecodable() {
        val trackId = UUID.randomUUID()
        val raw = buildString {
            append("v\t1\n")
            append("flags\ttrue\tfalse\tfalse\tfalse\n")
            append("override\t").append(trackId).append("\tLegacy%09Title\t~\tAlbum\t~\t12\n")
        }

        val decoded = TrackMergeSnapshotCodec.decode(raw)

        assertEquals(true, decoded.survivorFavorite)
        assertEquals("Legacy\tTitle", decoded.survivorOverride?.title)
        assertEquals(null, decoded.survivorOverride?.artist)
        assertEquals("Album", decoded.survivorOverride?.album)
    }

    @Test
    fun malformedJournalFailsInsteadOfProducingPartialSnapshot() {
        assertThrows(IllegalArgumentException::class.java) {
            TrackMergeSnapshotCodec.decode("v\t2\nflags\ttrue\tfalse\tfalse\tfalse\nsource\tbad%ZZescape\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrackMergeSnapshotCodec.decode("v\t2\nsource\tmissing-flags\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TrackMergeSnapshotCodec.decode("v\t99\nflags\ttrue\tfalse\tfalse\tfalse\n")
        }
    }

    private fun emptySnapshot(override: TrackMetadataOverrideEntity?) = TrackMergeSnapshot(
        survivorFavorite = false,
        survivorHidden = false,
        mergedFavorite = false,
        mergedHidden = false,
        survivorOverride = override,
        sourceIds = emptyList(),
        playlistEntries = emptyList(),
        lyricIds = emptyList(),
        historyEventIds = emptyList(),
        survivorFeedback = emptyList(),
        mergedFeedback = emptyList(),
    )
}
