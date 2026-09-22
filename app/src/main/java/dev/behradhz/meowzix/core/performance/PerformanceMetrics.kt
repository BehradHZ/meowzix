package dev.behradhz.meowzix.core.performance

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-local, bounded performance counters.
 *
 * No track names, Telegram identifiers, file paths, or other user data are recorded here. The
 * counters intentionally use constant memory so performance instrumentation cannot become a source
 * of jank itself.
 */
object PerformanceMetrics {
    private val processStartNs = SystemClock.elapsedRealtimeNanos()
    private val firstFrameMs = AtomicLong(UNSET)

    private val artworkCacheHits = AtomicLong(0)
    private val artworkCacheMisses = AtomicLong(0)
    private val artworkDecodeCount = AtomicLong(0)
    private val artworkDecodeTotalMs = AtomicLong(0)
    private val artworkDecodeMaxMs = AtomicLong(0)

    private val libraryRefreshCount = AtomicLong(0)
    private val libraryRefreshTotalMs = AtomicLong(0)
    private val libraryRefreshMaxMs = AtomicLong(0)

    private val telegramSyncCount = AtomicLong(0)
    private val telegramSyncTotalMs = AtomicLong(0)
    private val telegramSyncMaxMs = AtomicLong(0)

    private val playbackPrepareCount = AtomicLong(0)
    private val playbackPrepareTotalMs = AtomicLong(0)
    private val playbackPrepareMaxMs = AtomicLong(0)

    fun markFirstFrame() {
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - processStartNs) / NANOS_PER_MILLI
        firstFrameMs.compareAndSet(UNSET, elapsedMs)
    }

    fun recordArtworkCache(hit: Boolean) {
        if (hit) artworkCacheHits.incrementAndGet() else artworkCacheMisses.incrementAndGet()
    }

    fun recordArtworkDecode(durationMs: Long) = recordTiming(
        durationMs,
        artworkDecodeCount,
        artworkDecodeTotalMs,
        artworkDecodeMaxMs,
    )

    fun recordLibraryRefresh(durationMs: Long) = recordTiming(
        durationMs,
        libraryRefreshCount,
        libraryRefreshTotalMs,
        libraryRefreshMaxMs,
    )

    fun recordTelegramSync(durationMs: Long) = recordTiming(
        durationMs,
        telegramSyncCount,
        telegramSyncTotalMs,
        telegramSyncMaxMs,
    )

    fun recordPlaybackPrepare(durationMs: Long) = recordTiming(
        durationMs,
        playbackPrepareCount,
        playbackPrepareTotalMs,
        playbackPrepareMaxMs,
    )

    fun snapshot(): PerformanceSnapshot {
        val runtime = Runtime.getRuntime()
        val artworkCount = artworkDecodeCount.get()
        val refreshCount = libraryRefreshCount.get()
        val syncCount = telegramSyncCount.get()
        val prepareCount = playbackPrepareCount.get()
        return PerformanceSnapshot(
            firstFrameMs = firstFrameMs.get().takeUnless { it == UNSET },
            heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
            heapMaxBytes = runtime.maxMemory(),
            artworkCacheHits = artworkCacheHits.get(),
            artworkCacheMisses = artworkCacheMisses.get(),
            artworkDecodeCount = artworkCount,
            artworkDecodeAverageMs = average(artworkDecodeTotalMs.get(), artworkCount),
            artworkDecodeMaxMs = artworkDecodeMaxMs.get(),
            libraryRefreshCount = refreshCount,
            libraryRefreshAverageMs = average(libraryRefreshTotalMs.get(), refreshCount),
            libraryRefreshMaxMs = libraryRefreshMaxMs.get(),
            telegramSyncCount = syncCount,
            telegramSyncAverageMs = average(telegramSyncTotalMs.get(), syncCount),
            telegramSyncMaxMs = telegramSyncMaxMs.get(),
            playbackPrepareCount = prepareCount,
            playbackPrepareAverageMs = average(playbackPrepareTotalMs.get(), prepareCount),
            playbackPrepareMaxMs = playbackPrepareMaxMs.get(),
        )
    }

    fun report(): String = snapshot().toReport()

    private fun recordTiming(
        durationMs: Long,
        count: AtomicLong,
        totalMs: AtomicLong,
        maxMs: AtomicLong,
    ) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        count.incrementAndGet()
        totalMs.addAndGet(safeDuration)
        maxMs.accumulateAndGet(safeDuration) { current, update -> maxOf(current, update) }
    }

    private fun average(total: Long, count: Long): Long = if (count == 0L) 0L else total / count

    private const val UNSET = -1L
    private const val NANOS_PER_MILLI = 1_000_000L
}

data class PerformanceSnapshot(
    val firstFrameMs: Long?,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val artworkCacheHits: Long,
    val artworkCacheMisses: Long,
    val artworkDecodeCount: Long,
    val artworkDecodeAverageMs: Long,
    val artworkDecodeMaxMs: Long,
    val libraryRefreshCount: Long,
    val libraryRefreshAverageMs: Long,
    val libraryRefreshMaxMs: Long,
    val telegramSyncCount: Long,
    val telegramSyncAverageMs: Long,
    val telegramSyncMaxMs: Long,
    val playbackPrepareCount: Long,
    val playbackPrepareAverageMs: Long,
    val playbackPrepareMaxMs: Long,
) {
    fun toReport(): String = buildString {
        append("firstFrameMs=").append(firstFrameMs ?: "pending")
        append(" heapMiB=").append(heapUsedBytes / MEBIBYTE).append('/').append(heapMaxBytes / MEBIBYTE)
        append(" artworkCache=").append(artworkCacheHits).append('/').append(artworkCacheMisses)
        append(" artworkDecodeMs(avg/max)=").append(artworkDecodeAverageMs).append('/').append(artworkDecodeMaxMs)
        append(" libraryRefreshMs(avg/max)=").append(libraryRefreshAverageMs).append('/').append(libraryRefreshMaxMs)
        append(" telegramSyncMs(avg/max)=").append(telegramSyncAverageMs).append('/').append(telegramSyncMaxMs)
        append(" playbackPrepareMs(avg/max)=").append(playbackPrepareAverageMs).append('/').append(playbackPrepareMaxMs)
    }

    private companion object {
        const val MEBIBYTE = 1024L * 1024L
    }
}
