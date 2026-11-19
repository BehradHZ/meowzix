package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramStreamIdentityTest {
    @Test fun `only complete file identities become queue streaming sources`() {
        assertTrue(canBuildTelegramStream(42, "stable-identity"))
        assertFalse(canBuildTelegramStream(42, null))
        assertFalse(canBuildTelegramStream(42, ""))
        assertFalse(canBuildTelegramStream(null, "stable-identity"))
        assertFalse(canBuildTelegramStream(0, "stable-identity"))
    }

    @Test fun `accepts the original file despite a new numeric TDLib id`() {
        assertTrue(matchesTelegramFileIdentity("stable-file-1", "stable-file-1"))
    }

    @Test fun `rejects a recycled TDLib id belonging to another track`() {
        assertFalse(matchesTelegramFileIdentity("stable-file-1", "stable-file-2"))
    }

    @Test fun `unverified files are never treated as a matching audio source`() {
        assertFalse(matchesTelegramFileIdentity(null, "stable-file-1"))
        assertFalse(matchesTelegramFileIdentity("", "stable-file-1"))
        assertFalse(matchesTelegramFileIdentity("stable-file-1", null))
        assertFalse(matchesTelegramFileIdentity("stable-file-1", ""))
    }
}
