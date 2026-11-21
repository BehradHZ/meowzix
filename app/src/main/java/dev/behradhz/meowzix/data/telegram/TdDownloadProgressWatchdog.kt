package dev.behradhz.meowzix.data.telegram

internal const val TD_DOWNLOAD_STALL_TIMEOUT_MS = 30_000L
internal const val TD_DOWNLOAD_MAX_RECOVERIES = 2

internal enum class TdDownloadProgressState {
    INITIAL,
    PROGRESSED,
    WAITING,
    STALLED,
}

/**
 * Detects a stalled Telegram transfer without confusing a slow seek with a stopped download.
 *
 * TDLib's downloadedSize tracks total downloaded bytes, while readableEnd tracks the contiguous
 * bytes available at the consumer's current offset. Either increasing is meaningful progress.
 */
internal class TdDownloadProgressWatchdog(
    private val stallTimeoutMs: Long = TD_DOWNLOAD_STALL_TIMEOUT_MS,
) {
    init {
        require(stallTimeoutMs > 0L)
    }

    private var lastDownloadedSize: Long? = null
    private var lastReadableEnd: Long? = null
    private var lastProgressAtMs = 0L

    fun observe(
        downloadedSize: Long,
        readableEnd: Long,
        nowMs: Long,
    ): TdDownloadProgressState {
        val previousDownloadedSize = lastDownloadedSize
        val previousReadableEnd = lastReadableEnd
        if (previousDownloadedSize == null || previousReadableEnd == null) {
            reset(downloadedSize, readableEnd, nowMs)
            return TdDownloadProgressState.INITIAL
        }

        if (downloadedSize > previousDownloadedSize || readableEnd > previousReadableEnd) {
            reset(downloadedSize, readableEnd, nowMs)
            return TdDownloadProgressState.PROGRESSED
        }

        return if ((nowMs - lastProgressAtMs).coerceAtLeast(0L) >= stallTimeoutMs) {
            TdDownloadProgressState.STALLED
        } else {
            TdDownloadProgressState.WAITING
        }
    }

    fun reset(downloadedSize: Long, readableEnd: Long, nowMs: Long) {
        lastDownloadedSize = downloadedSize.coerceAtLeast(0L)
        lastReadableEnd = readableEnd.coerceAtLeast(0L)
        lastProgressAtMs = nowMs
    }
}
