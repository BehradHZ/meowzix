package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for playback of Telegram tracks, consumption of a manual Queue
 * occurrence, and formerly interleaved Next / Queue / Next sections.
 */
class QueuePlaybackRecoveryTest {
    private fun song(n: Int, uri: String = "content://test/$n") = PlayableTrack(
        id = UUID.nameUUIDFromBytes("recovery-$n".toByteArray()),
        title = "Song $n",
        artist = "Artist",
        album = null,
        durationMs = 180_000L,
        artworkRef = null,
        contentUri = uri,
    )

    @Test fun playingManualQueueConsumesOnlyItsOwnOccurrenceAndRetainsHistory() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.insertNext(songs[4])
        val queued = requireNotNull(queue.snapshot()).occurrenceIds[1]
        assertTrue(queue.isFixedOccurrence(1))

        // Switching playback is not equivalent to a successful audio start.
        requireNotNull(queue.jumpTo(1))
        assertTrue(queue.isFixedOccurrence(1))
        assertTrue(queue.consumeCurrentFixed())
        assertFalse(queue.consumeCurrentFixed())
        val playing = requireNotNull(queue.snapshot())
        assertEquals(queued, playing.occurrenceIds[playing.currentIndex])
        assertEquals(QueueItemOrigin.GENERATED, playing.origins[playing.currentIndex])
        assertFalse(queue.isFixedOccurrence(playing.currentIndex))

        requireNotNull(queue.jumpTo(2))
        val history = requireNotNull(queue.snapshot())
        assertEquals(queued, history.occurrenceIds[1])
        assertTrue(history.occurrenceIds.contains(queued))

        // A different playlist must not resurrect that same manual Queue item.
        queue.start(listOf(song(20), song(21)), 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        assertFalse(requireNotNull(queue.snapshot()).occurrenceIds.contains(queued))
    }

    @Test fun pendingUnplayedManualQueueStillSurvivesAnotherPlaylist() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.append(songs[4])
        val queued = requireNotNull(queue.snapshot()).occurrenceIds[1]
        queue.start(listOf(song(20), song(21)), 0, PlaybackMode.ORDERED, RepeatMode.OFF)

        val after = requireNotNull(queue.snapshot())
        assertEquals(queued, after.occurrenceIds[1])
        assertEquals(QueueItemOrigin.MANUAL, after.origins[1])
        assertTrue(queue.isFixedOccurrence(1))
        assertEquals(song(20).id, after.tracks[after.currentIndex].id)
    }

    @Test fun oldPersistedInterleavingIsRestoredAsOneQueueThenOneNext() {
        val songs = (0..7).map(::song)
        val queue = ProgressiveQueue()
        val origins = listOf(
            QueueItemOrigin.MANUAL,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.MANUAL,
            QueueItemOrigin.MANUAL,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.GENERATED,
            QueueItemOrigin.GENERATED,
        )
        val ids = songs.map { it.id }
        assertTrue(queue.restore(
            orderedTrackIds = ids,
            availableTracks = songs.associateBy { it.id },
            requestedCurrentIndex = 0,
            requestedMaterializedStartIndex = 0,
            requestedMaterializedEndExclusive = 8,
            mode = PlaybackMode.ORDERED,
            repeat = RepeatMode.OFF,
            seed = null,
            requestedOrigins = origins,
            requestedOccurrenceIds = List(songs.size) { UUID.randomUUID() },
        ))

        val after = requireNotNull(queue.snapshot())
        assertEquals(listOf(0, 3, 4, 1, 2, 5, 6, 7).map { songs[it].id },
            after.tracks.map { it.id })
        assertEquals(
            listOf(QueueItemOrigin.MANUAL, QueueItemOrigin.MANUAL) +
                List(5) { QueueItemOrigin.GENERATED },
            after.origins.drop(1),
        )
        assertTrue(queue.isFixedOccurrence(1))
        assertTrue(queue.isFixedOccurrence(2))
        assertFalse(queue.isFixedOccurrence(3))
    }

    @Test fun reQueueingAPlayedSongCreatesANewOccurrenceWithoutErasingHistory() {
        val songs = (0..6).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.insertNext(songs[4])
        val firstOccurrence = requireNotNull(queue.snapshot()).occurrenceIds[1]
        requireNotNull(queue.jumpTo(1))
        assertTrue(queue.consumeCurrentFixed())
        requireNotNull(queue.jumpTo(2))
        assertTrue(queue.insertNext(songs[4]))

        val state = requireNotNull(queue.snapshot())
        val originalIndex = state.occurrenceIds.indexOf(firstOccurrence)
        assertTrue(originalIndex in 0 until state.currentIndex)
        val pending = state.tracks.indices.filter { queue.isFixedOccurrence(it) }
        assertEquals(1, pending.size)
        assertEquals(songs[4].id, state.tracks[pending.single()].id)
        assertTrue(state.occurrenceIds[pending.single()] != firstOccurrence)
        assertTrue(state.occurrenceIds.contains(firstOccurrence))
    }

    @Test fun preparingACompletedDownloadUpdatesExistingSessionSourceWithoutChangingOccurrence() {
        val remote = song(1, "meowzix-tdlib://audio/123?trackId=abc")
        val queue = ProgressiveQueue()
        queue.start(listOf(song(0), remote, song(2)), 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val original = requireNotNull(queue.snapshot())
        val occurrence = original.occurrenceIds[1]
        val file = remote.copy(contentUri = "file:///app/offline/audio.mp3")

        assertTrue(queue.replacePlayableSource(remote.id, file))
        val after = requireNotNull(queue.snapshot())
        assertEquals(file.contentUri, after.tracks[1].contentUri)
        assertEquals(occurrence, after.occurrenceIds[1])
        assertEquals(original.tracks.map { it.id }, after.tracks.map { it.id })
        assertEquals(original.currentIndex, after.currentIndex)
    }

    @Test fun repeatedConsumptionNeverClearsAnUnrelatedManualQueueEntry() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF)
        queue.insertNext(songs[4])
        queue.append(songs[5])
        val before = requireNotNull(queue.snapshot())
        val consumed = before.occurrenceIds[1]
        val pending = before.occurrenceIds[2]

        assertTrue(queue.consumeFixedOccurrence(consumed))
        assertFalse(queue.consumeFixedOccurrence(consumed))
        assertTrue(queue.isFixedOccurrence(requireNotNull(queue.snapshot()).occurrenceIds.indexOf(pending)))
        assertEquals(pending, requireNotNull(queue.snapshot()).occurrenceIds[2])
    }
}
