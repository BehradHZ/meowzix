package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ModeSwitchCandidatesTest {
    @Test fun `original playlist order survives mode switches without repeating history or Queue`() {
        val source = (0 until 12).map { UUID.nameUUIDFromBytes("track-$it".toByteArray()) }
        val queue = ProgressiveQueue()
        val played = listOf(source[8], source[3], source[6], source[9], source[1])
            .map(::track)
        queue.start(played, 2, PlaybackMode.PURE_SHUFFLE, RepeatMode.OFF, sourceIds = source)
        queue.insertNext(track(source[10]))
        val snapshot = requireNotNull(queue.snapshot())
        val candidates = ModeSwitchCandidates.eligible(snapshot, snapshot.sourceTrackIds)
        for (skip in listOf(8, 3, 6, 10)) assertFalse(source[skip] in candidates)
        assertEquals(
            listOf(7, 9, 11, 0, 1, 2, 4, 5).map(source::get),
            ModeSwitchCandidates.orderedAfterCurrent(source, source[6], candidates),
        )
    }

    @Test fun `large source list is ordered without duplicate IDs or track hydration`() {
        val source = (0 until 5000).map { UUID.nameUUIDFromBytes("large-$it".toByteArray()) }
        val queue = ProgressiveQueue()
        queue.start(listOf(track(source[3000])), 0, PlaybackMode.SMART_SHUFFLE, RepeatMode.OFF, sourceIds = source)
        val allowed = ModeSwitchCandidates.eligible(requireNotNull(queue.snapshot()), source)
        val ordered = ModeSwitchCandidates.orderedAfterCurrent(source, source[3000], allowed)
        assertEquals(4999, ordered.size)
        assertEquals(4999, ordered.distinct().size)
        assertEquals(source[3001], ordered.first())
        assertEquals(source[2999], ordered.last())
    }

    private fun track(id: UUID) = PlayableTrack(id, "Track", "Artist", null, 1000L, null, "content://test")
}
