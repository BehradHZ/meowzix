package dev.behradhz.meowzix.domain.history

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningEventSemanticsTest {
    @Test fun `ninety percent is completion`() =
        assertEquals(ListeningEventType.PLAY_COMPLETED, ListeningEventSemantics.outcome(90_000, 100_000, true))

    @Test fun `skip before thirty seconds and twenty percent is early`() =
        assertEquals(ListeningEventType.SKIPPED_EARLY, ListeningEventSemantics.outcome(10_000, 100_000, true))

    @Test fun `meaningful intentional skip is late`() =
        assertEquals(ListeningEventType.SKIPPED_LATE, ListeningEventSemantics.outcome(40_000, 100_000, true))

    @Test fun `time bucket uses supplied local zone`() {
        val context = ListeningEventSemantics.timeContext(
            Instant.parse("2026-09-21T18:30:00Z"),
            ZoneId.of("Europe/Berlin"),
        )

        assertEquals(20, context.localHour)
        assertEquals(TimeBucket.EVENING, context.bucket)
    }
}
