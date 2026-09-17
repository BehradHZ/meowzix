package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TdLibAuthorizationStateMapperTest {
    @Test
    fun `maps phone code and ready states`() {
        val codeInfo = TdApi.AuthenticationCodeInfo().apply {
            phoneNumber = "+491234"
            timeout = 30
        }

        assertSame(
            TelegramAuthStep.WaitPhoneNumber,
            TdApi.AuthorizationStateWaitPhoneNumber().toDomainStep(),
        )
        assertEquals(
            TelegramAuthStep.WaitCode(phoneNumber = "+491234", timeoutSeconds = 30),
            TdApi.AuthorizationStateWaitCode(codeInfo).toDomainStep(),
        )
        assertSame(TelegramAuthStep.Ready, TdApi.AuthorizationStateReady().toDomainStep())
    }

    @Test
    fun `maps two step verification without retaining a password`() {
        val step = TdApi.AuthorizationStateWaitPassword(
            "pet name",
            true,
            false,
            "m***@example.com",
        ).toDomainStep()

        assertEquals(
            TelegramAuthStep.WaitPassword(
                hint = "pet name",
                hasRecoveryEmail = true,
                recoveryEmailPattern = "m***@example.com",
            ),
            step,
        )
    }

    @Test
    fun `maps email registration link and terminal states`() {
        val emailInfo = TdApi.EmailAddressAuthenticationCodeInfo("m***@example.com", 6)

        assertSame(
            TelegramAuthStep.WaitEmailAddress,
            TdApi.AuthorizationStateWaitEmailAddress().toDomainStep(),
        )
        assertEquals(
            TelegramAuthStep.WaitEmailCode("m***@example.com", 6),
            TdApi.AuthorizationStateWaitEmailCode().apply { codeInfo = emailInfo }.toDomainStep(),
        )
        assertSame(
            TelegramAuthStep.WaitRegistration,
            TdApi.AuthorizationStateWaitRegistration().toDomainStep(),
        )
        assertEquals(
            TelegramAuthStep.WaitOtherDeviceConfirmation("tg://login"),
            TdApi.AuthorizationStateWaitOtherDeviceConfirmation("tg://login").toDomainStep(),
        )
        assertSame(TelegramAuthStep.LoggingOut, TdApi.AuthorizationStateLoggingOut().toDomainStep())
        assertSame(TelegramAuthStep.Closing, TdApi.AuthorizationStateClosing().toDomainStep())
        assertSame(TelegramAuthStep.Closed, TdApi.AuthorizationStateClosed().toDomainStep())
    }
}
