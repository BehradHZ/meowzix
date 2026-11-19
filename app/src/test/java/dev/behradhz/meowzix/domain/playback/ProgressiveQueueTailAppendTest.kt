package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun `ordered queue retains unresolved future ids without hydrating every track`() {
        val queue = ProgressiveQueue()
        val initial = tracks(3)
        val future = tracks(60).drop(3).map { it.id }
        val allIds = initial.map { it.id } + future
        queue.start(
            initial, 0, PlaybackMode.ORDERED, RepeatMode.OFF,
            sourceIds = allIds, pendingIds = future,
        )
        val snapshot = requireNotNull(queue.snapshot())
        assertEquals(initial.size, snapshot.tracks.size)
        assertEquals(future, snapshot.pendingGeneratedIds)
        assertEquals(allIds, snapshot.sourceTrackIds)
    }

    @Test
    fun `attach future preserves playing occurrence and explicit next without reprepare`() {
        val queue = ProgressiveQueue()
        val selected = track("selected")
        val manual = track("manual-next")
        val future = tracks(4)
        queue.start(
            listOf(selected), 0, PlaybackMode.ORDERED, RepeatMode.OFF,
            sourceIds = listOf(selected.id),
        )
        assertTrue(queue.insertNext(manual))
        val before = requireNotNull(queue.snapshot())
        assertTrue(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id,
            future = future,
            sourceIds = listOf(selected.id) + future.map { it.id },
            pendingIds = listOf(track("later").id),
            mode = PlaybackMode.ORDERED,
        ))
        val after = requireNotNull(queue.snapshot())
        assertEquals(before.occurrenceIds.first(), after.occurrenceIds.first())
        assertEquals(selected.id, after.tracks[after.currentIndex].id)
        assertEquals(manual.id, after.tracks[1].id)
        assertEquals(future.map { it.id }, after.tracks.drop(2).map { it.id })
        assertEquals(listOf(track("later").id), after.pendingGeneratedIds)
        assertFalse(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id,
            future = future,
            sourceIds = listOf(selected.id),
            pendingIds = emptyList(),
            mode = PlaybackMode.ORDERED,
        ))
    }

    @Test
    fun `async shuffle tail ignores outdated occurrence but preserves seed on new session`() {
        val queue = ProgressiveQueue()
        val selected = track("same-track")
        val future = tracks(5)
        queue.start(listOf(selected), 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            sourceIds = listOf(selected.id))
        val stale = requireNotNull(queue.snapshot()).occurrenceIds.first()
        queue.start(listOf(selected), 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF,
            sourceIds = listOf(selected.id))
        val current = requireNotNull(queue.snapshot()).occurrenceIds.first()

        assertFalse(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id, expectedOccurrenceId = stale,
            future = future, sourceIds = listOf(selected.id) + future.map { it.id },
            pendingIds = emptyList(), mode = PlaybackMode.PURE_SHUFFLE, seed = 42L,
        ))
        assertEquals(listOf(selected.id), requireNotNull(queue.snapshot()).sourceTrackIds)
        assertTrue(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id, expectedOccurrenceId = current,
            future = future, sourceIds = listOf(selected.id) + future.map { it.id },
            pendingIds = emptyList(), mode = PlaybackMode.PURE_SHUFFLE, seed = 42L,
        ))
        val applied = requireNotNull(queue.snapshot())
        assertEquals(current, applied.occurrenceIds.first())
        assertEquals(42L, applied.shuffleSeed)
        assertEquals(future.map { it.id }, applied.tracks.drop(1).map { it.id })
    }

    @Test
    fun `async tail never overwrites a mode change`() {
        val queue = ProgressiveQueue()
        val selected = track("mode-selected")
        val future = tracks(3)
        queue.start(listOf(selected), 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF,
            sourceIds = listOf(selected.id))
        val originalOccurrence = requireNotNull(queue.snapshot()).occurrenceIds.first()
        queue.updatePlaybackMode(PlaybackMode.PURE_SHUFFLE)
        assertFalse(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id, expectedOccurrenceId = originalOccurrence,
            future = future, sourceIds = listOf(selected.id) + future.map { it.id },
            pendingIds = emptyList(), mode = PlaybackMode.SMART_SHUFFLE,
        ))
        assertEquals(PlaybackMode.PURE_SHUFFLE, requireNotNull(queue.snapshot()).playbackMode)
    }

    @Test
    fun `async pure and smart tails retain manual queue and chosen song`() {
        for (mode in listOf(PlaybackMode.PURE_SHUFFLE, PlaybackMode.SMART_SHUFFLE)) {
            val queue = ProgressiveQueue()
            val selected = track("anchor-$mode")
            val manuallyQueued = track("manual-$mode")
            val future = tracks(4)
            queue.start(listOf(selected), 0, mode, RepeatMode.OFF,
                sourceIds = listOf(selected.id))
            val selectedOccurrence = requireNotNull(queue.snapshot()).occurrenceIds.first()
            assertTrue(queue.insertNext(manuallyQueued))
            assertTrue(queue.attachFutureToSingleTrack(
                expectedTrackId = selected.id, expectedOccurrenceId = selectedOccurrence,
                future = future,
                sourceIds = listOf(selected.id) + future.map { it.id },
                pendingIds = emptyList(), mode = mode,
            ))
            val after = requireNotNull(queue.snapshot())
            assertEquals(selectedOccurrence, after.occurrenceIds.first())
            assertEquals(listOf(selected.id, manuallyQueued.id) + future.map { it.id },
                after.tracks.map { it.id })
            assertTrue(queue.isFixedOccurrence(1))
        }
    }

    @Test
    fun `initially known candidates survive fast mode changes and attach only once`() {
        val queue = ProgressiveQueue()
        val selected = track("full-context-start")
        val future = tracks(6)
        val all = listOf(selected.id) + future.map { it.id }
        queue.start(listOf(selected), 0, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF, sourceIds = all)
        val current = requireNotNull(queue.snapshot())
        assertEquals(all, current.sourceTrackIds)
        assertTrue(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id, expectedOccurrenceId = current.occurrenceIds.first(),
            future = future, sourceIds = all, pendingIds = emptyList(),
            mode = PlaybackMode.PURE_SHUFFLE, seed = 7L,
        ))
        val attached = requireNotNull(queue.snapshot())
        assertEquals(all, attached.sourceTrackIds)
        assertFalse(queue.attachFutureToSingleTrack(
            expectedTrackId = selected.id, expectedOccurrenceId = current.occurrenceIds.first(),
            future = future.asReversed(), sourceIds = all, pendingIds = emptyList(),
            mode = PlaybackMode.PURE_SHUFFLE, seed = 9L,
        ))
        assertEquals(future.map { it.id }, requireNotNull(queue.snapshot()).tracks.drop(1).map { it.id })
    }

    @Test
    fun `Library startup attaches 99 upcoming songs and refills every 90 in all modes`() {
        val all = tracks(320)
        val ids = all.map { it.id }
        for (mode in PlaybackMode.entries) {
            val queue = ProgressiveQueue()
            val selected = all.first()
            queue.start(
                listOf(selected), 0, mode, RepeatMode.OFF,
                sourceIds = listOf(selected.id),
            )
            val occurrence = requireNotNull(queue.snapshot()).occurrenceIds.first()
            assertTrue(queue.attachFutureToSingleTrack(
                expectedTrackId = selected.id,
                expectedOccurrenceId = occurrence,
                future = all.drop(1).take(99),
                sourceIds = ids,
                pendingIds = ids.drop(100),
                mode = mode,
            ))
            val initial = requireNotNull(queue.snapshot())
            assertEquals(mode, initial.playbackMode)
            assertEquals(100, initial.tracks.size)
            assertEquals(99, initial.tracks.drop(1).size)
            assertEquals(220, initial.pendingGeneratedIds.size)
            assertEquals(selected.id, initial.tracks.first().id)
            assertEquals(occurrence, initial.occurrenceIds.first())

            queue.jumpTo(89)
            assertEquals(null, queue.claimGeneratedRefill())
            queue.jumpTo(90)
            val second = requireNotNull(queue.claimGeneratedRefill())
            assertEquals(ids.drop(100).take(100), second.ids)
            assertEquals(null, queue.claimGeneratedRefill()) // Exactly one in-flight refill.
            assertTrue(queue.appendGeneratedBatch(second, all.drop(100).take(100)))
            assertEquals(200, requireNotNull(queue.snapshot()).tracks.size)

            queue.jumpTo(190)
            val third = requireNotNull(queue.claimGeneratedRefill())
            assertEquals(ids.drop(200).take(100), third.ids)
            assertTrue(queue.appendGeneratedBatch(third, all.drop(200).take(100)))

            queue.jumpTo(290)
            val last = requireNotNull(queue.claimGeneratedRefill())
            assertEquals(ids.drop(300), last.ids)
            assertTrue(queue.appendGeneratedBatch(last, all.drop(300)))
            val final = requireNotNull(queue.snapshot())
            assertEquals(320, final.tracks.size)
            assertEquals(320, final.tracks.map { it.id }.distinct().size)
            assertEquals(emptyList<UUID>(), final.pendingGeneratedIds)
        }
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
