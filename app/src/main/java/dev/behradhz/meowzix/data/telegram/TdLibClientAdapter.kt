package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.data.downloads.ProcessTransferBudget
import dev.behradhz.meowzix.data.downloads.TransferPriority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TdLibException(val errorCode: Int, message: String) : Exception(message)

class TdLibClientAdapter {
    /**
     * Repository collectors intentionally survive a client restart. All adapter instances publish
     * through these process-wide buses, while send() transparently follows the current active client.
     */
    val updates: Flow<TdApi.Object> = globalUpdateChannel.asSharedFlow()
    val failures: Flow<Throwable> = globalFailureChannel.asSharedFlow()
    val chatFolders: StateFlow<List<TdApi.ChatFolderInfo>> = globalChatFolders.asStateFlow()
    val chatFolderRevision: Long get() = globalChatFolderRevision.get()
    private val _authorizationStates = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationStates: StateFlow<TdApi.AuthorizationState?> = _authorizationStates.asStateFlow()
    internal val fileStates = TdFileStateRegistry()
    // TDLib changes an active file's byte window when DownloadFile is sent.
    // Serialize commands targeting the same file; awaiting bytes remains outside the lock.
    private val downloadCommandLocks = Array(64) { Mutex() }

    private val readyGate = AuthorizationReadyGate()
    private val closed = CompletableDeferred<Unit>()
    private val client: Client

    init {
        Client.setLogMessageHandler(0, null)
        client = Client.create(
            { update ->
                if (update is TdApi.UpdateFile) {
                    fileStates.publish(update.file)
                }
                if (update is TdApi.UpdateChatFolders) {
                    globalChatFolderRevision.incrementAndGet()
                    globalChatFolders.value = update.chatFolders.toList()
                }
                if (update is TdApi.UpdateAuthorizationState &&
                    update.authorizationState !is TdApi.AuthorizationStateReady
                ) {
                    globalChatFolderRevision.incrementAndGet()
                    globalChatFolders.value = emptyList()
                }
                val shouldForward = if (update is TdApi.UpdateAuthorizationState) {
                    readyGate.shouldForward(update.authorizationState is TdApi.AuthorizationStateReady)
                } else {
                    true
                }
                if (shouldForward) {
                    if (update is TdApi.UpdateAuthorizationState) {
                        _authorizationStates.value = update.authorizationState
                        if (update.authorizationState is TdApi.AuthorizationStateClosed) closed.complete(Unit)
                    }
                    globalUpdateChannel.tryEmit(update)
                }
            },
            { error -> globalFailureChannel.tryEmit(error) },
            { error -> globalFailureChannel.tryEmit(error) },
        )
        synchronized(Companion) {
            activeInstance = this
        }
    }

    suspend fun <R : TdApi.Object> send(function: TdApi.Function<R>): R {
        val target = activeInstance?.takeIf { it !== this } ?: this
        if (function is TdApi.DownloadFile) {
            // A synchronous TDLib download can run for minutes. Holding one of our
            // three transfer slots until it finishes starves streaming and seek
            // requests, even when enough audio bytes are already cached.
            // Start it asynchronously under the scheduler, then wait for its
            // requested range outside the slot.
            val request = TdApi.DownloadFile(
                function.fileId, function.priority, function.offset, function.limit, false,
            )
            val key = "td:${function.fileId}:${function.offset}:${function.limit}:${function.priority}"
            val result = ProcessTransferBudget.scheduler.runAndAwait(
                physicalKey = key,
                priority = transferPriority(function.priority),
                begin = {
                    target.downloadCommandLock(request.fileId).withLock {
                        target.sendDirect(request).also(target.fileStates::publish)
                    }
                },
                await = { started ->
                    if (function.synchronous) target.awaitDownloadCompletion(function, started)
                    else started
                },
            )
            @Suppress("UNCHECKED_CAST")
            return result as R
        }
        if (function is TdApi.CancelDownloadFile) {
            return target.downloadCommandLock(function.fileId).withLock {
                target.sendDirect(function)
            }
        }
        if (function is TdApi.DeleteFile) {
            return target.downloadCommandLock(function.fileId).withLock {
                target.sendDirect(function)
            }
        }
        return target.sendDirect(function)
    }

