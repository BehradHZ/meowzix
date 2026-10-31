package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioAnalysisPlanTest {
    @Test fun currentAndAtMostTwoDistinctSuccessorsAreSelectedInQueueOrder() {
        val ids = (1L..10L).map { UUID(0L, it) }
        assertEquals(listOf(ids[0], ids[1], ids[2]),
            PlaybackAudioAnalysisPlan.select(ids[0], ids.drop(1).asSequence()))
        assertEquals(listOf(ids[0], ids[1], ids[2]),
            PlaybackAudioAnalysisPlan.select(ids[0], sequenceOf(ids[0], ids[1], ids[1], ids[2], ids[3])))
    }

    @Test fun noActiveTrackSchedulesNothingAndSmallQueueStaysSmall() {
        val id = UUID(0L, 1L)
        assertTrue(PlaybackAudioAnalysisPlan.select(null, sequenceOf(id)).isEmpty())
        assertEquals(listOf(id), PlaybackAudioAnalysisPlan.select(id, emptySequence()))
    }
}
