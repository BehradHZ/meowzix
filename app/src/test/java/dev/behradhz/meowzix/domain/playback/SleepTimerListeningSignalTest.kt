package dev.behradhz.meowzix.domain.playback

import dev.behradhz.meowzix.domain.history.ListeningEventSemantics
import dev.behradhz.meowzix.domain.history.ListeningEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerListeningSignalTest {
    @Test
    fun timerStopUsesNonIntentionalOutcomeAndNeverBecomesEarlySkip() {
        val neutral = ListeningEventSemantics.outcome(
            positionMs = 12_000,
            durationMs = 240_000,
            intentionalSkip = false,
        )
        val userSkip = ListeningEventSemantics.outcome(
            positionMs = 12_000,
            durationMs = 240_000,
            intentionalSkip = true,
        )
        assertEquals(ListeningEventType.PLAY_STOPPED, neutral)
        assertEquals(ListeningEventType.SKIPPED_EARLY, userSkip)
    }
}
