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
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

private const val TDLIB_SCHEME = "meowzix-tdlib"

@OptIn(UnstableApi::class)
class MeowzixDataSourceFactory(context: Context) : DataSource.Factory {
    private val appContext = context.applicationContext
    private val defaultFactory = DefaultDataSource.Factory(appContext)

    override fun createDataSource(): DataSource = RoutingDataSource(appContext, defaultFactory)
}

@OptIn(UnstableApi::class)
private class RoutingDataSource(
    private val context: Context,
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

        val completedLocalCopy = if (dataSpec.uri.scheme == TDLIB_SCHEME) {
            findCompletedLocalCopy(context, dataSpec.uri)
        } else {
            null
        }
        val source: DataSource
        val routedSpec: DataSpec
        if (completedLocalCopy != null) {
            source = defaultFactory.createDataSource()
            routedSpec = dataSpec.withUri(Uri.fromFile(completedLocalCopy))
        } else if (dataSpec.uri.scheme == TDLIB_SCHEME) {
            source = TdLibStreamingDataSource()
            routedSpec = dataSpec
        } else {
            source = defaultFactory.createDataSource()
            routedSpec = dataSpec
        }

        listeners.forEach(source::addTransferListener)
        active = source
        return source.open(routedSpec)
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

private fun findCompletedLocalCopy(context: Context, streamUri: Uri): File? {
    val trackId = streamUri.getQueryParameter("trackId")
        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: return null
    val prefix = trackId.toString()
    val roots = listOf(
        File(context.filesDir, "offline"),
        File(context.filesDir, "playback-cache"),
    )
    return roots.asSequence()
        .filter(File::isDirectory)
        .flatMap { root -> root.listFiles().orEmpty().asSequence() }
        .firstOrNull { file ->
            file.isFile && file.length() > 0L &&
                (file.name == prefix || file.name.startsWith("$prefix.")) &&
                !file.name.endsWith(".part")
        }
}

/**
 * Random-access reader over TDLib's growing local file.
 *
 * A temporary lack of bytes is a buffering condition, not an end-of-file or source error. The
 * resolver starts a continuous TDLib download before Media3 opens this source. Sequential reads
 * keep that download alive and block the Media3 loader thread until the requested bytes arrive.
 * A genuine seek reprioritizes TDLib at the new offset and then continues downloading from there.
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

    @Volatile
    private var closed = true

    @Volatile
    private var blockingThread: Thread? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        closed = false
        val id = dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid Telegram playback URI: ${dataSpec.uri}")
        val client = runBlockingIo { awaitActiveClient() }
            ?: throw IOException("Telegram is not ready yet after retrying")

        openedUri = dataSpec.uri
        fileId = id
        readPosition = dataSpec.position
        bytesRemaining = dataSpec.length

        val initialWindow = runBlockingIo {
            val seeded = seedFileStateWithRetry(client, id)
            if (!seeded.local.isDownloadingCompleted) {
                ensureDownload(
                    client = client,
                    state = seeded,
                    position = readPosition,
                    priority = if (readPosition > 0L) {
                        TdDownloadPriority.CURRENT_TRACK_SEEK
                    } else {
                        TdDownloadPriority.CURRENT_TRACK_AUDIO
                    },
                    force = readPosition > 0L,
                )
            }
            waitForReadableWindow(readPosition)
        }

        if (initialWindow.readableEnd > readPosition) {
            openFileForState(initialWindow.state)
        } else if (!isAtEnd(initialWindow.state, readPosition)) {
            throw IOException("Telegram stream has no readable bytes at offset $readPosition")
        }

        val knownSize = initialWindow.state.size.toLong().takeIf { it > 0L }
        if (bytesRemaining == C.LENGTH_UNSET.toLong() && knownSize != null) {
            bytesRemaining = (knownSize - readPosition).coerceAtLeast(0L)
        }

        checkOpen()
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        checkOpen()
        return runBlockingIo { readFromGrowingFile(buffer, offset, length) }
    }

    // A block-bodied suspend function gives the retry loop an explicit Int return contract.
    // Keeping the loop as the last expression of runBlocking inferred Unit instead.
    private suspend fun readFromGrowingFile(buffer: ByteArray, offset: Int, length: Int): Int {
        var staleHandleRecoveries = 0
        while (true) {
            checkOpen()
            val window = waitForReadableWindow(readPosition)
            if (window.readableEnd <= readPosition) {
                if (isAtEnd(window.state, readPosition)) {
                    return C.RESULT_END_OF_INPUT
                }
                continue
            }

            val available = (window.readableEnd - readPosition)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val remainingLimit = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                Int.MAX_VALUE
            } else {
                bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            val toRead = min(length, min(available, remainingLimit))
            if (toRead <= 0) return C.RESULT_END_OF_INPUT

            val read = runCatching {
                val raf = openFileForState(window.state)
                raf.seek(readPosition)
                raf.read(buffer, offset, toRead)
            }.getOrElse {
                resetFileHandle()
                -1
            }

            if (read > 0) {
                readPosition += read
                if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
                bytesTransferred(read)
                return read
            }

            if (isAtEnd(window.state, readPosition)) {
                return C.RESULT_END_OF_INPUT
            }

            // TDLib may rotate/recreate the growing local file while retaining the same path.
            // Reopen the descriptor and wait for the filesystem view to catch up instead of
            // reporting a fatal source error to ExoPlayer.
            resetFileHandle()
            staleHandleRecoveries += 1
            if (staleHandleRecoveries > MAX_STALE_FILE_RECOVERIES) {
                val activeClient = awaitActiveClient()
                    ?: throw IOException("Telegram remained disconnected during playback")
                runCatching { activeClient.refreshFileState(fileId) }
                staleHandleRecoveries = 0
            }
            delay(STALE_FILE_RETRY_BASE_DELAY_MS * staleHandleRecoveries.coerceAtLeast(1).coerceAtMost(6))
        }
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        closed = true
        // Media3 invokes close from teardown while a loader thread may be blocked in runBlocking.
        // Interrupting that exact thread makes cancellation immediate rather than waiting through
        // multiple TDLib update timeouts. runBlocking converts interruption to cancellation and the
        // helper below surfaces it as InterruptedIOException, never as a fake end-of-input.
        blockingThread?.interrupt()
        resetFileHandle()
        openedUri = null
        fileId = 0
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T {
        checkOpen()
        val thread = Thread.currentThread()
        blockingThread = thread
        if (closed) {
            if (blockingThread === thread) blockingThread = null
            throw InterruptedIOException("Telegram stream was closed")
        }
        return try {
            runBlocking { block() }
        } catch (interrupted: InterruptedException) {
            Thread.interrupted()
            throw InterruptedIOException("Telegram stream read was cancelled").apply {
                initCause(interrupted)
            }
        } finally {
            if (blockingThread === thread) blockingThread = null
        }
    }

    private fun checkOpen() {
        if (closed || Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("Telegram stream was closed")
        }
    }

    private suspend fun awaitActiveClient(): TdLibClientAdapter? {
        repeat(CLIENT_READY_ATTEMPTS) { attempt ->
            checkOpen()
            TdLibClientAdapter.activeOrNull()?.let { return it }
            delay(CLIENT_READY_RETRY_DELAY_MS * (attempt + 1).coerceAtMost(4))
        }
        checkOpen()
        return TdLibClientAdapter.activeOrNull()
    }

    private suspend fun seedFileStateWithRetry(
        client: TdLibClientAdapter,
        id: Int,
    ): TdApi.File {
        var lastError: Throwable? = null
        repeat(FILE_STATE_SEED_ATTEMPTS) { attempt ->
            checkOpen()
            runCatching { client.seedFileState(id) }
                .onSuccess { return it }
                .onFailure { lastError = it }
            delay(FILE_STATE_RETRY_BASE_DELAY_MS * (attempt + 1))
        }
        throw IOException("Unable to resolve Telegram file state after retrying", lastError)
    }

    private suspend fun waitForReadableWindow(position: Long): ReadableWindow {
        var recoveryAttempts = 0
        while (true) {
            checkOpen()
            val client = awaitActiveClient()
                ?: throw IOException("Telegram remained disconnected during playback")
            var state = client.fileStates.current(fileId) ?: seedFileStateWithRetry(client, fileId)
            val readableEnd = resolveReadableEnd(client, state, position)
            if (readableEnd > position || isAtEnd(state, position)) {
                return ReadableWindow(state, readableEnd)
            }

            ensureDownload(
                client = client,
                state = state,
                position = position,
                priority = if (position > 0L) {
                    TdDownloadPriority.CURRENT_TRACK_SEEK
                } else {
                    TdDownloadPriority.CURRENT_TRACK_AUDIO
                },
                force = false,
            )

            val pushedState = withTimeoutOrNull(UPDATE_WAIT_TIMEOUT_MS) {
                client.fileStates.awaitReadableOrCompleted(fileId, position)
            }
            checkOpen()
            if (pushedState != null) {
                state = pushedState
                val pushedReadableEnd = resolveReadableEnd(client, state, position)
                if (pushedReadableEnd > position || isAtEnd(state, position)) {
                    return ReadableWindow(state, pushedReadableEnd)
                }
                continue
            }

            recoveryAttempts += 1
            if (recoveryAttempts > MAX_UPDATE_RECOVERIES) {
                throw IOException("Timed out waiting for Telegram audio data at offset $position")
            }

            val refreshed = runCatching { client.refreshFileState(fileId) }.getOrNull()
            if (refreshed != null) {
                state = refreshed
                val refreshedReadableEnd = resolveReadableEnd(client, state, position)
                if (refreshedReadableEnd > position || isAtEnd(state, position)) {
                    return ReadableWindow(state, refreshedReadableEnd)
                }

                // If TDLib stopped the request (for example after a client restart or a previous
                // finite-range request), resume continuously from the byte Media3 currently needs.
                if (!state.local.isDownloadingActive && !state.local.isDownloadingCompleted) {
                    ensureDownload(
                        client = client,
                        state = state,
                        position = position,
                        priority = if (position > 0L) {
                            TdDownloadPriority.CURRENT_TRACK_SEEK
                        } else {
                            TdDownloadPriority.CURRENT_TRACK_AUDIO
                        },
                        force = true,
                    )
                }
            } else {
                delay(FILE_STATE_RETRY_BASE_DELAY_MS * recoveryAttempts.coerceAtMost(6))
            }
        }
    }

    /**
     * TDLib's localFile only exposes the prefix for its current downloadOffset. Extractors can seek
     * around a progressive file, so a valid previously-downloaded range may exist even when that
     * range isn't represented by local.downloadedPrefixSize anymore. Query TDLib for the exact
     * offset before deciding that Media3 has no bytes to read.
     */
    private suspend fun resolveReadableEnd(
        client: TdLibClientAdapter,
        state: TdApi.File,
        position: Long,
    ): Long {
        checkOpen()
        val localEnd = state.readableEndAt(position)
        if (localEnd > position || state.local.isDownloadingCompleted) return localEnd
        val path = state.local.path
        if (path.isBlank()) return position

        val prefixSize = runCatching {
            client.send(TdApi.GetFileDownloadedPrefixSize(fileId, position)).size
        }.getOrDefault(0L)
        checkOpen()
        if (prefixSize <= 0L) return position

        val physicalLength = runCatching { File(path).length() }.getOrDefault(0L)
        if (physicalLength <= position) return position
        return (position + prefixSize).coerceAtMost(physicalLength)
    }

    private suspend fun ensureDownload(
        client: TdLibClientAdapter,
        state: TdApi.File,
        position: Long,
        priority: Int,
        force: Boolean,
    ) {
        checkOpen()
        if (state.local.isDownloadingCompleted) return
        val normalizedPosition = position.coerceAtLeast(0L)

        // The playback resolver already starts an unlimited download from offset 0. Do not replace
        // that healthy request every time Media3 reaches the current prefix: a different offset or
        // limit replaces TDLib's active request. Only reprioritize when seeking or after it stopped.
        val activeRequestCanReachPosition =
            state.local.isDownloadingActive && state.local.downloadOffset <= normalizedPosition
        if (!force && activeRequestCanReachPosition) return

        val requested = runCatching {
            client.send(
                TdApi.DownloadFile(
                    fileId,
                    priority,
                    normalizedPosition,
                    0L,
                    false,
                ),
            )
        }.getOrNull() ?: return
        checkOpen()
        client.fileStates.publish(requested)
    }

    private fun openFileForState(state: TdApi.File): RandomAccessFile {
        checkOpen()
        val path = state.local.path
        if (path.isBlank()) throw IOException("Telegram has not created the local stream file")
        if (path != openedPath || randomAccessFile == null) {
            resetFileHandle()
            randomAccessFile = RandomAccessFile(path, "r")
            openedPath = path
        }
        return requireNotNull(randomAccessFile)
    }

    private fun resetFileHandle() {
        runCatching { randomAccessFile?.close() }
        randomAccessFile = null
        openedPath = null
    }

    private fun isAtEnd(state: TdApi.File, position: Long): Boolean {
        if (!state.local.isDownloadingCompleted) return false
        val knownSize = state.size.toLong().takeIf { it > 0L }
        return knownSize?.let { position >= it } ?: (state.readableEndAt(position) <= position)
    }

    private data class ReadableWindow(
        val state: TdApi.File,
        val readableEnd: Long,
    )

    private companion object {
        const val UPDATE_WAIT_TIMEOUT_MS = 4_000L
        const val MAX_UPDATE_RECOVERIES = 15
        const val MAX_STALE_FILE_RECOVERIES = 8
        const val STALE_FILE_RETRY_BASE_DELAY_MS = 50L
        const val CLIENT_READY_ATTEMPTS = 8
        const val CLIENT_READY_RETRY_DELAY_MS = 250L
        const val FILE_STATE_SEED_ATTEMPTS = 6
        const val FILE_STATE_RETRY_BASE_DELAY_MS = 350L
    }
}
