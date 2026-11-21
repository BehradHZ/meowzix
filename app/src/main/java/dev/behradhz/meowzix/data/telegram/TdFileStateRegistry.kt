package dev.behradhz.meowzix.data.telegram

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.drinkless.tdlib.TdApi

/**
 * Push-driven snapshot registry for TDLib files used by streaming/download code.
 *
 * TDLib's UpdateFile callback publishes into this registry before the update is fanned out to
 * higher-level observers. Readers can therefore wait on state changes without repeatedly sending
 * GetFile requests while the network or filesystem is catching up.
 */
internal class TdFileStateRegistry {
    private val states = ConcurrentHashMap<Int, MutableStateFlow<TdApi.File?>>()

    fun current(fileId: Int): TdApi.File? = states[fileId]?.value

    fun observe(fileId: Int): Flow<TdApi.File> = state(fileId).filterNotNull()

    fun publish(file: TdApi.File) {
        state(file.id).value = file
    }

    suspend fun awaitReadableOrCompleted(fileId: Int, offset: Long): TdApi.File =
        observe(fileId).first { file ->
            file.local.isDownloadingCompleted || file.readableEndAt(offset) > offset
        }

    /**
     * Wait for actual transfer progress as well as bytes becoming readable. A seek can legitimately
     * wait while TDLib downloads the prefix before [readableOffset]; that is progress, not a stall.
     */
    suspend fun awaitProgressOrReadable(
        fileId: Int,
        previous: TdApi.File,
        readableOffset: Long? = null,
    ): TdApi.File = observe(fileId).first { file ->
        (readableOffset?.let { offset ->
            file.local.isDownloadingCompleted || file.readableEndAt(offset) > offset
        } == true) ||
            file.local.downloadedSize != previous.local.downloadedSize ||
            file.local.downloadOffset != previous.local.downloadOffset ||
            file.local.downloadedPrefixSize != previous.local.downloadedPrefixSize ||
            file.local.isDownloadingActive != previous.local.isDownloadingActive ||
            file.local.isDownloadingCompleted != previous.local.isDownloadingCompleted ||
            file.local.path != previous.local.path
    }

    private fun state(fileId: Int): MutableStateFlow<TdApi.File?> =
        states.getOrPut(fileId) { MutableStateFlow(null) }
}

/**
 * End-exclusive byte offset that can be read safely from the current growing file at [offset].
 * A sparse/random-access download can make the physical file longer than the contiguous TDLib
 * range, so both TDLib range metadata and the actual file length are respected.
 */
internal fun TdApi.File.readableEndAt(offset: Long): Long {
    val path = local.path.takeIf(String::isNotBlank) ?: return offset
    val physicalLength = File(path).takeIf(File::isFile)?.length() ?: return offset
    if (local.isDownloadingCompleted) return physicalLength

    val rangeStart = local.downloadOffset.toLong().coerceAtLeast(0L)
    val rangeEnd = rangeStart + local.downloadedPrefixSize.toLong().coerceAtLeast(0L)
    if (offset < rangeStart || offset >= rangeEnd) return offset
    return minOf(rangeEnd, physicalLength)
}

/**
 * A partial request is satisfied only by the contiguous bytes starting at its
 * requested offset. Total downloaded bytes (including sparse ranges) are not
 * evidence that Media3 can decode the beginning of a track.
 */
internal fun satisfiesDownloadRequest(
    offset: Long,
    limit: Long,
    fileSize: Long,
    readableEnd: Long,
    completed: Boolean,
): Boolean {
    if (completed) return true
    if (limit <= 0L) return false
    val remaining = if (fileSize > 0L) (fileSize - offset).coerceAtLeast(0L) else limit
    return readableEnd >= offset + minOf(limit, remaining)
}
