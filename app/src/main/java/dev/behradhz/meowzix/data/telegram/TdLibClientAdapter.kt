package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TdLibException(val errorCode: Int, message: String) : Exception(message)

class TdLibClientAdapter {
    private val updateChannel = MutableSharedFlow<TdApi.Object>(replay = 1, extraBufferCapacity = 64)
    private val failureChannel = MutableSharedFlow<Throwable>(replay = 1, extraBufferCapacity = 8)
    val updates: Flow<TdApi.Object> = updateChannel.asSharedFlow()
    val failures: Flow<Throwable> = failureChannel.asSharedFlow()
    internal val fileStates = TdFileStateRegistry()

    private val readyGate = AuthorizationReadyGate()
    private val client: Client

    init {
        Client.setLogMessageHandler(0, null)
        client = Client.create(
            { update ->
                // Streaming is a latency-sensitive consumer of UpdateFile. Publish synchronously
                // into the registry before best-effort SharedFlow fan-out so a full update buffer
                // cannot force the Media3 loader back into polling.
                if (update is TdApi.UpdateFile) {
                    fileStates.publish(update.file)
                }
                val shouldForward = if (update is TdApi.UpdateAuthorizationState) {
                    readyGate.shouldForward(update.authorizationState is TdApi.AuthorizationStateReady)
                } else {
                    true
                }
                if (shouldForward) updateChannel.tryEmit(update)
            },
            { error -> failureChannel.tryEmit(error) },
            { error -> failureChannel.tryEmit(error) },
        )
        activeInstance = this
    }

    suspend fun <R : TdApi.Object> send(function: TdApi.Function<R>): R =
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

    /** One GetFile seed is allowed when a stream has not observed this file in the current process. */
    internal suspend fun seedFileState(fileId: Int): TdApi.File {
        fileStates.current(fileId)?.let { return it }
        return send(TdApi.GetFile(fileId)).also(fileStates::publish)
    }

    /**
     * Low-frequency recovery path for a missed/lost UpdateFile. Streaming code deliberately calls
     * this only after an event wait times out, never on every read/poll interval.
     */
    internal suspend fun refreshFileState(fileId: Int): TdApi.File =
        send(TdApi.GetFile(fileId)).also(fileStates::publish)

    companion object {
        @Volatile
        private var activeInstance: TdLibClientAdapter? = null

        fun activeOrNull(): TdLibClientAdapter? = activeInstance
    }
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
