package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueCurrentIndexTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val items = listOf(first, second, first).map { QueueItem(it, "Track", null, null) }
    private val playing = PlaybackState(currentTrack = NowPlayingTrack(first, "Track", null, null))

    @Test fun prefersPlaybackIndexEvenForRepeatedTrackIds() {
        assertEquals(2, resolveCurrentQueueIndex(playing.copy(queueIndex = 2),
            QueueState(items = items, currentIndex = 0)))
    }

    @Test fun fallsBackToQueueIndexWhenPlaybackIndexMismatches() {
        assertEquals(2, resolveCurrentQueueIndex(playing.copy(queueIndex = 1),
            QueueState(items = items, currentIndex = 2)))
    }

    @Test fun fallsBackToFirstMatchingItem() {
        assertEquals(0, resolveCurrentQueueIndex(playing.copy(queueIndex = 9),
            QueueState(items = items, currentIndex = -1)))
    }

    @Test fun missingTrackAndMissingQueueReturnMinusOne() {
        assertEquals(-1, resolveCurrentQueueIndex(PlaybackState(), QueueState(items = items)))
        assertEquals(-1, resolveCurrentQueueIndex(playing, QueueState(items = listOf(
            QueueItem(second, "Other", null, null)))))
    }
}
