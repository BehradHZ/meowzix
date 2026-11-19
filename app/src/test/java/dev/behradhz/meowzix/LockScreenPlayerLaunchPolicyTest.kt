package dev.behradhz.meowzix

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenPlayerLaunchPolicyTest {
    @Test fun enabledAndAuthorizedDuringLockedPlayback() {
        assertTrue(shouldOpenLockScreenPlayer(true, true, true, true))
    }

    @Test fun cannotOpenWithoutConsentPermissionLockOrPlayback() {
        assertFalse(shouldOpenLockScreenPlayer(false, true, true, true))
        assertFalse(shouldOpenLockScreenPlayer(true, false, true, true))
        assertFalse(shouldOpenLockScreenPlayer(true, true, false, true))
        assertFalse(shouldOpenLockScreenPlayer(true, true, true, false))
    }
}
