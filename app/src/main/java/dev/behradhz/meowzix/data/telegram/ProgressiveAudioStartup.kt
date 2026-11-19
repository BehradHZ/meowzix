package dev.behradhz.meowzix.data.telegram

/**
 * Initial Telegram download request window for cold playback.
 *
 * On a cold start, TDLib returns after only this contiguous prefix has been
 * fetched, not after the full file. Media3 then determines its required playable
 * duration; the unlimited retained download continues independently.
 */
internal const val INITIAL_STREAMING_PREFIX_BYTES: Long = 64L * 1024L

internal fun initialStreamingPrefixLimit(fileSizeBytes: Long?): Long =
    fileSizeBytes?.takeIf { it > 0L }?.coerceAtMost(INITIAL_STREAMING_PREFIX_BYTES)
        ?: INITIAL_STREAMING_PREFIX_BYTES
