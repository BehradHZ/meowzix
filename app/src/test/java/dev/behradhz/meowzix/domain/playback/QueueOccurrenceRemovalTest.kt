package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Swipe actions identify the displayed occurrence, never an index captured
 * before a MediaController operation or a playback/window transition.
 */
class QueueOccurrenceRemovalTest {
    private fun song(index: Int) = PlayableTrack(
        id = UUID.nameUUIDFromBytes("swipe-track-$index".toByteArray()),
        title = "Song $index",
        artist = "Artist",
        album = null,
        durationMs = 120_000L,
        artworkRef = null,
        contentUri = "content://test/$index",
    )

    @Test fun swipeDeletesTargetAfterAnotherRowWasDeletedFirst() {
        val songs = (0..8).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val initial = requireNotNull(queue.snapshot())
        val b = initial.occurrenceIds[1]
        val d = initial.occurrenceIds[3]

        assertTrue(queue.removeOccurrence(b))
        // D is now at index 2, but its original gesture captured index 3.
        assertTrue(queue.removeOccurrence(d))
        val after = requireNotNull(queue.snapshot())
        assertEquals(listOf(0, 2, 4, 5, 6, 7, 8).map { songs[it].id },
            after.tracks.map { it.id })
        assertEquals(songs[0].id, after.tracks[after.currentIndex].id)
    }

    @Test fun swipeDeletesTargetAfterPlayerAutoAdvancedAndWindowChanged() {
        val songs = (0..50).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            sourceIds = songs.map { it.id })
        val target = requireNotNull(queue.snapshot()).occurrenceIds[30]
        val firstCurrent = requireNotNull(queue.snapshot()).occurrenceIds[0]
        requireNotNull(queue.jumpTo(12))
        queue.trimMaterializedHistory()

        assertTrue(queue.removeOccurrence(target))
        val after = requireNotNull(queue.snapshot())
        assertFalse(after.occurrenceIds.contains(target))
        assertEquals(songs[12].id, after.tracks[after.currentIndex].id)
        assertTrue(after.occurrenceIds.contains(firstCurrent))
        assertEquals(songs.size - 1, after.tracks.size)
    }

    @Test fun twoOccurrencesOfSameTrackAreRemovedIndependently() {
        val a = song(0)
        val b = song(1)
        val c = song(2)
        val queue = ProgressiveQueue()
        queue.start(listOf(a, b, c), 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        assertTrue(queue.insertNext(b))
        val before = requireNotNull(queue.snapshot())
        val twoCopies = before.tracks.indices.filter { before.tracks[it].id == b.id }
        assertEquals(2, twoCopies.size)
        val queuedCopy = twoCopies.first { queue.isFixedOccurrence(it) }
        val generatedCopy = twoCopies.first { !queue.isFixedOccurrence(it) }
        val manualId = before.occurrenceIds[queuedCopy]
        val generatedId = before.occurrenceIds[generatedCopy]

        assertTrue(queue.removeOccurrence(generatedId))
        val after = requireNotNull(queue.snapshot())
        assertFalse(after.occurrenceIds.contains(generatedId))
        assertTrue(after.occurrenceIds.contains(manualId))
        assertTrue(queue.isFixedOccurrence(after.occurrenceIds.indexOf(manualId)))
        assertEquals(listOf(a.id, b.id, c.id), after.tracks.map { it.id })
    }

    @Test fun swipedIdentityStillWorksAfterReordering() {
        val songs = (0..6).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())
        val target = before.occurrenceIds[2]
        val expectedCurrent = before.occurrenceIds[before.currentIndex]

        assertTrue(queue.move(2, 5))
        assertEquals(5, requireNotNull(queue.snapshot()).occurrenceIds.indexOf(target))
        assertTrue(queue.removeOccurrence(target))
        val after = requireNotNull(queue.snapshot())
        assertFalse(after.occurrenceIds.contains(target))
        assertEquals(expectedCurrent, after.occurrenceIds[after.currentIndex])
        assertEquals(listOf(0, 1, 3, 4, 5, 6).map { songs[it].id },
            after.tracks.map { it.id })
    }

    @Test fun duplicateSwipeOfSameOccurrenceDoesNotDeleteNextRow() {
        val songs = (0..4).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val target = requireNotNull(queue.snapshot()).occurrenceIds[1]
        assertTrue(queue.removeOccurrence(target))
        val first = requireNotNull(queue.snapshot())

        assertFalse(queue.removeOccurrence(target))
        assertSame(first, queue.snapshot())
        assertEquals(listOf(0, 2, 3, 4).map { songs[it].id }, first.tracks.map { it.id })
    }

    @Test fun nonExistentOccurrenceDoesNotChangeQueueRevisionOrPlayerTarget() {
        val queue = ProgressiveQueue()
        val songs = (0..4).map(::song)
        queue.start(songs, 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF)
        val before = requireNotNull(queue.snapshot())

        assertFalse(queue.removeOccurrence(UUID.randomUUID()))
        assertSame(before, queue.snapshot())
        assertEquals(before.revision, queue.snapshot()?.revision)
        assertEquals(songs[0].id, queue.snapshot()?.tracks?.get(0)?.id)
    }

    @Test fun removingCurrentOccurrenceStillSelectsItsSuccessor() {
        val songs = (0..3).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.ORDERED, RepeatMode.OFF)
        val current = requireNotNull(queue.snapshot()).occurrenceIds[0]

        assertTrue(queue.removeOccurrence(current))
        val after = requireNotNull(queue.snapshot())
        assertEquals(songs[1].id, after.tracks[after.currentIndex].id)
        assertFalse(after.occurrenceIds.contains(current))
    }
}
