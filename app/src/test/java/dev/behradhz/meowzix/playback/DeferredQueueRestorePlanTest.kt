package dev.behradhz.meowzix.playback

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackItem
import dev.behradhz.meowzix.playback.persistence.PersistedPlaybackSession
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredQueueRestorePlanTest {
    private fun song(index: Int): UUID = UUID.nameUUIDFromBytes("restore-$index".toByteArray())
    private fun saved(ids: List<UUID>, center: Int = 0): PersistedPlaybackSession =
        PersistedPlaybackSession(
            items = listOf(PersistedPlaybackItem(ids[center].toString(), "content://song", "Song", null, null, 1000)),
            currentIndex = 0, positionMs = 130, playbackMode = PlaybackMode.SMART_SHUFFLE,
            repeatMode = RepeatMode.ALL, logicalMediaIds = ids.map(UUID::toString),
            logicalCurrentIndex = center,
        )

    @Test fun nearbyTracksComeFirstWithoutHydratingOtherLibraryRows() {
        val ids = (0 until 1000).map(::song)
        val plan = requireNotNull(DeferredQueueRestorePlan.from(saved(ids, 500)))
        assertEquals(ids, plan.logicalIds)
        assertEquals(listOf(ids[500]) + ids.subList(501, 511) +
            ids.subList(497, 500).reversed(), plan.batches.first())
        assertTrue(plan.batches.drop(1).all { it.size <= 100 })
        assertEquals(ids.toSet(), plan.batches.flatten().toSet())
        assertEquals(1000, plan.batches.flatten().size)
    }

    @Test fun duplicateQueueOccurrencesRemainButIdsAreQueriedOnlyOnce() {
        val ids = listOf(song(1), song(2), song(1), song(3), song(1))
        val plan = requireNotNull(DeferredQueueRestorePlan.from(saved(ids, 2)))
        assertEquals(ids, plan.logicalIds)
        assertEquals(3, plan.batches.flatten().size)
        assertEquals(3, plan.batches.flatten().distinct().size)
    }

    @Test fun malformedQueueIdsRejectRestoreRatherThanShiftOccurrenceIndices() {
        assertNull(DeferredQueueRestorePlan.from(
            saved(listOf(song(1))).copy(logicalMediaIds = listOf(song(1).toString(), "not-a-uuid")),
        ))
    }

    @Test fun editedMediaWindowCannotBeOverwrittenByStaleDeferredRestore() {
        val id = song(7).toString()
        val initial = saved(listOf(song(7))).copy(items = listOf(
            PersistedPlaybackItem(id, "content://original", "Song", null, null, 1000),
        ))
        assertTrue(persistedWindowMatches(initial, listOf(id to "content://original")))
        assertFalse(persistedWindowMatches(initial, listOf(id to "content://replacement")))
        assertFalse(persistedWindowMatches(initial, emptyList()))
    }
}
