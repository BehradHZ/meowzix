package dev.behradhz.meowzix.domain.telegram

/** A TDLib restart is a last resort, not a substitute for network reconnection. */
internal fun telegramAuthRecoveryTimeoutMs(state: TelegramAuthState): Long? = when {
    state.step is TelegramAuthStep.ConfigurationRequired ||
        state.step is TelegramAuthStep.Ready ||
        state.step is TelegramAuthStep.LoggingOut -> null
    state.isSubmitting -> 60_000L
    state.step is TelegramAuthStep.Initializing ||
        state.step is TelegramAuthStep.Closing ||
        state.step is TelegramAuthStep.Closed -> 45_000L
    // The phone form is safe to preserve through a transport restart.
    state.step is TelegramAuthStep.WaitPhoneNumber &&
        state.connectionStatus != TelegramConnectionStatus.CONNECTED -> 90_000L
    // Do not invalidate an outstanding Telegram code, email challenge, or 2FA input.
    else -> null
}