    /**
     * Cancel and restart a stalled transfer under the same per-file command lock used by normal
     * DownloadFile/CancelDownloadFile calls. Keep the earliest known range so a seek recovery cannot
     * replace a concurrent full offline download with a later byte range.
     */
    internal suspend fun restartDownload(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Long = 0L,
        expectedUniqueId: String? = null,
    ): TdApi.File {
        val target = activeInstance?.takeIf { it !== this } ?: this
        val key = "td-restart:$fileId:$offset:$limit:${expectedUniqueId.orEmpty()}"
        return ProcessTransferBudget.scheduler.run(key, transferPriority(priority)) {
            target.downloadCommandLock(fileId).withLock {
                fun verifyIdentity(file: TdApi.File) {
                    if (!expectedUniqueId.isNullOrBlank() &&
                        !matchesTelegramFileIdentity(expectedUniqueId, file.remote.uniqueId)
                    ) {
                        throw IOException("Telegram audio identity changed; refresh the source before playback")
                    }
                }

                val before = target.sendDirect(TdApi.GetFile(fileId))
                verifyIdentity(before)
                target.fileStates.publish(before)
                if (before.local.isDownloadingCompleted) return@withLock before

                val hadExistingRange = before.local.isDownloadingActive || before.local.downloadedSize > 0
                var restartOffset = if (hadExistingRange) {
                    minOf(offset.coerceAtLeast(0L), before.local.downloadOffset.coerceAtLeast(0L))
                } else {
                    offset.coerceAtLeast(0L)
                }
                try {
                    target.sendDirect(TdApi.CancelDownloadFile(fileId, false))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Cancellation can race with a transfer that just stopped on its own.
                }

                val refreshed = target.sendDirect(TdApi.GetFile(fileId))
                verifyIdentity(refreshed)
                target.fileStates.publish(refreshed)
                if (refreshed.local.isDownloadingCompleted) return@withLock refreshed
                if (refreshed.local.isDownloadingActive || refreshed.local.downloadedSize > 0) {
                    restartOffset = minOf(restartOffset, refreshed.local.downloadOffset.coerceAtLeast(0L))
                }

                target.sendDirect(
                    TdApi.DownloadFile(fileId, priority, restartOffset, limit, false),
                ).also(target.fileStates::publish)
            }
        }
    }

    internal suspend fun cancelDownload(fileId: Int) {
        val target = activeInstance?.takeIf { it !== this } ?: this
        target.downloadCommandLock(fileId).withLock {
            target.sendDirect(TdApi.CancelDownloadFile(fileId, false))
        }
    }

    private fun downloadCommandLock(fileId: Int): Mutex =
        downloadCommandLocks[(fileId and Int.MAX_VALUE) % downloadCommandLocks.size]

