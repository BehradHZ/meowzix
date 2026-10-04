package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.data.downloads.ProcessTransferBudget
import dev.behradhz.meowzix.data.downloads.TransferPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
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
    internal val fileStates = TdFileStateRegistry()

    private val readyGate = AuthorizationReadyGate()
    private val client: Client

    init {
        Client.setLogMessageHandler(0, null)
        client = Client.create(
            { update ->
                if (update is TdApi.UpdateFile) {
                    fileStates.publish(update.file)
                }
                val shouldForward = if (update is TdApi.UpdateAuthorizationState) {
                    readyGate.shouldForward(update.authorizationState is TdApi.AuthorizationStateReady)
                } else {
                    true
                }
                if (shouldForward) globalUpdateChannel.tryEmit(update)
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
            val key = "td:${function.fileId}:${function.offset}:${function.limit}:${function.synchronous}"
            val result = ProcessTransferBudget.scheduler.run(
                physicalKey = key,
                priority = transferPriority(function.priority),
            ) {
                target.sendDirect(function)
            }
            @Suppress("UNCHECKED_CAST")
            return result as R
        }
        return target.sendDirect(function)
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

    companion object {
        private val globalUpdateChannel = MutableSharedFlow<TdApi.Object>(replay = 1, extraBufferCapacity = 64)
        private val globalFailureChannel = MutableSharedFlow<Throwable>(replay = 1, extraBufferCapacity = 8)
        private val resetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var activeInstance: TdLibClientAdapter? = null

        fun activeOrNull(): TdLibClientAdapter? = activeInstance

        /**
         * Close and recreate TDLib without LogOut. TDLib keeps the authorization database, so this
         * recovers a stuck startup/client while preserving the signed-in account and imported music.
         */
        fun resetActive() {
            resetScope.launch {
                val stale = synchronized(Companion) {
                    val current = activeInstance
                    activeInstance = null
                    current
                }
                if (stale != null) {
                    runCatching { stale.sendDirect(TdApi.Close()) }
                }
                runCatching { TdLibClientAdapter() }
                    .onFailure(globalFailureChannel::tryEmit)
            }
        }
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
