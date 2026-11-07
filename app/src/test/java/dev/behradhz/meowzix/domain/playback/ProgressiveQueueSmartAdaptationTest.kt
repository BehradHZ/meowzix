package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveQueueSmartAdaptationTest {
    @Test
    fun `smart queue protects current and marks future as generated`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(8)

        queue.start(tracks, 2, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF)

        val snapshot = requireNotNull(queue.snapshot())
        assertEquals(QueueItemOrigin.MANUAL, snapshot.origins[2])
        assertTrue(snapshot.origins.drop(3).all { it == QueueItemOrigin.GENERATED })
    }

    @Test
    fun `manual play next stays fixed while generated future reranks`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(10)
        queue.start(tracks, 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF)
        queue.insertNext(tracks[8])
        val before = requireNotNull(queue.snapshot())
        val generatedBefore = before.tracks.indices
            .filter { it > before.currentIndex && before.origins[it] == QueueItemOrigin.GENERATED }
            .map { before.tracks[it].id }

        val result = queue.rerankGeneratedFuture(generatedBefore.reversed(), before.revision)

        assertEquals(SmartFutureRevisionResult.APPLIED, result)
        val after = requireNotNull(queue.snapshot())
        assertEquals(tracks[8].id, after.tracks[1].id)
        assertEquals(QueueItemOrigin.MANUAL, after.origins[1])
        assertEquals(generatedBefore.reversed(), after.tracks.indices
            .filter { it > after.currentIndex && after.origins[it] == QueueItemOrigin.GENERATED }
            .map { after.tracks[it].id })
    }

    @Test
    fun `stale smart ranking cannot mutate queue`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(8)
        queue.start(tracks, 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF)
        val captured = requireNotNull(queue.snapshot())
        queue.append(tracks[7])
        val beforeApply = requireNotNull(queue.snapshot())

        val result = queue.rerankGeneratedFuture(captured.tracks.drop(1).map { it.id }.reversed(), captured.revision)

        assertEquals(SmartFutureRevisionResult.STALE, result)
        assertEquals(beforeApply.tracks.map { it.id }, requireNotNull(queue.snapshot()).tracks.map { it.id })
    }

    @Test
    fun `pure shuffle remains independent from smart reranking`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(12)
        queue.start(tracks, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF, seed = 42L)
        val before = requireNotNull(queue.snapshot())

        val result = queue.rerankGeneratedFuture(before.tracks.drop(1).map { it.id }.reversed(), before.revision)

        assertEquals(SmartFutureRevisionResult.NOT_SMART, result)
        assertEquals(before.tracks.map { it.id }, requireNotNull(queue.snapshot()).tracks.map { it.id })
    }

    @Test
    fun `restored provenance remains durable`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(6)
        val origins = listOf(
            QueueItemOrigin.MANUAL,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.MANUAL,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.GENERATED,
        )

        assertTrue(queue.restore(
            orderedTrackIds = tracks.map { it.id },
            availableTracks = tracks.associateBy { it.id },
            requestedCurrentIndex = 0,
            requestedMaterializedStartIndex = 0,
            requestedMaterializedEndExclusive = 6,
            mode = PlaybackMode.SMART_SHUFFLE,
            repeat = RepeatMode.OFF,
            seed = null,
            requestedOrigins = origins,
        ))

        val restored = requireNotNull(queue.snapshot())
        // Legacy interleaving is normalized into contiguous manual and generated
        // lanes; provenance must stay attached to each track, not its old index.
        assertEquals(tracks.zip(origins).associate { it.first.id to it.second },
            restored.tracks.zip(restored.origins).associate { it.first.id to it.second })
        assertEquals(QueueItemOrigin.MANUAL, restored.origins[restored.currentIndex])
        assertEquals(QueueItemOrigin.MANUAL, restored.origins[restored.currentIndex + 1])
    }

    private fun tracks(count: Int): List<PlayableTrack> = List(count) { index ->
        PlayableTrack(
            id = UUID.nameUUIDFromBytes("smart-track-$index".toByteArray()),
            title = "Track $index",
            artist = "Artist ${index % 3}",
            album = null,
            durationMs = 180_000,
            artworkRef = null,
            contentUri = "content://track/$index",
        )
    }
}
