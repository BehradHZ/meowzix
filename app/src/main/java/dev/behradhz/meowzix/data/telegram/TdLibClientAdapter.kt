package dev.behradhz.meowzix.data.telegram

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class TdLibException(val errorCode: Int, message: String) : Exception(message)

internal class TdLibClientAdapter {
    private val updateChannel = Channel<TdApi.Object>(Channel.UNLIMITED)
    private val failureChannel = Channel<Throwable>(Channel.UNLIMITED)
    val updates: Flow<TdApi.Object> = updateChannel.receiveAsFlow()
    val failures: Flow<Throwable> = failureChannel.receiveAsFlow()

    private val client: Client

    init {
        Client.setLogMessageHandler(0, null)
        client = Client.create(
            { update -> updateChannel.trySend(update) },
            { error -> failureChannel.trySend(error) },
            { error -> failureChannel.trySend(error) },
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

    companion object {
        @Volatile
        private var activeInstance: TdLibClientAdapter? = null

        fun activeOrNull(): TdLibClientAdapter? = activeInstance
    }
}
