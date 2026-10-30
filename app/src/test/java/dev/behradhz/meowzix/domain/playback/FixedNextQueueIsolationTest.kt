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

    @Test fun selectingCFromNextKeepsABAsHistoryAndDThroughZAsFuture() {
        val songs = (0..25).map(::song) // A..Z
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF,
            sourceIds = songs.map { it.id })
        val before = requireNotNull(queue.snapshot())

        val plan = requireNotNull(queue.selectUpcomingNext(2))
        val after = requireNotNull(queue.snapshot())
        assertEquals(2, after.currentIndex)
        assertEquals(songs[2].id, after.tracks[after.currentIndex].id)
        assertEquals(songs.take(2).map { it.id }, after.tracks.take(after.currentIndex).map { it.id })
        assertEquals(songs.drop(3).map { it.id },
            after.tracks.drop(after.currentIndex + 1).map { it.id })
        assertEquals(before.occurrenceIds, after.occurrenceIds)
        assertEquals(before.shuffleSeed, after.shuffleSeed)
        assertEquals(songs[2].id, plan.tracks[plan.startIndexInWindow].id)

        // B and A were skipped, but are still reachable through Previous.
        assertTrue(queue.hasPrevious())
        requireNotNull(queue.jumpTo(1))
        assertEquals(songs[1].id, queue.snapshot()?.tracks?.get(queue.snapshot()!!.currentIndex)?.id)
        requireNotNull(queue.jumpTo(0))
        assertEquals(songs[0].id, queue.snapshot()?.tracks?.get(queue.snapshot()!!.currentIndex)?.id)
        // Returning to C does not bring A/B into its future.
        requireNotNull(queue.selectUpcomingNext(2))
        assertEquals(songs.drop(3).map { it.id },
            requireNotNull(queue.snapshot()).tracks.drop(3).map { it.id })
    }

    @Test fun selectingNextKeepsManualQueuePendingAndSkippedNextAsHistory() {
        val songs = (0..25).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            seed = 42L, sourceIds = songs.map { it.id })
        queue.insertNext(songs[20])
        queue.append(songs[21])
        queue.append(songs[22])
        val before = requireNotNull(queue.snapshot())
        val fixedBefore = before.tracks.indices.filter(queue::isFixedOccurrence)
            .map { before.occurrenceIds[it] to before.tracks[it].id }
        val cIndex = before.tracks.indices.first {
            it > before.currentIndex &&
                before.origins[it] == QueueItemOrigin.GENERATED &&
                before.tracks[it].id == songs[2].id
        }
        val skippedGenerated = (before.currentIndex + 1 until cIndex).filter {
            before.origins[it] == QueueItemOrigin.GENERATED
        }
        val futureGenerated = (cIndex + 1 until before.tracks.size).filter {
            before.origins[it] == QueueItemOrigin.GENERATED
        }
        val allOccurrences = before.occurrenceIds.toSet()

        requireNotNull(queue.selectUpcomingNext(cIndex))
        val after = requireNotNull(queue.snapshot())
        assertEquals(songs[2].id, after.tracks[after.currentIndex].id)
        assertEquals(listOf(songs[0].id) + skippedGenerated.map { before.tracks[it].id },
            after.tracks.take(after.currentIndex).map { it.id })
        assertEquals(fixedBefore, after.tracks.indices.filter(queue::isFixedOccurrence)
            .map { after.occurrenceIds[it] to after.tracks[it].id })
        assertEquals(fixedBefore.map { it.second },
            after.tracks.indices.filter {
                it > after.currentIndex && queue.isFixedOccurrence(it)
            }.map { after.tracks[it].id })
        assertEquals(futureGenerated.map { before.occurrenceIds[it] },
            after.tracks.indices.filter {
                it > after.currentIndex && after.origins[it] == QueueItemOrigin.GENERATED
            }.map(after.occurrenceIds::get))
        assertEquals(allOccurrences, after.occurrenceIds.toSet())
        assertEquals(before.tracks.size, after.tracks.size)
        assertEquals(42L, after.shuffleSeed)
        assertTrue(queue.hasPrevious())
    }

    @Test fun selectingNextKeepsPreviousAndFollowingDuplicateOccurrencesDistinct() {
        val songs = (0..5).map(::song)
        val queue = ProgressiveQueue()
        queue.start(listOf(songs[0], songs[1], songs[2], songs[1], songs[3], songs[4]),
            0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())
        requireNotNull(queue.selectUpcomingNext(2))
        val after = requireNotNull(queue.snapshot())
        assertEquals(2, after.currentIndex)
        assertEquals(before.occurrenceIds, after.occurrenceIds)
        assertEquals(listOf(songs[0].id, songs[1].id),
            after.tracks.take(after.currentIndex).map { it.id })
        assertEquals(listOf(songs[1].id, songs[3].id, songs[4].id),
            after.tracks.drop(after.currentIndex + 1).map { it.id })
        assertTrue(after.occurrenceIds[1] != after.occurrenceIds[3])
        requireNotNull(queue.jumpTo(1))
        assertEquals(songs[1].id, requireNotNull(queue.snapshot()).tracks[1].id)
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
