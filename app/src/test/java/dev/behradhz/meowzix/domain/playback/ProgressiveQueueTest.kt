package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveQueueTest {
    @Test
    fun `initial window contains current plus ten future tracks`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(1_000)

        val plan = queue.start(tracks, 500, PlaybackMode.ORDERED, RepeatMode.OFF)
        val snapshot = requireNotNull(queue.snapshot())

        assertEquals(ProgressiveQueue.PREVIOUS_WINDOW_SIZE, plan.startIndexInWindow)
        assertEquals(ProgressiveQueue.PREVIOUS_WINDOW_SIZE + 1 + ProgressiveQueue.INITIAL_FORWARD_COUNT, plan.tracks.size)
        assertEquals(500, snapshot.currentIndex)
        assertEquals(497, snapshot.materializedStartIndex)
        assertEquals(511, snapshot.materializedEndExclusive)
    }

    @Test
    fun `forward window refills ten when only three remain`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(100)
        queue.start(tracks, 0, PlaybackMode.ORDERED, RepeatMode.OFF)

        repeat(7) { index ->
            queue.updateCurrent(tracks[index + 1].id.toString())
        }
        val batch = queue.takeForwardBatchIfNeeded()

        assertEquals(10, batch.size)
        assertEquals(tracks.subList(11, 21).map { it.id }, batch.map { it.id })
        assertTrue(batch.map { it.id }.distinct().size == batch.size)
    }

    @Test
    fun `old materialized history is trimmed to keep the player timeline bounded`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(100)
        queue.start(tracks, 0, PlaybackMode.ORDERED, RepeatMode.OFF)

        repeat(20) { index ->
            queue.updateCurrent(tracks[index + 1].id.toString())
            queue.takeForwardBatchIfNeeded()
            queue.trimMaterializedHistory()
        }

        val snapshot = requireNotNull(queue.snapshot())
        assertEquals(snapshot.currentIndex - ProgressiveQueue.PREVIOUS_WINDOW_SIZE, snapshot.materializedStartIndex)
        assertTrue(snapshot.materializedEndExclusive - snapshot.materializedStartIndex <= 1 + ProgressiveQueue.PREVIOUS_WINDOW_SIZE + ProgressiveQueue.INITIAL_FORWARD_COUNT + ProgressiveQueue.REFILL_BATCH_SIZE)
    }

    @Test
    fun `duplicate canonical track IDs are rejected from manual queue mutation`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(20)
        queue.start(tracks, 0, PlaybackMode.ORDERED, RepeatMode.OFF)

        assertFalse(queue.insertNext(tracks[5]))
        assertFalse(queue.append(tracks[10]))
        assertEquals(20, queue.snapshot()?.tracks?.size)
    }

    @Test
    fun `removing current track advances logical current to its successor`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(20)
        queue.start(tracks, 5, PlaybackMode.ORDERED, RepeatMode.OFF)

        assertTrue(queue.removeAt(5))

        val snapshot = requireNotNull(queue.snapshot())
        assertEquals(5, snapshot.currentIndex)
        assertEquals(tracks[6].id, snapshot.tracks[snapshot.currentIndex].id)
        assertFalse(snapshot.tracks.any { it.id == tracks[5].id })
    }

    @Test
    fun `many refills cover queue once without duplicates`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(1_000)
        queue.start(tracks, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF, seed = 42)
        val seen = mutableSetOf<UUID>()
        seen += queue.resetWindow().tracks.map { it.id }

        for (track in tracks.drop(1)) {
            queue.updateCurrent(track.id.toString())
            seen += queue.takeForwardBatchIfNeeded().map { it.id }
        }

        assertEquals(1_000, seen.size)
        assertEquals(tracks.map { it.id }.toSet(), seen)
    }

    @Test
    fun `ten thousand track queue keeps Media3 window bounded and reuses stable snapshot`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(10_000)
        val requestedIndex = 5_000

        val initialWindow = queue.start(
            orderedTracks = tracks,
            requestedStartIndex = requestedIndex,
            mode = PlaybackMode.ORDERED,
            repeat = RepeatMode.OFF,
        )
        val firstSnapshot = requireNotNull(queue.snapshot())
        val secondSnapshot = requireNotNull(queue.snapshot())

        assertEquals(10_000, firstSnapshot.tracks.size)
        assertEquals(requestedIndex, firstSnapshot.currentIndex)
        assertEquals(
            ProgressiveQueue.PREVIOUS_WINDOW_SIZE + 1 + ProgressiveQueue.INITIAL_FORWARD_COUNT,
            initialWindow.tracks.size,
        )
        assertSame(firstSnapshot, secondSnapshot)

        // A playback-position tick resolves the same logical media id. It must not allocate a new
        // 10k-element queue snapshot while nothing about the queue has changed.
        assertTrue(queue.updateCurrent(tracks[requestedIndex].id.toString()))
        assertSame(firstSnapshot, queue.snapshot())

        // A real structural change must invalidate the cache and remain logically correct.
        val newTrack = PlayableTrack(
            id = UUID.nameUUIDFromBytes("extra-track".toByteArray()),
            title = "Extra Track",
            artist = "Artist",
            album = null,
            durationMs = 180_000,
            artworkRef = null,
            contentUri = "content://track/extra",
        )
        assertTrue(queue.insertNext(newTrack))
        val changedSnapshot = requireNotNull(queue.snapshot())
        assertFalse(firstSnapshot === changedSnapshot)
        assertEquals(10_001, changedSnapshot.tracks.size)
        assertEquals(newTrack.id, changedSnapshot.tracks[requestedIndex + 1].id)
    }

    private fun tracks(count: Int): List<PlayableTrack> = List(count) { index ->
        PlayableTrack(
            id = UUID.nameUUIDFromBytes("track-$index".toByteArray()),
            title = "Track $index",
            artist = "Artist",
            album = null,
            durationMs = 180_000,
            artworkRef = null,
            contentUri = "content://track/$index",
        )
    }
}
