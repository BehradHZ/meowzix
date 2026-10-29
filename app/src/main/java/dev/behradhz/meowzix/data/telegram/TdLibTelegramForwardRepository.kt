package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
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
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
    private val folderLoadMutex = Mutex()
    private val folderCache = ForwardFolderMemoryCache<TelegramForwardFolder>()
    private val folderRequestLock = Any()
    private var inFlightFolderRequest: Deferred<List<TelegramForwardFolder>>? = null
    private val folderChatCaches = mutableMapOf<Int, ForwardFolderMemoryCache<TelegramChatSummary>>()
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
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not connected yet.")
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
            error("Reconnect Telegram to search chats.")
        }
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Telegram account information is still loading.")
        val userId = accountId.toLongOrNull()
            ?: error("Telegram account information is invalid.")
        val safeLimit = limit.coerceIn(1, 50)
        val term = query.trim()
        val ids = LinkedHashSet<Long>()

        if (term.isEmpty()) {
            // The landing panel never walks Telegram's entire chat history.
            ids.addAll(client.send(TdApi.GetChats(null, safeLimit)).chatIds.toList())
            withTimeoutOrNull(400L) {
                runCatching { client.send(TdApi.CreatePrivateChat(userId, false)) }.getOrNull()
            }?.let { ids.add(it.id) }
        } else {
            // Do the TDLib local index search first. Previous code awaited two
            // remote lookups before this, even for an ordinary title query.
            ids.addAll(client.send(TdApi.SearchChats(term, null, safeLimit)).chatIds.toList())

            // Network discovery remains available for chats not in TDLib's local index,
            // but never stalls every keystroke and never runs for one-letter searches.
            if (ids.isEmpty() && term.length >= 3) {
                coroutineScope {
                    val server = async {
                        withTimeoutOrNull(1_200L) {
                            runCatching { client.send(TdApi.SearchChatsOnServer(term, null, safeLimit)) }
                                .getOrNull()?.chatIds?.toList().orEmpty()
                        }.orEmpty()
                    }
                    val explicitUsername = async {
                        term.telegramUsernameCandidate()?.let { username ->
                            withTimeoutOrNull(1_200L) {
                                runCatching { client.send(TdApi.SearchPublicChat(username)) }.getOrNull()
                            }
                        }
                    }
                    ids.addAll(server.await())
                    explicitUsername.await()?.let { ids.add(it.id) }
                }
            } else if (term.startsWith("@")) {
                // Exact username lookup should still be possible when local names match.
                withTimeoutOrNull(750L) {
                    runCatching {
                        client.send(TdApi.SearchPublicChat(term.removePrefix("@")))
                    }.getOrNull()
                }?.let { ids.add(it.id) }
            }
            term.toLongOrNull()?.let { numericId ->
                withTimeoutOrNull(500L) {
                    runCatching { client.send(TdApi.GetChat(numericId)) }.getOrNull()
                }?.let { ids.add(it.id) }
            }
        }
        return resolveForwardChats(
            client = client,
            chatIds = ids.take(safeLimit),
            currentUserId = userId,
            selectedIds = telegramRepository.musicSourceState.value.selectedChatIds,
        )
    }

    /** Bounded parallel TDLib metadata reads preserve search/list ordering. */
    private suspend fun resolveForwardChats(
        client: TdLibClientAdapter,
        chatIds: List<Long>,
        currentUserId: Long,
        selectedIds: Set<Long>,
    ): List<TelegramChatSummary> = coroutineScope {
        val slots = Semaphore(8)
        chatIds.map { id ->
            async {
                slots.withPermit {
                    runCatching { client.send(TdApi.GetChat(id)) }.getOrNull()
                        ?.toForwardSummary(currentUserId, id in selectedIds)
                }
            }
        }.awaitAll().filterNotNull()
    }

    override fun cachedForwardFolders(): List<TelegramForwardFolder>? {
        val client = TdLibClientAdapter.activeOrNull() ?: return null
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) return null
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        return folderCache.snapshot(
            accountId = accountId,
            session = client,
            revision = client.chatFolderRevision,
            nowMillis = SystemClock.elapsedRealtime(),
        )
    }

    override suspend fun loadForwardFolders(): List<TelegramForwardFolder> =
        fetchForwardFolders(forceRefresh = false)

    override suspend fun refreshForwardFolders(): List<TelegramForwardFolder> =
        fetchForwardFolders(forceRefresh = true)

    /**
     * The singleton repository owns the load: dismissing the UI must not cancel
     * the first TDLib fetch and cause another fetch on the next picker opening.
     * Concurrent pickers share one in-flight request.
     */
    private suspend fun fetchForwardFolders(forceRefresh: Boolean): List<TelegramForwardFolder> {
        if (!forceRefresh) cachedForwardFolders()?.let { return it }
        val request = synchronized(folderRequestLock) {
            inFlightFolderRequest?.takeIf { it.isActive }
                ?: scope.async { loadFolderSnapshot(forceRefresh) }.also { inFlightFolderRequest = it }
        }
        return request.await()
    }

    private suspend fun loadFolderSnapshot(forceRefresh: Boolean): List<TelegramForwardFolder> =
        folderLoadMutex.withLock {
            if (!forceRefresh) cachedForwardFolders()?.let { return@withLock it }
            val client = TdLibClientAdapter.activeOrNull() ?: return@withLock emptyList()
            if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
                return@withLock emptyList()
            }
            val accountId = telegramRepository.musicSourceState.value.accountId
                ?: return@withLock emptyList()
            val infos = awaitTelegramFolderSnapshot(client.chatFolders, timeoutMs = 1_500L)
            val folderRevision = client.chatFolderRevision
            // Publish names immediately. No GetChatFolder / LoadChats / GetChat fanout here.
            val folders = infos.map { info ->
                TelegramForwardFolder(
                    info.id,
                    info.name?.text?.text?.ifBlank { "Folder" } ?: "Folder",
                    emptyList(),
                )
            }
            // Do not publish data from a stale client/account or from a folder
            // revision that changed while the expensive fetch was in progress.
            if (TdLibClientAdapter.activeOrNull() === client &&
                telegramRepository.authState.value.step is TelegramAuthStep.Ready &&
                telegramRepository.musicSourceState.value.accountId == accountId &&
                client.chatFolderRevision == folderRevision
            ) {
                // A blank snapshot may mean UpdateChatFolders has not arrived yet.
                // Do not cache that transient absence for five minutes.
                if (folders.isNotEmpty()) {
                    folderCache.save(
                        accountId = accountId,
                        session = client,
                        revision = folderRevision,
                        nowMillis = SystemClock.elapsedRealtime(),
                        folders = folders,
                    )
                }
                if (forceRefresh) synchronized(folderRequestLock) { folderChatCaches.clear() }
            }
            folders
        }

    override fun cachedForwardFolderChats(folderId: Int): List<TelegramChatSummary>? {
        val client = TdLibClientAdapter.activeOrNull() ?: return null
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) return null
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        return synchronized(folderRequestLock) {
            // An empty value can be a TDLib list still synchronizing. Never treat
            // a cached empty snapshot as proof that a folder has no destinations.
            folderChatCaches[folderId]?.snapshot(
                accountId, client, client.chatFolderRevision, SystemClock.elapsedRealtime(),
            )?.takeIf(List<TelegramChatSummary>::isNotEmpty)
        }
    }

    override fun cachedForwardChat(chatId: Long): TelegramChatSummary? {
        val client = TdLibClientAdapter.activeOrNull() ?: return null
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        return synchronized(folderRequestLock) {
            folderChatCaches.values.asSequence().mapNotNull { cache ->
                cache.snapshot(accountId, client, client.chatFolderRevision, SystemClock.elapsedRealtime())
                    ?.firstOrNull { it.chatId == chatId }
            }.firstOrNull()
        }
    }

    /**
     * Show the local TDLib folder list immediately, then hydrate destinations in small
     * batches. A slow LoadChats(100) can no longer block the first visible rows.
     *
     * The cold flow is scoped to the selected folder: switching folders cancels unused
     * work. Completed lists are retained in the existing account/revision-scoped cache.
     */
    override fun streamForwardFolderChats(folderId: Int): Flow<List<TelegramChatSummary>> = flow {
        cachedForwardFolderChats(folderId)?.let {
            emit(it)
            return@flow
        }

        val client = TdLibClientAdapter.activeOrNull()
            ?: error("Telegram is not connected yet.")
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) {
            error("Reconnect Telegram to load this folder.")
        }
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Telegram account information is still loading.")
        val userId = accountId.toLongOrNull()
            ?: error("Telegram account information is invalid.")
        val revision = client.chatFolderRevision
        val selectedIds = telegramRepository.musicSourceState.value.selectedChatIds
        val chatList = TdApi.ChatListFolder(folderId)
        val orderedIds = LinkedHashSet<Long>()
        val resolved = LinkedHashMap<Long, TelegramChatSummary>()

        suspend fun hydrateAndEmit(ids: Collection<Long>) {
            orderedIds.addAll(ids)
            for (batch in orderedIds.filterNot(resolved::containsKey).chunked(FOLDER_CHAT_BATCH_SIZE)) {
                val found = resolveForwardChats(client, batch, userId, selectedIds)
                found.forEach { resolved[it.chatId] = it }
                val visible = orderedIds.mapNotNull(resolved::get)
                if (visible.isNotEmpty()) emit(visible)
            }
        }

        // GetChats is a snapshot of already loaded chat-list positions, not a
        // query for all members. Zero results at this point are not a final state.
        suspend fun hydrateLocal(limit: Int) {
            val ids = client.send(TdApi.GetChats(chatList, limit)).chatIds.toList()
            hydrateAndEmit(ids)
        }

        suspend fun loadPage(limit: Int): Boolean {
            return try {
                withTimeout(FOLDER_LOAD_TIMEOUT_MS) {
                    client.send(TdApi.LoadChats(chatList, limit))
                }
                false
            } catch (error: TdLibException) {
                // TDLib documents 404 as END OF LIST, not as a generic load failure.
                if (error.errorCode == 404) true else throw error
            }
        }

        suspend fun checkUpdatesAfterLoad(limit: Int) {
            // TDLib publishes chat-list positions asynchronously after LoadChats.
            // Allow those updates to reach GetChats before deciding that no rows exist.
            var previousCount = orderedIds.size
            repeat(FOLDER_UPDATE_POLL_ATTEMPTS) { attempt ->
                hydrateLocal(limit)
                if (orderedIds.size > previousCount) return
                if (attempt < FOLDER_UPDATE_POLL_ATTEMPTS - 1) delay(FOLDER_UPDATE_POLL_DELAY_MS)
                previousCount = orderedIds.size
            }
        }

        hydrateLocal(FOLDER_FIRST_SCREEN_COUNT)

        // Read the static definition separately. Explicit inclusions may have no
        // chat-list position yet, so they should not disappear when GetChats is empty.
        val definitionResult = runCatching {
            withTimeout(FOLDER_LOAD_TIMEOUT_MS) { client.send(TdApi.GetChatFolder(folderId)) }
        }
        val definition = definitionResult.getOrNull()
        val included = LinkedHashSet<Long>()
        definition?.pinnedChatIds?.let { included.addAll(it.toList()) }
        definition?.includedChatIds?.let { included.addAll(it.toList()) }
        val excluded = definition?.excludedChatIds?.toSet().orEmpty()
        orderedIds.removeAll(excluded)
        excluded.forEach(resolved::remove)
        hydrateAndEmit(included.filterNot(excluded::contains))

        // For a cold folder, load one small page first. Emit every group of
        // resolved destinations immediately rather than waiting for a 100-chat pass.
        var reachedEnd = false
        if (resolved.isEmpty()) {
            reachedEnd = loadPage(FOLDER_FIRST_SCREEN_COUNT)
            checkUpdatesAfterLoad(FOLDER_FIRST_SCREEN_COUNT)
        }

        if (!reachedEnd && orderedIds.size < FOLDER_MAX_VISIBLE_COUNT) {
            // TDLib may load fewer chats than requested in each call. Retry a
            // bounded number of pages; each newly resolved batch is emitted.
            repeat(FOLDER_EXTRA_LOAD_PASSES) {
                if (reachedEnd || orderedIds.size >= FOLDER_MAX_VISIBLE_COUNT) return@repeat
                reachedEnd = loadPage(FOLDER_MAX_VISIBLE_COUNT)
                checkUpdatesAfterLoad(FOLDER_MAX_VISIBLE_COUNT)
            }
        } else {
            hydrateLocal(FOLDER_MAX_VISIBLE_COUNT)
        }

        if (TdLibClientAdapter.activeOrNull() !== client ||
            telegramRepository.authState.value.step !is TelegramAuthStep.Ready ||
            telegramRepository.musicSourceState.value.accountId != accountId ||
            client.chatFolderRevision != revision
        ) {
            error("Telegram folders changed while loading. Please retry.")
        }

        val final = orderedIds.mapNotNull(resolved::get)
        if (final.isEmpty()) {
            // A list that has known IDs but no resolved chats is NOT empty;
            // neither is a list that never reached end-of-list (TDLib 404).
            if (!isConfirmedEmptyForwardFolder(
                    knownChatCount = orderedIds.size,
                    resolvedChatCount = final.size,
                    reachedEnd = reachedEnd,
                    definitionLoaded = definitionResult.isSuccess,
                )
            ) {
                error("This Telegram folder hasn't finished loading. Tap Retry loading folder.")
            }
            emit(emptyList())
            return@flow
        }

        // Cache only a complete, validated snapshot. A partial 100-chat page
        // must not suppress future loads of other folder members.
        if (shouldCacheForwardFolderChats(
                resolvedChatCount = final.size,
                reachedEnd = reachedEnd,
                definitionLoaded = definitionResult.isSuccess,
            )
        ) {
            synchronized(folderRequestLock) {
                folderChatCaches.getOrPut(folderId) { ForwardFolderMemoryCache() }.save(
                    accountId, client, revision, SystemClock.elapsedRealtime(), final,
                )
            }
        }
        emit(final)
    }

    override suspend fun loadForwardFolderChats(folderId: Int): List<TelegramChatSummary> =
        streamForwardFolderChats(folderId).lastOrNull().orEmpty()

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
        const val FOLDER_FIRST_SCREEN_COUNT = 24
        const val FOLDER_CHAT_BATCH_SIZE = 6
        const val FOLDER_MAX_VISIBLE_COUNT = 100
        const val FOLDER_EXTRA_LOAD_PASSES = 3
        const val FOLDER_LOAD_TIMEOUT_MS = 12_000L
        const val FOLDER_UPDATE_POLL_ATTEMPTS = 4
        const val FOLDER_UPDATE_POLL_DELAY_MS = 150L
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
