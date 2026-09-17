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
    suspend fun availableLocalTracks(): List<PlayableTrack>
}
