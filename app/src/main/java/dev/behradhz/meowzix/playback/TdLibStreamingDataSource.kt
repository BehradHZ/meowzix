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
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi

private const val TDLIB_SCHEME = "meowzix-tdlib"

/** Routes normal Android URIs through Media3 and Telegram cloud URIs through the growing-file reader. */
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
 * Reads a TDLib file while it is still growing. Opening a cloud track starts a normal background
 * download, but playback waits only for the bytes it currently needs. Seeking into an unloaded
 * range raises that range's priority instead of waiting for the whole song.
 */
@OptIn(UnstableApi::class)
private class TdLibStreamingDataSource : BaseDataSource(true) {
    private var openedUri: Uri? = null
    private var dataSpec: DataSpec? = null
    private var fileId: Int = 0
    private var randomAccessFile: RandomAccessFile? = null
    private var openedPath: String? = null
    private var readPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val id = dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid Telegram playback URI: ${dataSpec.uri}")
        val client = TdLibClientAdapter.activeOrNull()
            ?: throw IOException("Telegram is not ready yet")

        openedUri = dataSpec.uri
        this.dataSpec = dataSpec
        fileId = id
        readPosition = dataSpec.position
        bytesRemaining = dataSpec.length

        runBlocking {
            // Start the complete download at normal priority. This is non-blocking: playback begins
            // as soon as the required prefix arrives and the remainder continues in parallel.
            runCatching { client.send(TdApi.DownloadFile(id, PLAYBACK_PRIORITY, 0L, 0L, false)) }
            if (readPosition > 0L) requestRange(client, readPosition)
            ensureReadable(client, readPosition)
        }

        opened = true
        transferStarted(dataSpec)
        val knownSize = runBlocking { runCatching { client.send(TdApi.GetFile(id)) }.getOrNull()?.size?.toLong() }
            ?.takeIf { it > 0L }
        if (bytesRemaining == C.LENGTH_UNSET.toLong() && knownSize != null) {
            bytesRemaining = (knownSize - readPosition).coerceAtLeast(0L)
        }
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val client = TdLibClientAdapter.activeOrNull()
            ?: throw IOException("Telegram disconnected during playback")

        val readableEnd = runBlocking { waitForReadableEnd(client, readPosition) }
        if (readableEnd <= readPosition) return C.RESULT_END_OF_INPUT
        val available = (readableEnd - readPosition).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val remainingLimit = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            Int.MAX_VALUE
        } else {
            bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        val toRead = min(length, min(available, remainingLimit))
        if (toRead <= 0) return C.RESULT_END_OF_INPUT

        val raf = openFileForCurrentPath(client)
        raf.seek(readPosition)
        val read = raf.read(buffer, offset, toRead)
        if (read <= 0) return C.RESULT_END_OF_INPUT
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
        dataSpec = null
        fileId = 0
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private suspend fun waitForReadableEnd(client: TdLibClientAdapter, position: Long): Long {
        var rangeRequested = false
        repeat(MAX_WAIT_POLLS) {
            val file = client.send(TdApi.GetFile(fileId))
            val local = file.local
            val physicalLength = local.path
                .takeIf(String::isNotBlank)
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.length()
                ?: 0L

            if (local.isDownloadingCompleted && physicalLength > position) {
                return physicalLength
            }

            val rangeStart = local.downloadOffset.toLong()
            val rangeEnd = rangeStart + local.downloadedPrefixSize.toLong().coerceAtLeast(0L)
            val usableEnd = min(rangeEnd, physicalLength)
            if (position >= rangeStart && position < usableEnd) return usableEnd

            if (local.isDownloadingCompleted) {
                // TDLib says the transfer is done but the filesystem has not exposed the final
                // bytes yet. Give it a few polls instead of reporting a false readable range.
                delay(POLL_INTERVAL_MS)
                return@repeat
            }

            if (!rangeRequested) {
                requestRange(client, position)
                rangeRequested = true
            }
            delay(POLL_INTERVAL_MS)
        }
        throw IOException("Timed out waiting for Telegram audio data")
    }

    private suspend fun ensureReadable(client: TdLibClientAdapter, position: Long) {
        waitForReadableEnd(client, position)
        openFileForCurrentPath(client)
    }

    private fun openFileForCurrentPath(client: TdLibClientAdapter): RandomAccessFile {
        val path = runBlocking { client.send(TdApi.GetFile(fileId)).local.path }
        if (path.isBlank()) throw IOException("Telegram has not created the local stream file")
        if (path != openedPath || randomAccessFile == null) {
            randomAccessFile?.close()
            randomAccessFile = RandomAccessFile(path, "r")
            openedPath = path
        }
        return requireNotNull(randomAccessFile)
    }

    private suspend fun requestRange(client: TdLibClientAdapter, position: Long) {
        runCatching {
            client.send(
                TdApi.DownloadFile(
                    fileId,
                    SEEK_PRIORITY,
                    position.coerceAtLeast(0L),
                    SEEK_WINDOW_BYTES,
                    false,
                ),
            )
        }
    }

    private companion object {
        const val PLAYBACK_PRIORITY = 24
        const val SEEK_PRIORITY = 32
        const val SEEK_WINDOW_BYTES = 4L * 1024L * 1024L
        const val POLL_INTERVAL_MS = 35L
        const val MAX_WAIT_POLLS = 860 // ~30 seconds on a badly stalled connection.
    }
}
