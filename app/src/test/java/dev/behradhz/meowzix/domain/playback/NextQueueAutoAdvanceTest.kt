package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Next lane is a committed sequence of occurrences. Automatic track
 * transitions consume its head; they do not invoke a new shuffle/permutation.
 */
class NextQueueAutoAdvanceTest {
    private fun song(index: Int): PlayableTrack = PlayableTrack(
        id = UUID.nameUUIDFromBytes("auto-next-$index".toByteArray()),
        title = "Track $index",
        artist = "Test artist",
        album = null,
        durationMs = 120_000L,
        artworkRef = null,
        contentUri = "content://test/$index",
    )

    private fun next(snapshot: ProgressiveQueueSnapshot): List<Pair<UUID, UUID>> =
        snapshot.tracks.indices.filter {
            it > snapshot.currentIndex && snapshot.origins[it] == QueueItemOrigin.GENERATED
        }.map { snapshot.occurrenceIds[it] to snapshot.tracks[it].id }

    @Test fun automaticTransitionsPreserveTheExactRemainingNextOrderInEveryPlaybackMode() {
        val songs = (0..24).map(::song)
        for (mode in PlaybackMode.entries) {
            val queue = ProgressiveQueue()
            queue.start(songs, 0, mode, RepeatMode.OFF, sourceIds = songs.map { it.id })
            val originallyCommitted = next(requireNotNull(queue.snapshot()))
            for (nextIndex in 1..7) {
                assertTrue(
                    "Failed to advance in $mode to $nextIndex",
                    queue.updateCurrentFromWindowIndex(nextIndex, songs[nextIndex].id.toString()),
                )
                val state = requireNotNull(queue.snapshot())
                assertEquals(songs[nextIndex].id, state.tracks[state.currentIndex].id)
                assertEquals(
                    "Automatic advancement must only remove the played Next head in $mode",
                    originallyCommitted.drop(nextIndex),
                    next(state),
                )
            }
        }
    }

    @Test fun playingFixedQueueEntriesDoesNotReshuffleOrConsumeGeneratedNext() {
        val songs = (0..24).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF,
            sourceIds = songs.map { it.id })
        assertTrue(queue.insertNext(songs[20]))
        assertTrue(queue.append(songs[21]))
        val committedNext = next(requireNotNull(queue.snapshot()))

        assertTrue(queue.updateCurrentFromWindowIndex(1, songs[20].id.toString()))
        queue.consumeCurrentFixed()
        assertEquals(committedNext, next(requireNotNull(queue.snapshot())))

        assertTrue(queue.updateCurrentFromWindowIndex(2, songs[21].id.toString()))
        queue.consumeCurrentFixed()
        assertEquals(committedNext, next(requireNotNull(queue.snapshot())))

        assertTrue(queue.updateCurrentFromWindowIndex(3, songs[1].id.toString()))
        assertEquals(committedNext.drop(1), next(requireNotNull(queue.snapshot())))
    }

    @Test fun lateSmartShuffleRankingCannotChangeVisibleNextAfterAutomaticTransition() {
        val songs = (0..24).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF,
            sourceIds = songs.map { it.id })
        assertTrue(queue.updateCurrentFromWindowIndex(1, songs[1].id.toString()))
        val current = requireNotNull(queue.snapshot())
        val before = next(current)

        val result = queue.rerankGeneratedFuture(
            rankedTrackIds = before.map { it.second }.reversed(),
            expectedRevision = current.revision,
        )
        assertEquals(SmartFutureRevisionResult.NO_CHANGE, result)
        assertEquals(before, next(requireNotNull(queue.snapshot())))
        assertEquals(current.revision, requireNotNull(queue.snapshot()).revision)
    }

    @Test fun sameModeRegenerationIsIgnoredButExplicitModeChangeCanReorder() {
        val songs = (0..14).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs, 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            seed = 1234L, sourceIds = songs.map { it.id })
        val before = next(requireNotNull(queue.snapshot()))

        queue.replaceFuture(songs.drop(1).reversed(), PlaybackMode.PURE_SHUFFLE, seed = 5678L)
        assertEquals(before, next(requireNotNull(queue.snapshot())))
        assertEquals(1234L, requireNotNull(queue.snapshot()).shuffleSeed)

        queue.replaceFuture(songs.drop(1).reversed(), PlaybackMode.ORDERED)
        assertEquals(songs.drop(1).reversed().map { it.id },
            next(requireNotNull(queue.snapshot())).map { it.second })
    }

    @Test fun repeatingCannotRegenerateWhileLogicalNextOrPendingTracksRemain() {
        val songs = (0..14).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs.take(10), 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.ALL,
            sourceIds = songs.map { it.id },
            pendingIds = songs.drop(10).map { it.id })
        assertFalse(requireNotNull(queue.snapshot()).mayBeginAnotherCycle())

        queue.jumpTo(9)
        assertFalse(requireNotNull(queue.snapshot()).mayBeginAnotherCycle())

        val batch = queue.claimGeneratedRefill()
        assertNotNull("An exhausted short window must claim its pending continuation", batch)
        assertTrue(queue.appendGeneratedBatch(requireNotNull(batch), songs.drop(10)))
        val appended = requireNotNull(queue.snapshot())
        assertEquals(songs.map { it.id }, appended.tracks.map { it.id })
        assertFalse(appended.mayBeginAnotherCycle())

        queue.jumpTo(songs.lastIndex)
        assertTrue(requireNotNull(queue.snapshot()).mayBeginAnotherCycle())
    }

    @Test fun refillAppendsWithoutTouchingAnyExistingNextOccurrence() {
        val songs = (0..109).map(::song)
        val queue = ProgressiveQueue()
        queue.start(songs.take(100), 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF,
            sourceIds = songs.map { it.id }, pendingIds = songs.drop(100).map { it.id })
        val before = next(requireNotNull(queue.snapshot()))

        queue.jumpTo(95)
        val batch = requireNotNull(queue.claimGeneratedRefill())
        assertTrue(queue.appendGeneratedBatch(batch, songs.drop(100)))

        val after = requireNotNull(queue.snapshot())
        val actual = after.tracks.indices.filter {
            after.origins[it] == QueueItemOrigin.GENERATED && it <= 99
        }.map { after.occurrenceIds[it] to after.tracks[it].id }
        assertEquals(before, actual)
        assertEquals(songs.drop(100).map { it.id }, after.tracks.takeLast(10).map { it.id })
    }
}
