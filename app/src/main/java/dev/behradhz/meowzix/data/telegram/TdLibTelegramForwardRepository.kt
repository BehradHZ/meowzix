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
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
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
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.selects.select
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
    private val activeSends = ConcurrentHashMap<String, ActiveSend>()
    private val folderLoadMutex = Mutex()
    private val folderCache = ForwardFolderMemoryCache<TelegramForwardFolder>()
    private val snapshots = ForwardDestinationSnapshotStore(context)
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
                .map { it.step }
                .distinctUntilChanged()
                .collect { step ->
                    if (step is TelegramAuthStep.Ready) processPendingSends()
                    if (step is TelegramAuthStep.LoggingOut) {
                        snapshots.clearAll()
                        folderCache.clear()
                        synchronized(folderRequestLock) { folderChatCaches.clear() }
                    }
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
            snapshots.recentChats(accountId)?.takeIf { it.isNotEmpty() }?.let { cached ->
                return cached.take(safeLimit).map { it.copy(selected = it.chatId in telegramRepository.musicSourceState.value.selectedChatIds) }
            }
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
        val result = resolveForwardChats(
            client = client,
            chatIds = ids.take(safeLimit),
            currentUserId = userId,
            selectedIds = telegramRepository.musicSourceState.value.selectedChatIds,
        )
        if (term.isEmpty() && result.isNotEmpty()) snapshots.saveRecentChats(accountId, result)
        return result
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
        folderCache.snapshot(
            accountId = accountId,
            session = client,
            revision = client.chatFolderRevision,
            nowMillis = SystemClock.elapsedRealtime(),
        )?.let { return it }
        client.chatFolders.value.takeIf { it.isNotEmpty() }?.let { infos ->
            val folders = infos.map {
                TelegramForwardFolder(it.id, it.name?.text?.text?.ifBlank { "Folder" } ?: "Folder", emptyList())
            }
            folderCache.save(accountId, client, client.chatFolderRevision, SystemClock.elapsedRealtime(), folders)
            snapshots.saveFolders(accountId, folders)
            return folders
        }
        return snapshots.folders(accountId)
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
        if (forceRefresh) {
            // Keep the last-known disk snapshot until the replacement is valid.
            folderCache.clear()
            synchronized(folderRequestLock) { folderChatCaches.clear() }
        }
        val request = synchronized(folderRequestLock) {
            (if (forceRefresh) null else inFlightFolderRequest?.takeIf { it.isActive })
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
            if (forceRefresh && infos.isEmpty() && snapshots.folders(accountId)?.isNotEmpty() == true) {
                error("Telegram hasn't returned an updated folder list yet. The cached folders were kept.")
            }
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
                if (folders.isNotEmpty() || forceRefresh) {
                    if (forceRefresh) snapshots.clear(accountId)
                    folderCache.save(
                        accountId = accountId,
                        session = client,
                        revision = folderRevision,
                        nowMillis = SystemClock.elapsedRealtime(),
                        folders = folders,
                    )
                    snapshots.saveFolders(accountId, folders)
                }
            }
            folders
        }

    override fun cachedForwardFolderChats(folderId: Int): List<TelegramChatSummary>? {
        val client = TdLibClientAdapter.activeOrNull() ?: return null
        if (telegramRepository.authState.value.step !is TelegramAuthStep.Ready) return null
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        val selectedIds = telegramRepository.musicSourceState.value.selectedChatIds
        val memory = synchronized(folderRequestLock) {
            folderChatCaches[folderId]?.snapshot(
                accountId, client, client.chatFolderRevision, SystemClock.elapsedRealtime(),
            )?.takeIf { it.isNotEmpty() }
        }
        return (memory ?: snapshots.folderChats(accountId, folderId))
            ?.takeIf { it.isNotEmpty() }
            ?.map { it.copy(selected = it.chatId in selectedIds) }
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
        cachedForwardFolderChats(folderId)?.let { emit(it) }

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
        val freshMemory = synchronized(folderRequestLock) {
            folderChatCaches[folderId]?.snapshot(accountId, client, revision, SystemClock.elapsedRealtime())
        }
        if (freshMemory != null) return@flow
        val chatList = TdApi.ChatListFolder(folderId)
        val orderedIds = LinkedHashSet<Long>()
        val resolved = LinkedHashMap<Long, TelegramChatSummary>()
        val excludedIds = mutableSetOf<Long>()

        suspend fun hydrateAndEmit(ids: Collection<Long>) {
            orderedIds.addAll(ids.filterNot(excludedIds::contains))
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
        excludedIds.addAll(excluded)
        orderedIds.removeAll(excluded)
        excluded.forEach(resolved::remove)
        hydrateAndEmit(included.filterNot(excluded::contains))

        // For a cold folder, load one small page first. Emit every group of
        // resolved destinations immediately rather than waiting for a 100-chat pass.
        var reachedEnd = false
        if (orderedIds.size < FOLDER_FIRST_SCREEN_COUNT) {
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

        // Partial previews are durable, but only complete in-memory snapshots skip further pagination.
        snapshots.saveFolderChats(accountId, folderId, final)
        if (shouldCacheForwardFolderChats(
                knownChatCount = orderedIds.size,
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
        val id = jobId.toString()
        val job = sendDao.jobById(id) ?: return
        val state = TelegramSendState.valueOf(job.state)
        when (state) {
            TelegramSendState.CANCELED, TelegramSendState.CANCELING -> return
            TelegramSendState.SENT ->
                error("This song was already sent. Delete it in Telegram if needed.")
            TelegramSendState.CHECKING,
            TelegramSendState.PREPARING_FILE,
            TelegramSendState.UPLOADING,
            TelegramSendState.SENDING -> {
                if (sendDao.requestCancelActive(id, System.currentTimeMillis()) > 0) {
                    val active = activeSends[id]
                    if (active == null) {
                        // A prior process owns the delivery (or its confirmation was lost).
                        // Never silently claim an unconfirmed send was canceled.
                        sendDao.markCancelUncertain(
                            id, "Cancellation couldn't be confirmed. Check the destination chat before trying again.",
                            System.currentTimeMillis(),
                        )
                    } else {
                        active.cancelSignal.complete(Unit)
                    }
                }
            }
            else -> {
                // A failed or uncertain send is being dismissed, not re-sent.
                val changed = sendDao.cancel(id, System.currentTimeMillis())
                if (changed == 0 && sendDao.jobById(id)?.state != TelegramSendState.CANCELED.name) {
                    error("This send is changing state. Try again.")
                }
            }
        }
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
        val active = ActiveSend()
        activeSends[job.id] = active
        val attempt = job.attemptCount + 1

        try {
            setState(
                job, TelegramSendState.CHECKING,
                attemptCount = attempt, errorMessage = null,
            )
            ensureNotCancelRequested(active)
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

            ensureNotCancelRequested(active)
            val target = try {
                client.send(TdApi.GetChat(job.targetChatId))
            } catch (cause: TdLibException) {
                if (cause.errorCode == 404) {
                    throw IllegalStateException(
                        "The destination chat is no longer available in Telegram. Refresh the destination list and choose another chat.",
                        cause,
                    )
                }
                throw cause
            }
            val track = libraryDao.trackById(job.trackId)
                ?: error("This track is no longer in your Meowzix library.")
            val telegramSource = telegramDao.telegramSourceForTrack(job.accountId, job.trackId)

            val deliveredMessageId = sendWithTelegramSourceFallback(
                sourceMessageId = telegramSource?.messageId,
                forwardMessage = {
                    val source = checkNotNull(telegramSource)
                    sendTelegramBackedTrack(client, job, target, source.chatId, source.messageId, active)
                },
                sendAudioCopy = {
                    if (telegramSource != null && telegramSource.messageId < 0L) {
                        sendProfileAudio(client, job, track.title, track.artist, track.durationMs, telegramSource, active)
                    } else {
                        sendLocalTrack(
                            client, job, track.title, track.artist, track.durationMs,
                            telegramSource, originalMessageMissing = telegramSource != null,
                            active = active,
                        )
                    }
                },
            )
            if (active.cancelSignal.isCompleted) {
                active.pendingMessageId = deliveredMessageId
                throw SendCancellationRequested()
            }
            sendDao.markSent(job.id, deliveredMessageId, System.currentTimeMillis())
            if (active.cancelSignal.isCompleted) {
                active.pendingMessageId = deliveredMessageId
                throw SendCancellationRequested()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SendCancellationRequested) {
            confirmSendCancellation(job, client, active)
        } catch (uncertain: DeliveryUncertainException) {
            setState(
                job,
                TelegramSendState.VERIFYING,
                attemptCount = attempt,
                errorMessage = "Meowzix couldn't confirm delivery. Check Telegram before retrying so the song isn't sent twice.",
            )
        } catch (error: Throwable) {
            if (active.cancelSignal.isCompleted) {
                confirmSendCancellation(job, client, active)
                return
            }
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
        } finally {
            activeSends.remove(job.id, active)
        }
    }

    private suspend fun confirmSendCancellation(
        job: TelegramSendJobEntity,
        client: TdLibClientAdapter,
        active: ActiveSend,
    ) {
        val pendingId = active.pendingMessageId
        if (pendingId == null && active.submissionStarted) {
            sendDao.markCancelUncertain(
                job.id,
                "Cancellation couldn't be confirmed. The send may have reached Telegram; check the chat before retrying.",
                System.currentTimeMillis(),
            )
            return
        }
        if (pendingId != null) {
            try {
                // TDLib deletes pending outgoing messages to stop the upload/send.
                // Revoke=true also covers the race where Telegram has just accepted it.
                client.send(TdApi.DeleteMessages(job.targetChatId, longArrayOf(pendingId), true))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                sendDao.markCancelUncertain(
                    job.id,
                    "Telegram couldn't confirm cancellation. Check the destination chat before retrying.",
                    System.currentTimeMillis(),
                )
                return
            }
        }
        sendDao.cancel(job.id, System.currentTimeMillis())
    }

    private fun ensureNotCancelRequested(active: ActiveSend) {
        if (active.cancelSignal.isCompleted) throw SendCancellationRequested()
    }

    private class ActiveSend(
        val cancelSignal: CompletableDeferred<Unit> = CompletableDeferred(),
        @Volatile var pendingMessageId: Long? = null,
        @Volatile var submissionStarted: Boolean = false,
    )

    private class SendCancellationRequested : Exception()

    private suspend fun sendTelegramBackedTrack(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        target: TdApi.Chat,
        sourceChatId: Long,
        sourceMessageId: Long,
        active: ActiveSend,
    ): Long {
        // Only the preflight is eligible for copy fallback. A send that has already
        // reached Telegram must NEVER be retried as an upload: delivery could be duplicated.
        val properties = try {
            client.send(TdApi.GetMessage(sourceChatId, sourceMessageId))
            client.send(TdApi.GetMessageProperties(sourceChatId, sourceMessageId))
        } catch (error: TdLibException) {
            if (error.errorCode == 404) throw OriginalTelegramMessageMissing()
            throw error
        }
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

        ensureNotCancelRequested(active)
        setState(job, TelegramSendState.SENDING, progressPercent = 90, errorMessage = null)
        active.submissionStarted = true
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
        return awaitDelivery(client, pending, job.id, null, active)
    }

    /**
     * Profile audio has no forwardable message. TDLib can send an existing server file
     * by its file ID, which avoids re-uploading. Fall back only if that request is
     * rejected BEFORE TDLib accepts a pending outgoing message.
     */
    private suspend fun sendProfileAudio(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        title: String,
        artist: String?,
        durationMs: Long,
        source: TelegramTrackSourceEntity,
        active: ActiveSend,
    ): Long {
        ensureNotCancelRequested(active)
        val profileAudio = findTelegramProfileAudio(
            client, source.chatId, source.messageId, source.tdPersistentFileId,
        )
        if (profileAudio != null) {
            ensureNotCancelRequested(active)
            setState(job, TelegramSendState.SENDING, progressPercent = 88, errorMessage = null)
            val inputAudio = TdApi.InputAudio(
                TdApi.InputFileId(profileAudio.audio.id),
                null,
                (durationMs / 1_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                title,
                artist.orEmpty(),
            )
            val pending = try {
                active.submissionStarted = true
                client.send(
                    TdApi.SendMessage(
                        job.targetChatId, null, null, null, null,
                        TdApi.InputMessageAudio(inputAudio, TdApi.FormattedText("", null)),
                    ),
                )
            } catch (error: TdLibException) {
                // The call itself was rejected; no pending message exists to duplicate.
                if (error.errorCode !in setOf(400, 404)) throw error
                active.submissionStarted = false
                ensureNotCancelRequested(active)
                return sendLocalTrack(
                    client, job, title, artist, durationMs, source,
                    originalMessageMissing = false, active = active, profileAudio = profileAudio,
                )
            }
            // Do not fall back if message delivery later fails or becomes uncertain.
            return awaitDelivery(client, pending, job.id, null, active)
        }

        return sendLocalTrack(
            client, job, title, artist, durationMs, source,
            originalMessageMissing = false, active = active,
        )
    }

    private suspend fun sendLocalTrack(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        title: String,
        artist: String?,
        durationMs: Long,
        telegramSource: TelegramTrackSourceEntity?,
        originalMessageMissing: Boolean,
        active: ActiveSend,
        profileAudio: TdApi.Audio? = null,
    ): Long {
        ensureNotCancelRequested(active)
        setState(job, TelegramSendState.PREPARING_FILE, progressPercent = 2, errorMessage = null)
        val prepared = prepareLocalAudio(client, job, telegramSource, originalMessageMissing, profileAudio)
        try {
            ensureNotCancelRequested(active)
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
            active.submissionStarted = true
            val pending = client.send(
                TdApi.SendMessage(job.targetChatId, null, null, null, null, content),
            )
            val fileId = (pending.content as? TdApi.MessageAudio)?.audio?.audio?.id
            return awaitDelivery(client, pending, job.id, fileId, active)
        } finally {
            if (prepared.temporary) runCatching { prepared.file.delete() }
        }
    }

    private suspend fun prepareLocalAudio(
        client: TdLibClientAdapter,
        job: TelegramSendJobEntity,
        telegramSource: TelegramTrackSourceEntity?,
        originalMessageMissing: Boolean,
        knownProfileAudio: TdApi.Audio? = null,
    ): PreparedLocalAudio {
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
        // Profile music is indexed using a negative synthetic ID, not a Telegram message.
        // Fetch its audio via GetUserProfileAudios, not GetMessage/ForwardMessages. A previously
        // played song takes the local-file path above, without another remote download.
        if (telegramSource != null && telegramSource.messageId < 0L) {
            val profileAudio = knownProfileAudio ?: findTelegramProfileAudio(
                client,
                telegramSource.chatId,
                telegramSource.messageId,
                telegramSource.tdPersistentFileId,
            ) ?: error("This profile song is no longer available on Telegram and no saved audio copy was found.")
            val downloaded = client.send(
                TdApi.DownloadFile(
                    profileAudio.audio.id,
                    TdDownloadPriority.USER_REQUESTED_DOWNLOAD,
                    0L, 0L, true,
                ),
            ).also(client.fileStates::publish)
            val file = downloaded.local.path.takeIf {
                downloaded.local.isDownloadingCompleted && it.isNotBlank()
            }?.let(::File)?.takeIf { it.isFile && it.canRead() && it.length() > 0L }
                ?: error("Telegram couldn't finish downloading this profile song. Try again when connected.")
            // TDLib owns its downloaded file. Never delete this source after sending.
            return PreparedLocalAudio(file, temporary = false)
        }
        if (originalMessageMissing) {
            error("The original Telegram message was deleted or is inaccessible, and no saved audio copy was found. Play the song to save it, then retry.")
        }
        error("This track has no readable audio file. Restore the local file or play it to download before sending.")
    }

    private suspend fun awaitDelivery(
        client: TdLibClientAdapter,
        pending: TdApi.Message,
        jobId: String,
        uploadFileId: Int?,
        active: ActiveSend,
    ): Long = coroutineScope {
        active.pendingMessageId = pending.id
        ensureNotCancelRequested(active)
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
                coroutineScope {
                    val deliveryWait = async {
                        client.updates.mapNotNull { update ->
                            when (update) {
                                is TdApi.UpdateMessageSendSucceeded ->
                                    if (update.oldMessageId == pending.id) DeliveryResult.Success(update.message.id) else null
                                is TdApi.UpdateMessageSendFailed ->
                                    if (update.oldMessageId == pending.id)
                                        DeliveryResult.Failure(update.error.code, update.error.message) else null
                                else -> null
                            }
                        }.first()
                    }
                    try {
                        select<DeliveryResult> {
                            deliveryWait.onAwait { it }
                            active.cancelSignal.onAwait { DeliveryResult.Canceled }
                        }
                    } finally {
                        deliveryWait.cancel()
                    }
                }
            }
            when (delivery) {
                is DeliveryResult.Success -> delivery.messageId
                is DeliveryResult.Failure -> throw TdLibException(delivery.code, delivery.message)
                DeliveryResult.Canceled -> throw SendCancellationRequested()
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

    private fun userFacingTelegramError(error: Throwable): String = telegramSendErrorMessage(error)

    private data class PreparedLocalAudio(val file: File, val temporary: Boolean)
    private class DeliveryUncertainException : Exception()

    private sealed interface DeliveryResult {
        data class Success(val messageId: Long) : DeliveryResult
        data class Failure(val code: Int, val message: String) : DeliveryResult
        data object Canceled : DeliveryResult
    }

    private companion object {
        const val FOLDER_FIRST_SCREEN_COUNT = 24
        const val FOLDER_CHAT_BATCH_SIZE = 6
        const val FOLDER_MAX_VISIBLE_COUNT = 1000
        const val FOLDER_EXTRA_LOAD_PASSES = 8
        const val FOLDER_LOAD_TIMEOUT_MS = 12_000L
        const val FOLDER_UPDATE_POLL_ATTEMPTS = 4
        const val FOLDER_UPDATE_POLL_DELAY_MS = 150L
        const val MAX_AUTO_ATTEMPTS = 3
        const val SEND_CONFIRMATION_TIMEOUT_MS = 30L * 60L * 1_000L
        const val TELEGRAM_SEND_WORK_NAME = "telegram-send-queue"
    }
}

/**
 * A profile song has a synthetic negative ID and cannot be forwarded as a message.
 * Fallback after a missing-message preflight is safe; fallback after an actual send is not.
 */
internal class OriginalTelegramMessageMissing : Exception()

internal suspend fun <T> sendWithTelegramSourceFallback(
    sourceMessageId: Long?,
    forwardMessage: suspend () -> T,
    sendAudioCopy: suspend () -> T,
): T {
    if (sourceMessageId == null || sourceMessageId <= 0L) return sendAudioCopy()
    return try {
        forwardMessage()
    } catch (_: OriginalTelegramMessageMissing) {
        sendAudioCopy()
    }
}

/** Keep TDLib/transport internals out of the user-facing error surface. */
internal fun telegramSendErrorMessage(failure: Throwable): String {
    val raw = failure.message.orEmpty()
    val normalized = raw.uppercase()
    return when {
        failure is TdLibException && failure.errorCode == 404 ->
            "Telegram couldn't find the chat or original message. Refresh the destination list, or send a saved audio copy."
        "CHAT_WRITE_FORBIDDEN" in normalized || "USER_BANNED_IN_CHANNEL" in normalized ->
            "You don't have permission to send music to this chat."
        "CHAT_RESTRICTED" in normalized ->
            "This chat doesn't allow sending audio files."
        "FILE_TOO_BIG" in normalized ->
            "Telegram rejected the audio because the file is too large."
        failure is IllegalStateException && (
            raw.startsWith("This track") ||
                raw.startsWith("This profile song") ||
                raw.startsWith("The original Telegram message") ||
                raw.startsWith("The destination chat") ||
                raw.startsWith("Telegram couldn't finish downloading") ||
                raw.startsWith("Telegram can't preserve source attribution") ||
                raw.startsWith("The original Telegram message doesn't") ||
                raw.startsWith("Telegram didn't create an outgoing message")
            ) -> raw
        failure is TdLibException -> telegramErrorGuidance(failure).message
        else -> "Telegram couldn't send this track. Check your connection and try again."
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
