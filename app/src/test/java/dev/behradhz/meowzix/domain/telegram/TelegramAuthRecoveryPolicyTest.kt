package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelegramAuthRecoveryPolicyTest {
    @Test fun `stuck startup is eligible for bounded retry`() {
        assertEquals(45_000L, telegramAuthRecoveryTimeoutMs(TelegramAuthState()))
    }

    @Test fun `input steps are not reset while user is verifying`() {
        val connected = TelegramConnectionStatus.CONNECTED
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitCode("+989123456789", 60),
            connectionStatus = connected,
        )))
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitPassword("", false, ""),
        )))
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitEmailAddress,
        )))
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.Ready,
            isSubmitting = true,
        )))
    }

    @Test fun `stuck pending request is recoverable but ready phone input is not`() {
        assertEquals(60_000L, telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitCode("+989123456789", 60),
            isSubmitting = true,
        )))
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitPhoneNumber,
            connectionStatus = TelegramConnectionStatus.CONNECTED,
        )))
        assertEquals(90_000L, telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.WaitPhoneNumber,
            connectionStatus = TelegramConnectionStatus.CONNECTING,
        )))
        assertNull(telegramAuthRecoveryTimeoutMs(TelegramAuthState(
            step = TelegramAuthStep.ConfigurationRequired,
        )))
    }
}
