package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTelegramTrackPolicyTest {
    @Test fun baselineIsNeverNew() {
        assertFalse(NewTelegramTrackPolicy.isNewAfterBaseline(false, null, 100))
        assertFalse(NewTelegramTrackPolicy.isNewAfterBaseline(false, null, -4))
    }

    @Test fun onlyPostCheckpointMessagesAreNew() {
        assertTrue(NewTelegramTrackPolicy.isNewAfterBaseline(true, 100, 101))
        assertFalse(NewTelegramTrackPolicy.isNewAfterBaseline(true, 100, 100))
        assertFalse(NewTelegramTrackPolicy.isNewAfterBaseline(true, 100, 99))
    }

    @Test fun newProfileAudioAfterBaselineIsNew() {
        assertTrue(NewTelegramTrackPolicy.isNewAfterBaseline(true, 100, -42))
    }
}
