package dev.behradhz.meowzix.domain.history

import dev.behradhz.meowzix.domain.playback.*
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningProgressCounterTest {
    private val playing = PlaybackState(status = PlaybackStatus.PLAYING,
        currentTrack = NowPlayingTrack(UUID(0, 1), "Track", null, null),
        playbackOccurrenceId = UUID(0, 2), positionMs = 1_000)

    @Test fun normalProgressCountsActualListening() {
        assertEquals(250L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 1_250)))
    }
    @Test fun seeksIncludingSmallExternalSeeksNeverEarnCompletion() {
        assertEquals(0L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 2_000, seekRevision = 1)))
        assertEquals(0L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 95_000)))
        assertEquals(0L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 2_000), true))
    }
    @Test fun pausesBufferingBackwardsSeeksAndNewOccurrencesDoNotCount() {
        assertEquals(0L, ListeningProgressCounter.delta(playing.copy(status = PlaybackStatus.PAUSED), playing.copy(positionMs = 1_250)))
        assertEquals(0L, ListeningProgressCounter.delta(playing.copy(status = PlaybackStatus.BUFFERING), playing.copy(positionMs = 1_250)))
        assertEquals(0L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 0)))
        assertEquals(0L, ListeningProgressCounter.delta(playing, playing.copy(positionMs = 1_250, playbackOccurrenceId = UUID(0, 3))))
    }
    @Test fun aShortTrackIsNotCompleteAtItsStart() {
        assertEquals(ListeningEventType.SKIPPED_EARLY, ListeningEventSemantics.outcome(0, 10_000, true))
        assertEquals(ListeningEventType.PLAY_COMPLETED, ListeningEventSemantics.outcome(9_000, 10_000, false))
        assertEquals(ListeningEventType.PLAY_STOPPED, ListeningEventSemantics.outcome(0, 0, false))
    }
}
