package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowedPlaybackQueueTest {
    @Test
    fun `large logical queue materializes only a bounded initial window`() {
        val queue = WindowedPlaybackQueue()
        val tracks = tracks(1_000)

        queue.reset(tracks, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF, shuffleSeed = 42)

        val window = queue.materializedWindow()
        assertEquals(1_000, queue.snapshot().tracks.size)
        assertEquals(11, window.tracks.size)
        assertEquals(0, window.currentIndex)
    }

    @Test
    fun `refill returns ten new tracks when only three loaded tracks remain ahead`() {
        val queue = WindowedPlaybackQueue()
        val tracks = tracks(40)
        queue.reset(tracks, 0, PlaybackMode.ORDERED, RepeatMode.OFF)

        val initial = queue.materializedWindow()
        assertEquals(11, initial.tracks.size)
        assertTrue(queue.shouldRefillAhead(currentPlayerIndex = 7, playerItemCount = initial.tracks.size))

        val refill = queue.nextBatchAfter(initial.tracks.last().id.toString())
        assertEquals(10, refill.size)
        assertEquals(tracks.subList(11, 21).map { it.id }, refill.map { it.id })
    }

    @Test
    fun `automatic queue identity deduplicates by canonical track id`() {
        val queue = WindowedPlaybackQueue()
        val original = track(1)
        val duplicateSourceRepresentation = original.copy(contentUri = "file:///another-copy.mp3")

        queue.reset(
            listOf(original, duplicateSourceRepresentation, track(2)),
            0,
            PlaybackMode.ORDERED,
            RepeatMode.OFF,
        )

        assertEquals(listOf(original.id, trackId(2)), queue.snapshot().tracks.map { it.id })
        assertFalse(queue.append(duplicateSourceRepresentation))
        assertEquals(2, queue.snapshot().tracks.size)
    }

    @Test
    fun `successive windows never introduce an automatic duplicate`() {
        val queue = WindowedPlaybackQueue()
        val tracks = tracks(100)
        queue.reset(tracks, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF)

        val materializedIds = linkedSetOf<UUID>()
        var window = queue.materializedWindow().tracks
        window.forEach { assertTrue(materializedIds.add(it.id)) }

        while (window.last().id != tracks.last().id) {
            val refill = queue.nextBatchAfter(window.last().id.toString())
            if (refill.isEmpty()) break
            refill.forEach { assertTrue(materializedIds.add(it.id)) }
            window = refill
        }

        assertEquals(tracks.map { it.id }.toSet(), materializedIds)
    }

    @Test
    fun `queue keeps bounded history while allowing forward trimming`() {
        val queue = WindowedPlaybackQueue()
        queue.reset(tracks(100), 50, PlaybackMode.ORDERED, RepeatMode.OFF)

        val window = queue.materializedWindow()
        assertEquals(WindowedPlaybackQueue.HISTORY_LIMIT, window.currentIndex)
        assertEquals(21, window.tracks.size)
        assertEquals(4, queue.trimBeforeCount(WindowedPlaybackQueue.HISTORY_LIMIT + 4))
    }

    private fun tracks(count: Int): List<PlayableTrack> = (0 until count).map(::track)

    private fun track(index: Int): PlayableTrack = PlayableTrack(
        id = trackId(index),
        title = "Track $index",
        artist = "Artist",
        album = "Album",
        durationMs = 180_000,
        artworkRef = null,
        contentUri = "file:///track-$index.mp3",
    )

    private fun trackId(index: Int): UUID = UUID(0L, index.toLong() + 1L)
}
