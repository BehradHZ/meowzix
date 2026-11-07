package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveQueueTailAppendTest {
    @Test
    fun `append prioritizes a manual entry without expanding the playback window`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(100)
        val appended = track("appended")
        queue.start(tracks, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())

        assertTrue(queue.append(appended))

        val after = requireNotNull(queue.snapshot())
        assertEquals(before.materializedStartIndex, after.materializedStartIndex)
        assertEquals(before.materializedEndExclusive, after.materializedEndExclusive)
        assertEquals(appended.id, after.tracks[1].id)
        assertTrue(queue.isFixedOccurrence(1))
        assertEquals(tracks.drop(1).map { it.id }, after.tracks.drop(2).map { it.id })
        assertEquals(101, after.tracks.size)
        assertTrue(queue.takeForwardBatchIfNeeded().isEmpty())
    }

    @Test
    fun `tail append near exhausted window is exposed by normal forward refill`() {
        val queue = ProgressiveQueue()
        val tracks = tracks(5)
        val appended = track("appended-near-end")
        queue.start(tracks, tracks.lastIndex, PlaybackMode.ORDERED, RepeatMode.OFF)

        assertTrue(queue.append(appended))

        val batch = queue.takeForwardBatchIfNeeded()
        assertEquals(listOf(appended.id), batch.map { it.id })
        val snapshot = requireNotNull(queue.snapshot())
        assertEquals(snapshot.tracks.size, snapshot.materializedEndExclusive)
    }

    private fun tracks(count: Int): List<PlayableTrack> = List(count) { index ->
        track("track-$index")
    }

    private fun track(key: String) = PlayableTrack(
        id = UUID.nameUUIDFromBytes(key.toByteArray()),
        title = key,
        artist = "Artist",
        album = null,
        durationMs = 180_000,
        artworkRef = null,
        contentUri = "content://$key",
    )
}
