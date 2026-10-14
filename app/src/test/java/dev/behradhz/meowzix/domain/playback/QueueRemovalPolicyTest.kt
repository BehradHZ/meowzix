package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRemovalPolicyTest {
    @Test
    fun `only removing the selected occurrence requires a player rebuild`() {
        val snapshot = queueSnapshot()

        assertEquals(QueueRemovalPlan.Current, planQueueRemoval(snapshot, 25))
        assertEquals(QueueRemovalPlan.LogicalOnly, planQueueRemoval(snapshot, 1))
        assertEquals(QueueRemovalPlan.LogicalOnly, planQueueRemoval(snapshot, 80))
        assertEquals(QueueRemovalPlan.Materialized(0), planQueueRemoval(snapshot, 22))
        assertEquals(QueueRemovalPlan.Materialized(4), planQueueRemoval(snapshot, 26))
        assertNull(planQueueRemoval(snapshot, -1))
        assertNull(planQueueRemoval(snapshot, 100))
    }

    private fun queueSnapshot(): ProgressiveQueueSnapshot {
        val tracks = List(100) { index ->
            PlayableTrack(
                id = UUID.nameUUIDFromBytes("queue-$index".toByteArray()),
                title = "Track $index",
                artist = "Artist",
                album = null,
                durationMs = 180_000,
                artworkRef = null,
                contentUri = "content://test/$index",
            )
        }
        val queue = ProgressiveQueue()
        queue.start(tracks, 25, PlaybackMode.ORDERED, RepeatMode.OFF)
        return requireNotNull(queue.snapshot())
    }
}
