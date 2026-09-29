package dev.behradhz.meowzix.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import dev.behradhz.meowzix.data.telegram.TdDownloadPriority
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import dev.behradhz.meowzix.data.telegram.readableEndAt
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

private const val TDLIB_SCHEME = "meowzix-tdlib"

@OptIn(UnstableApi::class)
class MeowzixDataSourceFactory(context: Context) : DataSource.Factory {
    private val defaultFactory = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = RoutingDataSource(defaultFactory)
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val defaultFactory: DataSource.Factory,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        active?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "DataSource is already open" }
        val source = if (dataSpec.uri.scheme == TDLIB_SCHEME) {
            TdLibStreamingDataSource()
        } else {
            defaultFactory.createDataSource()
        }
        listeners.forEach(source::addTransferListener)
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }
}

/**
 * Random-access reader over TDLib's growing local file.
 *
 * Media3's loader thread is synchronous, so it may block, but waiting is notification-driven:
 * UpdateFile wakes the registry waiter. GetFile is used once to seed process-local state and then
 * only as a bounded low-frequency recovery path if an update appears to have been lost.
 */
@OptIn(UnstableApi::class)
private class TdLibStreamingDataSource : BaseDataSource(true) {
    private var openedUri: Uri? = null
    private var fileId: Int = 0
    private var randomAccessFile: RandomAccessFile? = null
    private var openedPath: String? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var requestedRangeStart = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val id = dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid Telegram playback URI: ${dataSpec.uri}")
        val client = TdLibClientAdapter.activeOrNull()
            ?: throw IOException("Telegram is not ready yet")

        openedUri = dataSpec.uri
        fileId = id
        readPosition = dataSpec.position
        bytesRemaining = dataSpec.length

        val initialState = runBlocking {
            val seeded = runCatching { client.seedFileState(id) }
                .getOrElse { throw IOException("Unable to resolve Telegram file state", it) }
            if (!seeded.local.isDownloadingCompleted) {
                requestRange(
                    client = client,
                    position = readPosition,
                    priority = if (readPosition > 0L) {
                        TdDownloadPriority.CURRENT_TRACK_SEEK
                    } else {
                        TdDownloadPriority.CURRENT_TRACK_AUDIO
                    },
                )
            }
            waitForReadableState(client, readPosition)
        }

        if (initialState.readableEndAt(readPosition) > readPosition) {
            openFileForState(initialState)
        } else if (!isAtEnd(initialState, readPosition)) {
            throw IOException("Telegram stream has no readable bytes at offset $readPosition")
        }

        val knownSize = initialState.size.toLong().takeIf { it > 0L }
        if (bytesRemaining == C.LENGTH_UNSET.toLong() && knownSize != null) {
            bytesRemaining = (knownSize - readPosition).coerceAtLeast(0L)
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val client = TdLibClientAdapter.activeOrNull()
            ?: throw IOException("Telegram disconnected during playback")

        val state = runBlocking { waitForReadableState(client, readPosition) }
        val readableEnd = state.readableEndAt(readPosition)
        if (readableEnd <= readPosition) {
            if (isAtEnd(state, readPosition)) return C.RESULT_END_OF_INPUT
            throw IOException("Telegram audio range is unavailable at offset $readPosition")
        }

        val available = (readableEnd - readPosition).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingLimit = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            Int.MAX_VALUE
        } else {
            bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val toRead = min(length, min(available, remainingLimit))
        if (toRead <= 0) return C.RESULT_END_OF_INPUT

        val raf = openFileForState(state)
        raf.seek(readPosition)
        val read = raf.read(buffer, offset, toRead)
        if (read <= 0) {
            if (isAtEnd(state, readPosition)) return C.RESULT_END_OF_INPUT
            throw IOException("Telegram stream file stopped before the advertised readable range")
        }
        readPosition += read
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        randomAccessFile?.close()
        randomAccessFile = null
        openedPath = null
        openedUri = null
        fileId = 0
        requestedRangeStart = C.LENGTH_UNSET.toLong()
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private suspend fun waitForReadableState(
        client: TdLibClientAdapter,
        position: Long,
    ): TdApi.File {
        var state = client.fileStates.current(fileId) ?: client.seedFileState(fileId)
        var recoveryAttempts = 0
        while (true) {
            if (state.readableEndAt(position) > position || state.local.isDownloadingCompleted) {
                return state
            }

            requestRange(
                client = client,
                position = position,
                priority = if (position > 0L) {
                    TdDownloadPriority.CURRENT_TRACK_SEEK
                } else {
                    TdDownloadPriority.CURRENT_TRACK_AUDIO
                },
            )

            val pushedState = withTimeoutOrNull(LOST_UPDATE_RECOVERY_MS) {
                client.fileStates.awaitReadableOrCompleted(fileId, position)
            }
            if (pushedState != null) {
                state = pushedState
                recoveryAttempts = 0
                continue
            }

            if (++recoveryAttempts > MAX_LOST_UPDATE_RECOVERIES) {
                throw IOException("Timed out waiting for Telegram audio data at offset $position")
            }
            state = runCatching { client.refreshFileState(fileId) }
                .getOrElse { error ->
                    throw IOException("Unable to refresh Telegram audio state", error)
                }
        }
    }

    private fun openFileForState(state: TdApi.File): RandomAccessFile {
        val path = state.local.path
        if (path.isBlank()) throw IOException("Telegram has not created the local stream file")
        if (path != openedPath || randomAccessFile == null) {
            randomAccessFile?.close()
            randomAccessFile = RandomAccessFile(path, "r")
            openedPath = path
        }
        return requireNotNull(randomAccessFile)
    }

    private suspend fun requestRange(
        client: TdLibClientAdapter,
        position: Long,
        priority: Int,
    ) {
        val previousStart = requestedRangeStart
        if (
            previousStart != C.LENGTH_UNSET.toLong() &&
            position >= previousStart &&
            position < previousStart + STREAM_WINDOW_BYTES
        ) {
            return
        }

        val requested = runCatching {
            client.send(
                TdApi.DownloadFile(
                    fileId,
                    priority,
                    position.coerceAtLeast(0L),
                    STREAM_WINDOW_BYTES,
                    false,
                ),
            )
        }.getOrNull() ?: return
        requestedRangeStart = position.coerceAtLeast(0L)
        client.fileStates.publish(requested)
    }

    private fun isAtEnd(state: TdApi.File, position: Long): Boolean {
        if (!state.local.isDownloadingCompleted) return false
        val knownSize = state.size.toLong().takeIf { it > 0L }
        return knownSize?.let { position >= it } ?: (state.readableEndAt(position) <= position)
    }

    private companion object {
        const val STREAM_WINDOW_BYTES: Long = 4L * 1024L * 1024L
        const val LOST_UPDATE_RECOVERY_MS = 5_000L
        const val MAX_LOST_UPDATE_RECOVERIES = 6
    }
}
