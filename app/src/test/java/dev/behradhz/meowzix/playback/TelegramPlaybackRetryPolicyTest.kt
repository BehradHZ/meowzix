package dev.behradhz.meowzix.playback

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPlaybackRetryPolicyTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun replacingUriForSameOccurrenceKeepsRetryBudget() {
        assertTrue(keepTelegramPlaybackRetryForOccurrence("track", first, "track", first))
    }

    @Test fun aNewOccurrenceOfSameTrackCancelsOldRecovery() {
        assertFalse(keepTelegramPlaybackRetryForOccurrence("track", first, "track", second))
    }

    @Test fun aDifferentSongOrUnarmedRetryNeverSharesBudget() {
        assertFalse(keepTelegramPlaybackRetryForOccurrence("track", first, "other", first))
        assertFalse(keepTelegramPlaybackRetryForOccurrence(null, null, "track", null))
    }

    @Test fun legacyQueueWithoutOccurrenceStillKeepsItsOwnRetryBudget() {
        assertTrue(keepTelegramPlaybackRetryForOccurrence("track", null, "track", null))
    }
}
