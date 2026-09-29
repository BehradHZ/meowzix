package dev.behradhz.meowzix.data.telegram

/**
 * Central priority policy for TDLib media requests.
 *
 * TDLib accepts priorities in the 1..32 range. Keep latency-sensitive playback work above
 * speculative/background work so artwork and cache maintenance cannot delay audio reads.
 */
internal object TdDownloadPriority {
    const val CURRENT_TRACK_SEEK = 32
    const val CURRENT_TRACK_AUDIO = 31
    const val USER_REQUESTED_DOWNLOAD = 30
    const val NEXT_TRACK_PRELOAD = 24
    const val VISIBLE_ARTWORK = 18
    const val NEAR_VISIBLE_ARTWORK = 16
    const val BACKGROUND_TRACK_CACHE = 12
    const val ARTWORK_MAINTENANCE = 4
}
