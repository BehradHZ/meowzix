package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Safe, actionable feedback. Never show raw TDLib errors, which may contain private details. */
internal data class TelegramErrorGuidance(val message: String, val retryable: Boolean)

internal fun telegramErrorGuidance(error: Throwable): TelegramErrorGuidance {
    val code = (error as? TdLibException)?.errorCode
    val raw = error.message.orEmpty().uppercase()
    fun guidance(message: String, retryable: Boolean = false) =
        TelegramErrorGuidance(message, retryable)

    val floodSeconds = Regex("FLOOD_WAIT[_ ]?(\\d+)").find(raw)
        ?.groupValues?.getOrNull(1)?.toLongOrNull()
    if (floodSeconds != null || code == 429) {
        val wait = floodSeconds?.let { " Wait at least ${(it + 59L) / 60L} minute(s)." }.orEmpty()
        return guidance("Telegram has temporarily limited requests.$wait Try again later.")
    }

    return when {
        "PHONE_CODE_INVALID" in raw || "EMAIL_CODE_INVALID" in raw ->
            guidance("That verification code is incorrect. Use the latest code Telegram sent.")
        "PHONE_CODE_EXPIRED" in raw || "EMAIL_CODE_EXPIRED" in raw ->
            guidance("Your verification code expired. Request a new one from Telegram.")
        "PASSWORD_HASH_INVALID" in raw || "PASSWORD_INVALID" in raw ->
            guidance("The Telegram two-step verification password is incorrect.")
        "PHONE_NUMBER_INVALID" in raw ->
            guidance("Enter a valid phone number with its country code.")
        code == 401 || "AUTH_KEY_UNREGISTERED" in raw || "UNAUTHORIZED" in raw ->
            guidance("Your Telegram session is no longer authorized. Sign in again; saved music stays on this device.")
        "CHANNEL_PRIVATE" in raw || "CHAT_ADMIN_REQUIRED" in raw ||
            "CHAT_FORBIDDEN" in raw || "USER_BANNED_IN_CHANNEL" in raw ->
            guidance("This Telegram chat is unavailable or access was removed. Check its permissions or choose another music source.")
        "FILE_REFERENCE" in raw ->
            guidance("Telegram's file reference expired. Refresh the chat and retry the download.")
        "NO_SPACE" in raw || "ENOSPC" in raw || "DISK_FULL" in raw ->
            guidance("There is not enough free storage to finish this Telegram request. Free space and retry.")
        code != null && code in setOf(500, 502, 503, 504) ->
            guidance("Telegram's servers are temporarily unavailable. Try again shortly; safe reads retry automatically.", true)
        "NETWORK" in raw || "CONNECTION" in raw || "TIMEOUT" in raw ||
            "TIMED OUT" in raw || "SOCKET" in raw || "TRANSPORT" in raw ->
            guidance("Can't reach Telegram. Check your internet or proxy; the app will retry safe reads.", true)
        else -> guidance("Telegram could not complete this request. Check the connection and try again.")
    }
}

/**
 * Only retry idempotent reads (GetChats/GetChat/GetMe); never retry sign-in
 * codes, password checks, chat selection writes, or sync side effects here.
 */
internal suspend fun <T> retryTransientTelegramRead(
    attempts: Int = 3,
    wait: suspend (Long) -> Unit = { delay(it) },
    operation: suspend () -> T,
): T {
    require(attempts >= 1)
    repeat(attempts - 1) { retry ->
        try {
            return operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!telegramErrorGuidance(error).retryable) throw error
            wait(750L * (1L shl retry.coerceAtMost(3)))
        }
    }
    return operation()
}
