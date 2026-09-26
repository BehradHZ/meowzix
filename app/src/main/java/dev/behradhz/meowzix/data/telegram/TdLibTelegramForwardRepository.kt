package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TelegramSendDao
import dev.behradhz.meowzix.data.db.TelegramSendJobEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.telegram.TelegramSendEnqueueResult
import dev.behradhz.meowzix.domain.telegram.TelegramSendJob
import dev.behradhz.meowzix.domain.telegram.TelegramSendState
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi

@Singleton
class TdLibTelegramForwardRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val telegramDao: TelegramDao,
    private val sendDao: TelegramSendDao,
    private val libraryDao: LibraryDao,
    private val telegramRepository: TelegramRepository,
) : TelegramForwardRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val sendJobs: Flow<List<TelegramSendJob>> = sendDao.observeRecent().map { jobs ->
        jobs.map(TelegramSendJobEntity::toDomain)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleProcessing()
        }
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }

        scope.launch {
            sendDao.recoverInterrupted(
                errorMessage = "Delivery wasn't confirmed before Meowzix stopped. Check Telegram before retrying.",
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            processPendingSends()
        }

        scope.launch {
            telegramRepository.authState
                .map { it.step is TelegramAuthStep.Ready }
                .distinctUntilChanged()
                .collect { ready ->
                    if (ready) processPendingSends()
                }
        }
    }

    override fun initialize() {
        scheduleProcessing()
    }

    override suspend fun searchChats(query: String, limit: Int): List<TelegramChatSummary> {
        val activeClient = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not connected yet.")
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
            error("Reconnect Telegram to search chats.")
        }
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Telegram account information is still loading.")
        val currentUserId = accountId.toLongOrNull()
            ?: error("Telegram account information is invalid.")
        val safeLimit = limit.coerceIn(1, 100)
        val trimmed = query.trim()
        val ids = LinkedHashSet<Long>()

        if (trimmed.isEmpty()) {
            ids += activeClient.send(TdApi.GetChats(null, safeLimit)).chatIds.toList()
            runCatching { activeClient.send(TdApi.CreatePrivateChat(currentUserId, false)) }
                .getOrNull()
                ?.let { ids.add(it.id) }
        } else {
            trimmed.toLongOrNull()?.let { numericId ->
                runCatching { activeClient.send(TdApi.GetChat(numericId)) }
                    .getOrNull()
                    ?.let { ids.add(it.id) }

                if (numericId > 0L) {
                    runCatching { activeClient.send(TdApi.CreatePrivateChat(numericId, false)) }
                        .getOrNull()
                        ?.let { ids.add(it.id) }
                }
            }

            trimmed.telegramUsernameCandidate()?.let { username ->
                runCatching { activeClient.send(TdApi.SearchPublicChat(username)) }
                    .getOrNull()
                    ?.let { ids.add(it.id) }
            }

            ids += activeClient.send(TdApi.SearchChats(trimmed, null, safeLimit)).chatIds.toList()

            if (ids.size < safeLimit) {
                runCatching {
                    activeClient.send(TdApi.SearchChatsOnServer(trimmed, null, safeLimit - ids.size))
                }.getOrNull()?.chatIds?.let { ids += it.toList() }
            }
        }

        val selectedIds = telegramRepository.musicSourceState.value.selectedChatIds
        val result = ArrayList<TelegramChatSummary>(minOf(ids.size, safeLimit))
        for (chatId in ids.take(safeLimit)) {
            val chat = runCatching { activeClient.send(TdApi.GetChat(chatId)) }.getOrNull() ?: continue
            result += chat.toForwardSummary(currentUserId, chatId in selectedIds)
        }
        return result
    }

    override suspend fun enqueueTrack(
        trackId: UUID,
        targetChatId: Long,
        targetTitle: String?,
        options: TelegramForwardOptions,
    ): TelegramSendEnqueueResult {
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Connect Telegram before sending music.")
        val trackKey = trackId.toString()

        if (telegramDao.telegramSourceForTrackInChat(accountId, targetChatId, trackKey) != null ||
            sendDao.sentForTrackDestination(accountId, targetChatId, trackKey) != null
        ) {
            return TelegramSendEnqueueResult.AlreadyInChat(targetTitle)
        }

        val dedupeKey = telegramSendDedupeKey(accountId, targetChatId, trackKey)
        sendDao.activeByDedupeKey(dedupeKey)?.let {
            return TelegramSendEnqueueResult.AlreadyQueued(it.toDomain())
        }

        val now = System.currentTimeMillis()
        val job = TelegramSendJobEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            trackId = trackKey,
            targetChatId = targetChatId,
            targetTitle = targetTitle,
            state = TelegramSendState.QUEUED.name,
            progressPercent = 0,
            attemptCount = 0,
            includeSourceAttribution = options.includeSourceAttribution,
            keepCaption = options.keepCaption,
            activeDedupeKey = dedupeKey,
            sentMessageId = null,
            errorMessage = null,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )

        val inserted = sendDao.insert(job)
        if (inserted == -1L) {
            val existing = sendDao.activeByDedupeKey(dedupeKey)
                ?: error("This track is already queued for that Telegram chat.")
            return TelegramSendEnqueueResult.AlreadyQueued(existing.toDomain())
        }

        if (!hasNetwork()) {
            setState(job, TelegramSendState.WAITING_FOR_NETWORK, errorMessage = "Waiting for connection.")
        } else if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
            setState(job, TelegramSendState.WAITING_FOR_TELEGRAM, errorMessage = "Reconnect Telegram to continue.")
        }

        scheduleProcessing()
        return TelegramSendEnqueueResult.Queued((sendDao.jobById(job.id) ?: job).toDomain())
    }

    override suspend fun retrySend(jobId: UUID) {
        val changed = sendDao.retry(jobId.toString(), System.currentTimeMillis())
        if (changed == 0) error("This Telegram send can't be retried in its current state.")
        scheduleProcessing()
    }

    override suspend fun cancelSend(jobId: UUID) {
        val changed = sendDao.cancel(jobId.toString(), System.currentTimeMillis())
        if (changed == 0) error("This Telegram send is already being delivered or has finished.")
    }

    override suspend fun processPendingSends() {
        queueMutex.withLock {
            while (true) {
                val job = sendDao.nextRunnable() ?: return

                if (!hasNetwork()) {
                    setState(job, TelegramSendState.WAITING_FOR_NETWORK, errorMessage = "Waiting for connection.")
                    return
                }

                if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
                    setState(job, TelegramSendState.WAITING_FOR_TELEGRAM, errorMessage = "Reconnect Telegram to continue.")
                    return
                }

                val activeClient = TdLibClientAdapter.activeOrNull()
                if (activeClient == null) {
                    setState(job, TelegramSendState.WAITING_FOR_TELEGRAM, errorMessage = "Telegram is reconnecting.")
                    return
                }

                processOne(job, activeClient)
                val after = sendDao.jobById(job.id) ?: continue
                if (after.state == TelegramSendState.WAITING_FOR_NETWORK.name ||
                    after.state == TelegramSendState.WAITING_FOR_TELEGRAM.name
                ) {
                    return
                }
            }
        }
    }

    private fun scheduleProcessing() {
        scope.launch { processPendingSends() }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<TelegramSendQueueWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TELEGRAM_SEND_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun processOne(job: TelegramSendJobEntity, client: TdLibClientAdapter) {
        val attempt = job.attemptCount + 1
        setState(
            job,
            TelegramSendState.CHECKING,
            attemptCount = attempt,
            errorMessage = null,
        )

        try {
            val knownDestinationSource = telegramDao.telegramSourceForTrackInChat(
                job.accountId,
                job.targetChatId,
                job.trackId,
            )
            if (knownDestinationSource != null) {
                sendDao.markSent(job.id, knownDestinationSource.messageId, System.currentTimeMillis())
                return
            }

            sendDao.sentForTrackDestination(job.accountId, job.targetChatId, job.trackId)?.let { existing ->
                sendDao.markSent(job.id, existing.sentMessageId ?: 0L, System.currentTimeMillis())
                return
            }

            val target = client.send(TdApi.GetChat(job.targetChatId))
            val track = libraryDao.trackById(job.trackId)
                ?: error("This track is no longer in your Meowzix library.")
            val telegramSource = telegramDao.telegramSourceForTrack(job.accountId, job.trackId)

            val deliveredMessageId = if (telegramSource != null) {
                sendTelegramBackedTrack(client, job, target, telegramSource.chatId, telegramSource.messageId)
            } else {
                sendLocalTrack(client, job, track.title, track.artist, track.durationMs)
            }
            sendDao.markSent(job.id, deliveredMessageId, System.currentTimeMillis())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (uncertain: DeliveryUncertainException) {
            setState(
                job,
                TelegramSendState.VERIFYING,
                attemptCount = attempt,
                errorMessage = "Meowzix couldn't confirm delivery. Check Telegram before retrying so the song isn't sent twice.",
            )
        } catch (error: Throwable) {
            when {
                !hasNetwork() -> setState(
                    job,
                    TelegramSendState.WAITING_FOR_NETWORK,
                    attemptCount = attempt,
                    errorMessage = "Connection lost. The song is still queued.",
                )

                error is TdLibException && error.errorCode == 401 -> setState(
                    job,
                    TelegramSendState.WAITING_FOR_TELEGRAM,
                    attemptCount = attempt,
                    errorMessage = "Telegram session expired. Reconnect to continue.",
                )

                error.isTransientTelegramFailure() && attempt < MAX_AUTO_ATTEMPTS -> {
                    setState(
                        job,
                        TelegramSendState.RETRYING,
                        attemptCount = attempt,
                        errorMessage = "Temporary Telegram error. Retrying automatically.",
                    )
                    delay(1_000L * (1L shl (attempt - 1).coerceIn(0, 2)))
                }

                else -> sendDao.markFailed(
                    job.id,
                    userFacingTelegramError(error),
                    System.currentTimeMillis(),
                )
            }
        }
    }

    private suspend fun sendTelegramBackedTrack(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        target: TdApi.Chat,
        sourceChatId: Long,
        sourceMessageId: Long,
    ): Long {
        client.send(TdApi.GetMessage(sourceChatId, sourceMessageId))
        val properties = client.send(TdApi.GetMessageProperties(sourceChatId, sourceMessageId))
        val targetIsSecret = target.type is TdApi.ChatTypeSecret

        if (job.includeSourceAttribution) {
            if (targetIsSecret) {
                error("Telegram can't preserve source attribution in a secret chat. Turn off source attribution and try again.")
            }
            if (!properties.canBeForwarded) {
                error("The original Telegram message doesn't allow forwarding with source attribution.")
            }
        } else {
            val canCopy = if (targetIsSecret) properties.canBeCopiedToSecretChat else properties.canBeCopied
            if (!canCopy) {
                error("The original Telegram message doesn't allow its content to be copied to this chat.")
            }
        }

        setState(job, TelegramSendState.SENDING, progressPercent = 90, errorMessage = null)
        val result = client.send(
            TdApi.ForwardMessages(
                job.targetChatId,
                null,
                sourceChatId,
                longArrayOf(sourceMessageId),
                null,
                !job.includeSourceAttribution,
                !job.includeSourceAttribution && !job.keepCaption,
            ),
        )
        val pending = result.messages.firstOrNull()
            ?: error("Telegram didn't create an outgoing message for this track.")
        return awaitDelivery(client, pending, job.id, null)
    }

    private suspend fun sendLocalTrack(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        title: String,
        artist: String?,
        durationMs: Long,
    ): Long {
        setState(job, TelegramSendState.PREPARING_FILE, progressPercent = 2, errorMessage = null)
        val prepared = prepareLocalAudio(job)
        try {
            setState(job, TelegramSendState.UPLOADING, progressPercent = 5, errorMessage = null)
            val inputAudio = TdApi.InputAudio(
                TdApi.InputFileLocal(prepared.file.absolutePath),
                null,
                (durationMs / 1_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                title,
                artist.orEmpty(),
            )
            val content = TdApi.InputMessageAudio(
                inputAudio,
                TdApi.FormattedText("", null),
            )
            val pending = client.send(
                TdApi.SendMessage(
                    job.targetChatId,
                    null,
                    null,
                    null,
                    null,
                    content,
                ),
            )
            val fileId = (pending.content as? TdApi.MessageAudio)?.audio?.audio?.id
            return awaitDelivery(client, pending, job.id, fileId)
        } finally {
            if (prepared.temporary) runCatching { prepared.file.delete() }
        }
    }

    private suspend fun prepareLocalAudio(job: TelegramSendJobEntity): PreparedLocalAudio {
        val candidates = libraryDao.sourcesForTrack(job.trackId)
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .sortedBy(TrackSourceEntity::sendPriority)

        for (source in candidates) {
            source.localPath?.takeIf { it.isNotBlank() }?.let { path ->
                val file = File(path)
                if (file.isFile && file.canRead()) return PreparedLocalAudio(file, temporary = false)
            }

            source.contentUri?.takeIf { it.isNotBlank() }?.let { rawUri ->
                val uri = Uri.parse(rawUri)
                if (uri.scheme == "file") {
                    uri.path?.let(::File)?.takeIf { it.isFile && it.canRead() }?.let {
                        return PreparedLocalAudio(it, temporary = false)
                    }
                }

                val extension = source.safeExtension(uri)
                val cacheDir = File(context.cacheDir, "telegram-send").apply { mkdirs() }
                val output = File(cacheDir, "${job.id}.$extension")
                val copied = runCatching {
                    val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
                    input.use { inputStream ->
                        output.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                    }
                    true
                }.getOrDefault(false)
                if (copied && output.isFile && output.length() > 0L) {
                    return PreparedLocalAudio(output, temporary = true)
                }
                runCatching { output.delete() }
            }
        }
        error("This track isn't available as a readable local audio file, so Meowzix can't upload it to Telegram.")
    }

    private suspend fun awaitDelivery(
        client: TdLibClientAdapter,
        pending: TdApi.Message,
        jobId: String,
        uploadFileId: Int?,
    ): Long = coroutineScope {
        if (pending.sendingState == null) return@coroutineScope pending.id

        val progressJob = if (uploadFileId != null) {
            launch {
                client.updates.collect { update ->
                    if (update !is TdApi.UpdateFile || update.file.id != uploadFileId) return@collect
                    val file = update.file
                    val total = file.expectedSize.takeIf { it > 0L }
                    val uploaded = file.remote.uploadedSize
                    val progress = if (total != null) {
                        (5 + (uploaded.toDouble() / total.toDouble()).coerceIn(0.0, 1.0) * 88.0)
                            .roundToInt()
                            .coerceIn(5, 93)
                    } else {
                        5
                    }
                    sendDao.updateProgress(jobId, progress, System.currentTimeMillis())
                    if (file.remote.isUploadingCompleted) {
                        val current = sendDao.jobById(jobId)
                        if (current != null) {
                            setState(current, TelegramSendState.SENDING, progressPercent = 95, errorMessage = null)
                        }
                    }
                }
            }
        } else {
            null
        }

        try {
            val delivery = withTimeout(SEND_CONFIRMATION_TIMEOUT_MS) {
                client.updates.mapNotNull { update ->
                    when (update) {
                        is TdApi.UpdateMessageSendSucceeded -> if (update.oldMessageId == pending.id) {
                            DeliveryResult.Success(update.message.id)
                        } else {
                            null
                        }
                        is TdApi.UpdateMessageSendFailed -> if (update.oldMessageId == pending.id) {
                            DeliveryResult.Failure(update.error.code, update.error.message)
                        } else {
                            null
                        }
                        else -> null
                    }
                }.first()
            }
            when (delivery) {
                is DeliveryResult.Success -> delivery.messageId
                is DeliveryResult.Failure -> throw TdLibException(delivery.code, delivery.message)
            }
        } catch (_: TimeoutCancellationException) {
            throw DeliveryUncertainException()
        } finally {
            progressJob?.cancel()
        }
    }

    private suspend fun setState(
        job: TelegramSendJobEntity,
        state: TelegramSendState,
        progressPercent: Int = job.progressPercent,
        attemptCount: Int = job.attemptCount,
        errorMessage: String? = job.errorMessage,
    ) {
        sendDao.updateState(
            id = job.id,
            state = state.name,
            progressPercent = progressPercent.coerceIn(0, 100),
            attemptCount = attemptCount,
            errorMessage = errorMessage,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun hasNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun Throwable.isTransientTelegramFailure(): Boolean {
        if (this is TdLibException && (errorCode == 429 || errorCode in 500..599)) return true
        val normalized = message.orEmpty().uppercase()
        return normalized.contains("TIMEOUT") ||
            normalized.contains("NETWORK") ||
            normalized.contains("CONNECTION") ||
            normalized.contains("TEMPORAR")
    }

    private fun userFacingTelegramError(error: Throwable): String {
        val raw = error.message.orEmpty()
        val normalized = raw.uppercase()
        return when {
            normalized.contains("CHAT_WRITE_FORBIDDEN") -> "You can't send messages to this Telegram chat."
            normalized.contains("USER_BANNED_IN_CHANNEL") -> "You can't send messages to this Telegram chat."
            normalized.contains("CHAT_RESTRICTED") -> "Telegram doesn't allow sending audio to this chat."
            normalized.contains("FILE_TOO_BIG") -> "This audio file is too large for Telegram to send."
            raw.isNotBlank() -> raw
            else -> "Telegram couldn't send this track."
        }
    }

    private data class PreparedLocalAudio(val file: File, val temporary: Boolean)
    private class DeliveryUncertainException : Exception()

    private sealed interface DeliveryResult {
        data class Success(val messageId: Long) : DeliveryResult
        data class Failure(val code: Int, val message: String) : DeliveryResult
    }

    private companion object {
        const val MAX_AUTO_ATTEMPTS = 3
        const val SEND_CONFIRMATION_TIMEOUT_MS = 30L * 60L * 1_000L
        const val TELEGRAM_SEND_WORK_NAME = "telegram-send-queue"
    }
}

internal fun telegramSendDedupeKey(accountId: String, targetChatId: Long, trackId: String): String =
    "$accountId:$targetChatId:$trackId"

private fun TelegramSendJobEntity.toDomain(): TelegramSendJob = TelegramSendJob(
    id = UUID.fromString(id),
    trackId = UUID.fromString(trackId),
    targetChatId = targetChatId,
    targetTitle = targetTitle,
    state = TelegramSendState.valueOf(state),
    progressPercent = progressPercent,
    attemptCount = attemptCount,
    sentMessageId = sentMessageId,
    errorMessage = errorMessage,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

private fun TrackSourceEntity.sendPriority(): Int = when (type) {
    TrackSourceType.LOCAL_MEDIASTORE -> 0
    TrackSourceType.APP_OFFLINE_COPY -> 1
    TrackSourceType.TDLIB_LOCAL -> 2
    TrackSourceType.TELEGRAM_REMOTE -> 3
}

private fun TrackSourceEntity.safeExtension(uri: Uri): String {
    val fromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    if (!fromMime.isNullOrBlank()) return fromMime
    val fromPath = uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = "")
    return fromPath?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) } ?: "audio"
}

private fun String.telegramUsernameCandidate(): String? {
    val candidate = removePrefix("@").trim()
    if (candidate.isEmpty() || candidate.toLongOrNull() != null) return null
    return candidate.takeIf { value -> value.all { it.isLetterOrDigit() || it == '_' } }
}

private fun TdApi.Chat.toForwardSummary(
    currentUserId: Long,
    selected: Boolean,
): TelegramChatSummary = TelegramChatSummary(
    chatId = id,
    title = title.ifBlank { "Telegram chat" },
    kind = when (val chatType = type) {
        is TdApi.ChatTypePrivate -> if (chatType.userId == currentUserId) {
            TelegramChatKind.SAVED_MESSAGES
        } else {
            TelegramChatKind.PRIVATE
        }
        is TdApi.ChatTypeBasicGroup -> TelegramChatKind.BASIC_GROUP
        is TdApi.ChatTypeSupergroup -> TelegramChatKind.SUPERGROUP_OR_CHANNEL
        is TdApi.ChatTypeSecret -> TelegramChatKind.SECRET
        else -> TelegramChatKind.PRIVATE
    },
    selected = selected,
    profilePhotoRef = localPhotoRef(),
)

private fun TdApi.Chat.localPhotoRef(): String? {
    val photo = photo?.small ?: return null
    if (!photo.local.isDownloadingCompleted || photo.local.path.isBlank()) return null
    return File(photo.local.path).takeIf(File::isFile)?.let { Uri.fromFile(it).toString() }
}
