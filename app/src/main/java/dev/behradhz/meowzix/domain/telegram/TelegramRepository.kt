package dev.behradhz.meowzix.domain.telegram

import kotlinx.coroutines.flow.StateFlow

sealed interface TelegramAuthStep {
    data object Initializing : TelegramAuthStep
    data object ConfigurationRequired : TelegramAuthStep
    data object WaitPhoneNumber : TelegramAuthStep
    data class WaitCode(val phoneNumber: String, val timeoutSeconds: Int) : TelegramAuthStep
    data class WaitPassword(
        val hint: String,
        val hasRecoveryEmail: Boolean,
        val recoveryEmailPattern: String,
    ) : TelegramAuthStep
    data object WaitEmailAddress : TelegramAuthStep
    data class WaitEmailCode(val emailPattern: String, val codeLength: Int) : TelegramAuthStep
    data object WaitRegistration : TelegramAuthStep
    data class WaitOtherDeviceConfirmation(val link: String) : TelegramAuthStep
    data object Ready : TelegramAuthStep
    data object LoggingOut : TelegramAuthStep
    data object Closing : TelegramAuthStep
    data object Closed : TelegramAuthStep
    data class Unsupported(val reason: String) : TelegramAuthStep
}

data class TelegramAuthState(
    val step: TelegramAuthStep = TelegramAuthStep.Initializing,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

/** Domain boundary that keeps TDLib types and threading out of UI consumers. */
interface TelegramRepository {
    val authState: StateFlow<TelegramAuthState>

    fun submitPhoneNumber(phoneNumber: String)
    fun submitCode(code: String)
    fun submitPassword(password: String)
    fun submitEmailAddress(emailAddress: String)
    fun submitEmailCode(code: String)
    fun register(firstName: String, lastName: String)
    fun logout()
    fun clearError()
}