    /**
     * Emulate synchronous DownloadFile without monopolizing the process-wide
     * request scheduler. Progress is driven by TDLib UpdateFile; a bounded
     * watchdog detects stopped/stalled requests instead of hanging forever.
     */
    private suspend fun awaitDownloadCompletion(
        request: TdApi.DownloadFile,
        initial: TdApi.File,
    ): TdApi.File {
        fun ready(file: TdApi.File): Boolean = satisfiesDownloadRequest(
            offset = request.offset,
            limit = request.limit,
            fileSize = file.size.toLong(),
            readableEnd = if (request.limit > 0L) file.readableEndAt(request.offset) else 0L,
            completed = file.local.isDownloadingCompleted,
        )

        var current = initial
        var recoveryAttempts = 0
        val watchdog = TdDownloadProgressWatchdog()
        watchdog.reset(
            initial.local.downloadedSize.toLong(),
            initial.readableEndAt(request.offset),
            System.nanoTime() / 1_000_000L,
        )

        while (true) {
            if (ready(current)) return current
            val next = withTimeoutOrNull(DOWNLOAD_UPDATE_WAIT_MS) {
                fileStates.awaitProgressOrReadable(
                    request.fileId,
                    current,
                )
            }
            current = next ?: refreshFileState(request.fileId)
            if (ready(current)) return current

            val nowMs = System.nanoTime() / 1_000_000L
            when (watchdog.observe(
                current.local.downloadedSize.toLong(),
                current.readableEndAt(request.offset),
                nowMs,
            )) {
                TdDownloadProgressState.PROGRESSED -> recoveryAttempts = 0
                TdDownloadProgressState.STALLED -> {
                    if (recoveryAttempts >= TD_DOWNLOAD_MAX_RECOVERIES) {
                        try {
                            cancelDownload(request.fileId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Preserve the original timeout as the actionable failure.
                        }
                        throw IOException("Telegram download stalled after recovery attempts")
                    }
                    current = restartDownload(
                        fileId = request.fileId,
                        priority = request.priority,
                        offset = request.offset,
                        limit = request.limit,
                        expectedUniqueId = initial.remote.uniqueId,
                    )
                    recoveryAttempts += 1
                    watchdog.reset(
                        current.local.downloadedSize.toLong(),
                        current.readableEndAt(request.offset),
                        System.nanoTime() / 1_000_000L,
                    )
                }
                TdDownloadProgressState.INITIAL,
                TdDownloadProgressState.WAITING,
                -> Unit
            }
        }
    }

    private suspend fun <R : TdApi.Object> sendDirect(function: TdApi.Function<R>): R =
        suspendCancellableCoroutine { continuation ->
            client.send(
                function,
                { result ->
                    if (!continuation.isActive) return@send
                    if (result is TdApi.Error) {
                        continuation.resumeWithException(TdLibException(result.code, result.message))
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(result as R)
                    }
                },
                { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                },
            )
        }

    internal suspend fun seedFileState(fileId: Int): TdApi.File {
        fileStates.current(fileId)?.let { return it }
        return send(TdApi.GetFile(fileId)).also { file ->
            (activeInstance ?: this).fileStates.publish(file)
        }
    }

    internal suspend fun refreshFileState(fileId: Int): TdApi.File =
        send(TdApi.GetFile(fileId)).also { file ->
            (activeInstance ?: this).fileStates.publish(file)
        }

    internal fun deactivate() {
        synchronized(Companion) {
            if (activeInstance === this) activeInstance = null
        }
    }

    suspend fun closeAndAwaitClosed() {
        if (!closed.isCompleted) {
            runCatching { sendDirect(TdApi.Close()) }
            closed.await()
        }
    }

    companion object {
        private const val DOWNLOAD_UPDATE_WAIT_MS = 5_000L
        private val globalUpdateChannel = MutableSharedFlow<TdApi.Object>(replay = 1, extraBufferCapacity = 64)
        private val globalFailureChannel = MutableSharedFlow<Throwable>(replay = 1, extraBufferCapacity = 8)
        private val globalChatFolders = MutableStateFlow<List<TdApi.ChatFolderInfo>>(emptyList())
        private val globalChatFolderRevision = AtomicLong(0L)
        @Volatile
        private var activeInstance: TdLibClientAdapter? = null

        fun activeOrNull(): TdLibClientAdapter? = activeInstance
    }
}

private fun transferPriority(tdPriority: Int): TransferPriority = when {
    tdPriority >= TdDownloadPriority.CURRENT_TRACK_AUDIO -> TransferPriority.IMMEDIATE_PLAYBACK
    tdPriority >= TdDownloadPriority.USER_REQUESTED_DOWNLOAD -> TransferPriority.USER_ACTION
    tdPriority == TdDownloadPriority.NEXT_TRACK_PRELOAD -> TransferPriority.SPECULATIVE_PREFETCH
    tdPriority <= TdDownloadPriority.ARTWORK_MAINTENANCE -> TransferPriority.SPECULATIVE_PREFETCH
    else -> TransferPriority.BULK_DOWNLOAD
}

internal class AuthorizationReadyGate {
    private var readyDelivered = false

    @Synchronized
    fun shouldForward(isReady: Boolean): Boolean {
        if (!isReady) {
            readyDelivered = false
            return true
        }
        if (readyDelivered) return false
        readyDelivered = true
        return true
    }
}
