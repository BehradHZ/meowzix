package dev.behradhz.meowzix.domain.telegram

import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import java.util.UUID
import kotlinx.coroutines.flow.Flow
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

data class TelegramForwardOptions(
    val includeSourceAttribution: Boolean = true,
    val keepCaption: Boolean = true,
)

enum class TelegramSendState {
    QUEUED,
    CHECKING,
    WAITING_FOR_NETWORK,
    WAITING_FOR_TELEGRAM,
    PREPARING_FILE,
    UPLOADING,
    SENDING,
    VERIFYING,
    RETRYING,
    SENT,
    FAILED,
    CANCELED,
}

data class TelegramSendJob(
    val id: UUID,
    val trackId: UUID,
    val targetChatId: Long,
    val targetTitle: String?,
    val state: TelegramSendState,
    val progressPercent: Int = 0,
    val attemptCount: Int = 0,
    val sentMessageId: Long? = null,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    val isTerminal: Boolean
        get() = state == TelegramSendState.SENT || state == TelegramSendState.CANCELED
}

sealed interface TelegramSendEnqueueResult {
    data class Queued(val job: TelegramSendJob) : TelegramSendEnqueueResult
    data class AlreadyQueued(val job: TelegramSendJob) : TelegramSendEnqueueResult
    data class AlreadyInChat(val targetTitle: String?) : TelegramSendEnqueueResult
}

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

    /** Restart the TDLib process client without logging out or deleting imported/local music. */
    fun resetClient() = TdLibClientAdapter.resetActive()

    fun clearError()

    fun refreshSelectableChats()
    fun setMusicSourceSelected(chatId: Long, selected: Boolean)
    fun syncSelectedSources()
    suspend fun trackIdsForChat(chatId: Long): List<UUID>
    fun clearMusicSourceError()
}

interface TelegramForwardRepository {
    val sendJobs: Flow<List<TelegramSendJob>>

    fun initialize()

    suspend fun searchChats(query: String, limit: Int = 50): List<TelegramChatSummary>

    suspend fun enqueueTrack(
        trackId: UUID,
        targetChatId: Long,
        targetTitle: String? = null,
        options: TelegramForwardOptions = TelegramForwardOptions(),
    ): TelegramSendEnqueueResult

    suspend fun retrySend(jobId: UUID)
    suspend fun cancelSend(jobId: UUID)
    suspend fun processPendingSends()
}

fun telegramPlaylistId(accountId: String, chatId: Long): UUID =
    UUID.nameUUIDFromBytes("telegram-playlist:$accountId:$chatId".toByteArray())
