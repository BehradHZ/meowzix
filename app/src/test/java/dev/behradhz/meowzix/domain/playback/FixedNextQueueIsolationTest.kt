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

    @Test fun removingANextOccurrenceDoesNotReshuffleOrAdvancePlayback() {
        val songs = (0..15).map(::song)
        val queue = ProgressiveQueue()
        queue.start(
            songs.take(12), 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            seed = 1234L, sourceIds = songs.map { it.id },
        )
        queue.insertNext(songs[15])
        val before = requireNotNull(queue.snapshot())
        val currentOccurrence = before.occurrenceIds[before.currentIndex]
        val generatedBefore = before.tracks.indices
            .filter { before.origins[it] == QueueItemOrigin.GENERATED && it > before.currentIndex }
            .map { before.occurrenceIds[it] to before.tracks[it].id }
        val toRemove = before.tracks.indexOfFirst { it.id == songs[5].id }
        val removedOccurrence = before.occurrenceIds[toRemove]

        assertTrue(queue.removeAt(toRemove))
        val after = requireNotNull(queue.snapshot())
        assertEquals(currentOccurrence, after.occurrenceIds[after.currentIndex])
        assertEquals(PlaybackMode.PURE_SHUFFLE, after.playbackMode)
        assertEquals(1234L, after.shuffleSeed)
        val generatedAfter = after.tracks.indices
            .filter { after.origins[it] == QueueItemOrigin.GENERATED && it > after.currentIndex }
            .map { after.occurrenceIds[it] to after.tracks[it].id }
        assertEquals(generatedBefore.filterNot { it.first == removedOccurrence }, generatedAfter)

        assertTrue(queue.updateCurrentFromWindowIndex(1, songs[15].id.toString()))
        val advanced = requireNotNull(queue.snapshot())
        assertEquals(generatedAfter, advanced.tracks.indices
            .filter { advanced.origins[it] == QueueItemOrigin.GENERATED && it > advanced.currentIndex }
            .map { advanced.occurrenceIds[it] to advanced.tracks[it].id })
    }

    @Test fun deletingDuplicateGeneratedOccurrenceLeavesFixedQueueInstanceIntact() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF,
            sourceIds = songs.map { it.id })
        queue.insertNext(songs[1])
        val before = requireNotNull(queue.snapshot())
        val fixedOccurrence = before.occurrenceIds[1]
        val duplicateGeneratedIndex = (2 until before.tracks.size).first {
            before.tracks[it].id == songs[1].id && before.origins[it] == QueueItemOrigin.GENERATED
        }

        assertTrue(queue.removeAt(duplicateGeneratedIndex))
        val after = requireNotNull(queue.snapshot())
        assertTrue(after.occurrenceIds.contains(fixedOccurrence))
        assertTrue(queue.isFixedOccurrence(after.occurrenceIds.indexOf(fixedOccurrence)))
        assertEquals(0, after.currentIndex)
    }

    @Test fun selectingCFromNextDiscardsABAndKeepsDThroughZ() {
        val songs = (0..25).map(::song) // A..Z
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF,
            sourceIds = songs.map { it.id })

        val before = requireNotNull(queue.snapshot())
        val cIndex = before.tracks.indexOfFirst { it.id == songs[2].id }
        val nextSuffix = before.generatedIndicesAfterSelection(cIndex)
        assertEquals(songs.drop(3).map { it.id }, nextSuffix.map { before.tracks[it].id })

        queue.start(
            orderedTracks = listOf(before.tracks[cIndex]) + nextSuffix.map(before.tracks::get),
            requestedStartIndex = 0,
            mode = PlaybackMode.ORDERED,
            repeat = RepeatMode.OFF,
            sourceIds = songs.map { it.id },
            futureOccurrenceIds = nextSuffix.map(before.occurrenceIds::get),
        )

        val after = requireNotNull(queue.snapshot())
        assertEquals(songs.drop(2).map { it.id }, after.tracks.map { it.id })
        assertEquals(nextSuffix.map(before.occurrenceIds::get), after.occurrenceIds.drop(1))
        assertFalse(after.tracks.any { it.id == songs[0].id || it.id == songs[1].id })
    }

    @Test fun selectingFromNextSkipsOnlyPreviousNextAndPreservesManualQueue() {
        val songs = (0..25).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            seed = 42L, sourceIds = songs.map { it.id })
        queue.insertNext(songs[1])
        queue.append(songs[4])
        queue.append(songs[6])
        val before = requireNotNull(queue.snapshot())
        val fixedBefore = before.tracks.indices.filter(queue::isFixedOccurrence)
            .map { before.occurrenceIds[it] to before.tracks[it].id }
        val cIndex = before.tracks.indices.first {
            before.origins[it] == QueueItemOrigin.GENERATED && before.tracks[it].id == songs[2].id
        }
        val nextSuffix = before.generatedIndicesAfterSelection(cIndex)
        // Songs transferred into fixed Queue are not also present in generated Next.
        val expectedNext = (cIndex + 1 until before.tracks.size).filter {
            before.origins[it] == QueueItemOrigin.GENERATED
        }
        assertEquals(expectedNext, nextSuffix)

        queue.start(
            listOf(before.tracks[cIndex]) + nextSuffix.map(before.tracks::get),
            0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            seed = 42L,
            sourceIds = songs.map { it.id },
            futureOccurrenceIds = nextSuffix.map(before.occurrenceIds::get),
        )

        val after = requireNotNull(queue.snapshot())
        assertEquals(songs[2].id, after.tracks[after.currentIndex].id)
        assertEquals(fixedBefore, after.tracks.indices.filter(queue::isFixedOccurrence)
            .map { after.occurrenceIds[it] to after.tracks[it].id })
        assertEquals(expectedNext.map { before.tracks[it].id }, after.tracks.indices.filter {
            it > after.currentIndex && after.origins[it] == QueueItemOrigin.GENERATED
        }.map { after.tracks[it].id })
        assertEquals(nextSuffix.map(before.occurrenceIds::get), after.tracks.indices.filter {
            it > after.currentIndex && after.origins[it] == QueueItemOrigin.GENERATED
        }.map(after.occurrenceIds::get))
        assertEquals(42L, after.shuffleSeed)
    }

    @Test fun selectingNextWithDuplicateSongIdsRetainsOnlyTheFollowingOccurrences() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(listOf(songs[0], songs[1], songs[2], songs[1], songs[3], songs[4]),
            0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())
        val nextSuffix = before.generatedIndicesAfterSelection(2)
        assertEquals(listOf(songs[1].id, songs[3].id, songs[4].id),
            nextSuffix.map { before.tracks[it].id })

        queue.start(
            listOf(before.tracks[2]) + nextSuffix.map(before.tracks::get),
            0, PlaybackMode.ORDERED, RepeatMode.OFF,
            futureOccurrenceIds = nextSuffix.map(before.occurrenceIds::get),
        )
        val after = requireNotNull(queue.snapshot())
        assertEquals(listOf(songs[2].id, songs[1].id, songs[3].id, songs[4].id),
            after.tracks.map { it.id })
        assertEquals(nextSuffix.map(before.occurrenceIds::get), after.occurrenceIds.drop(1))
        assertFalse(after.occurrenceIds.contains(before.occurrenceIds[1]))
    }

    @Test fun selectingManualQueueEntryDoesNotSkipEarlierGeneratedNextOccurrences() {
        val songs = (0..7).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        queue.insertNext(songs[7])
        queue.append(songs[6])
        val before = requireNotNull(queue.snapshot())
        val manualIndex = before.tracks.indices.first(queue::isFixedOccurrence)
        val nextToKeep = before.generatedIndicesAfterSelection(manualIndex)
        val expectedNext = (before.currentIndex + 1 until before.tracks.size).filter {
            before.origins[it] == QueueItemOrigin.GENERATED
        }
        assertEquals(expectedNext, nextToKeep)
        val fixedBefore = before.tracks.indices.filter(queue::isFixedOccurrence)
            .map(before.occurrenceIds::get)

        queue.start(
            listOf(before.tracks[manualIndex]) + nextToKeep.map(before.tracks::get),
            0, PlaybackMode.ORDERED, RepeatMode.OFF,
            futureOccurrenceIds = nextToKeep.map(before.occurrenceIds::get),
        )
        val after = requireNotNull(queue.snapshot())
        assertEquals(fixedBefore, after.tracks.indices.filter(queue::isFixedOccurrence)
            .map(after.occurrenceIds::get))
    }

}
