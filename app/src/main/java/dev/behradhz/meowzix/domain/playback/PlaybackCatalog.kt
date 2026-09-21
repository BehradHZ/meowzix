package dev.behradhz.meowzix.domain.playback

import java.util.UUID

data class PlayableTrack(
    val id: UUID,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkRef: String?,
    val contentUri: String,
)

interface PlaybackCatalog {
    /** Returns only sources that are already readable from local storage. */
    suspend fun availableLocalTracks(): List<PlayableTrack>

    /**
     * Returns every currently playable library track. Remote Telegram rows are represented by a
     * meowzix-tdlib:// URI and are resolved lazily by the playback data source when they become
     * current. This is intentionally separate from availableLocalTracks so queue construction does
     * not force eager network downloads.
     */
    suspend fun availableTracks(): List<PlayableTrack> = availableLocalTracks()
}
