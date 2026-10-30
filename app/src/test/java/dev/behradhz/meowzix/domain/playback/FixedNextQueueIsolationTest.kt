package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedNextQueueIsolationTest {
    private fun song(id: Int) = PlayableTrack(
        id = UUID.nameUUIDFromBytes("fixed-next-$id".toByteArray()),
        title = "Song $id", artist = "Artist", album = null,
        durationMs = 120_000L, artworkRef = null, contentUri = "content://song/$id",
    )

    @Test fun manualQueueSurvivesIndependentLibraryPlaybackEvenWithSameTrackId() {
        val songs = (0..8).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF, sourceIds = songs.map { it.id })
        assertTrue(queue.insertNext(songs[1]))
        assertTrue(queue.append(songs[2]))
        assertTrue(queue.append(songs[3]))
        val fixedBefore = requireNotNull(queue.snapshot())
            .let { state -> state.tracks.indices.filter(queue::isFixedOccurrence)
                .map { state.occurrenceIds[it] to state.tracks[it].id } }
        assertEquals(listOf(songs[1].id, songs[2].id, songs[3].id), fixedBefore.map { it.second })

        // Select B independently from the Library, not its queued occurrence.
        queue.start(songs, 1, PlaybackMode.ORDERED, RepeatMode.OFF, sourceIds = songs.map { it.id })
        val after = requireNotNull(queue.snapshot())
        assertEquals(songs[1].id, after.tracks[after.currentIndex].id)
        val fixedAfter = after.tracks.indices.filter(queue::isFixedOccurrence)
            .map { after.occurrenceIds[it] to after.tracks[it].id }
        assertEquals(fixedBefore, fixedAfter)
        assertTrue(after.tracks.count { it.id == songs[1].id } >= 2)
    }

    @Test fun nextReorderNeverConvertsAnOccurrenceIntoManualQueue() {
        val songs = (0..8).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())
        val occurrence = before.occurrenceIds[2]
        assertEquals(QueueItemOrigin.GENERATED, before.origins[2])
        assertTrue(queue.move(2, 4))
        val after = requireNotNull(queue.snapshot())
        val index = after.occurrenceIds.indexOf(occurrence)
        assertEquals(QueueItemOrigin.GENERATED, after.origins[index])
        assertFalse(queue.isFixedOccurrence(index))
    }

    @Test fun crossingSectionsTransfersOnlyTheDraggedOccurrence() {
        val songs = (0..7).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF)
        queue.insertNext(songs[7])
        val before = requireNotNull(queue.snapshot())
        val nextId = before.occurrenceIds[2]
        assertTrue(queue.move(2, 1))
        var state = requireNotNull(queue.snapshot())
        var position = state.occurrenceIds.indexOf(nextId)
        assertTrue(queue.isFixedOccurrence(position))
        assertTrue(queue.move(position, state.tracks.lastIndex))
        state = requireNotNull(queue.snapshot())
        position = state.occurrenceIds.indexOf(nextId)
        assertFalse(queue.isFixedOccurrence(position))
        assertEquals(QueueItemOrigin.GENERATED, state.origins[position])
    }

    @Test fun consumptionRemovesOnlyThatFixedOccurrenceFromPersistence() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.insertNext(songs[4])
        val state = requireNotNull(queue.snapshot())
        val fixed = state.occurrenceIds[1]
        assertTrue(queue.consumeFixedOccurrence(fixed))
        assertFalse(queue.consumeFixedOccurrence(fixed))
        queue.start(songs, 3, PlaybackMode.ORDERED, RepeatMode.OFF)
        assertFalse(requireNotNull(queue.snapshot()).occurrenceIds.contains(fixed))
    }

    @Test fun modeChangeAndFutureRegenerationKeepManualQueueFixed() {
        val songs = (0..8).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.insertNext(songs[7])
        val fixedId = requireNotNull(queue.snapshot()).occurrenceIds[1]
        queue.updatePlaybackMode(PlaybackMode.PURE_SHUFFLE)
        queue.replaceFuture(songs.drop(1), PlaybackMode.SMART_SHUFFLE)
        val state = requireNotNull(queue.snapshot())
        assertEquals(fixedId, state.occurrenceIds[1])
        assertTrue(queue.isFixedOccurrence(1))
        assertEquals(QueueItemOrigin.GENERATED, state.origins[2])
    }
}
