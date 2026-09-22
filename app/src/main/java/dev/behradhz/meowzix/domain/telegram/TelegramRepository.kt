package dev.behradhz.meowzix.domain.telegram

import java.util.UUID
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

enum class TelegramChatKind { SAVED_MESSAGES, PRIVATE, BASIC_GROUP, SUPERGROUP_OR_CHANNEL, SECRET }

data class TelegramChatSummary(
    val chatId: Long,
    val title: String,
    val kind: TelegramChatKind,
    val selected: Boolean,
    val profilePhotoRef: String? = null,
)

data class TelegramSyncResult(
    val chatsSynced: Int,
    val messagesScanned: Int,
    val tracksImported: Int,
    val tracksUpdated: Int,
    val sourcesMarkedMissing: Int,
)

data class TelegramMusicSourceState(
    val accountId: String? = null,
    val chats: List<TelegramChatSummary> = emptyList(),
    val selectedChatIds: Set<Long> = emptySet(),
    val isLoadingChats: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncResult: TelegramSyncResult? = null,
    val errorMessage: String? = null,
)

/**
 * Controls how a Telegram-backed track is sent to another Telegram chat.
 *
 * `includeSourceAttribution = true` performs a real Telegram forward and therefore keeps the
 * original sender/source information. Telegram only allows caption removal when sending a copy,
 * so `keepCaption` is ignored by TDLib while source attribution is retained.
 */
data class TelegramForwardOptions(
    val includeSourceAttribution: Boolean = true,
    val keepCaption: Boolean = true,
)

/** Domain boundary that keeps TDLib types and threading out of UI consumers. */
interface TelegramRepository {
    val authState: StateFlow<TelegramAuthState>
    val musicSourceState: StateFlow<TelegramMusicSourceState>

    fun submitPhoneNumber(phoneNumber: String)
    fun submitCode(code: String)
    fun submitPassword(password: String)
    fun submitEmailAddress(emailAddress: String)
    fun submitEmailCode(code: String)
    fun register(firstName: String, lastName: String)
    fun logout()
    fun clearError()

    fun refreshSelectableChats()
    fun setMusicSourceSelected(chatId: Long, selected: Boolean)
    fun syncSelectedSources()
    suspend fun trackIdsForChat(chatId: Long): List<UUID>
    fun clearMusicSourceError()
}

/**
 * Telegram message forwarding is deliberately its own capability. This keeps importing/syncing
 * music independent from outbound messaging and lets UI hide the action when Telegram is absent.
 */
interface TelegramForwardRepository {
    /**
     * Returns chats suitable for a Telegram-style forward picker. Implementations should prefer
     * TDLib's local chat index so typing stays immediate and may supplement it with server search.
     */
    suspend fun searchChats(query: String, limit: Int = 50): List<TelegramChatSummary>

    /**
     * Forwards the Telegram message backing [trackId] to [targetChatId]. Local-only tracks are not
     * uploaded as substitutes: this operation intentionally preserves Telegram message identity.
     */
    suspend fun forwardTrack(
        trackId: UUID,
        targetChatId: Long,
        options: TelegramForwardOptions = TelegramForwardOptions(),
    )
}

fun telegramPlaylistId(accountId: String, chatId: Long): UUID =
    UUID.nameUUIDFromBytes("telegram-playlist:$accountId:$chatId".toByteArray())
