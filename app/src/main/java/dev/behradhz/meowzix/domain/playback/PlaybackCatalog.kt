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
     * Fast-path local availability check for one canonical Track. Implementations should avoid
     * loading/scanning the complete local playback catalog for this query.
     */
    suspend fun isTrackLocallyPlayable(trackId: UUID): Boolean =
        availableLocalTracks().any { it.id == trackId }

    /** Resolve one canonical track without materializing the complete playback catalog. */
    suspend fun playableTrack(trackId: UUID): PlayableTrack? =
        availableTracks(listOf(trackId)).firstOrNull()

    /**
     * Resolve only the requested logical queue. Implementations should keep the caller's UUID order
     * and avoid loading unrelated Track/TrackSource rows.
     */
    suspend fun availableTracks(trackIds: List<UUID>): List<PlayableTrack> {
        if (trackIds.isEmpty()) return emptyList()
        val requested = trackIds.toHashSet()
        val byId = availableTracks().associateBy(PlayableTrack::id)
        return trackIds.distinct().mapNotNull { id -> if (id in requested) byId[id] else null }
    }

    /**
     * Returns every currently playable library track. Remote Telegram rows are represented by a
     * meowzix-tdlib:// URI and are resolved lazily by the playback data source when they become
     * current. This is intentionally separate from availableLocalTracks so queue construction does
     * not force eager network downloads.
     */
    suspend fun availableTracks(): List<PlayableTrack> = availableLocalTracks()
}
