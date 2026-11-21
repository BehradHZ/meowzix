package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/** Resolves a non-local track into a concrete local Media3-playable source on demand. */
interface RemoteTrackPlaybackResolver {
    suspend fun prepareForPlayback(trackId: UUID): PlayableTrack?
    /** A verified local copy, without contacting Telegram or starting a transfer. */
    suspend fun cachedLocalForPlayback(trackId: UUID): PlayableTrack? = null
    /** Bypass a failed app-owned local copy after a Media3 read/decoder error. */
    suspend fun prepareRemoteForPlayback(trackId: UUID): PlayableTrack? = prepareForPlayback(trackId)
    /** Revalidate a stale Telegram file ID on demand, never by scanning the playlist. */
    suspend fun recoverTelegramIdentity(trackId: UUID): PlayableTrack? = prepareForPlayback(trackId)
    /** Confirm removal using Telegram source metadata, not a generic timeout or network error. */
    suspend fun confirmDeletedSource(trackId: UUID): Boolean = false
    /** Cheap persisted check for a missing source after a player/process restart. */
    suspend fun isPreviouslyConfirmedMissing(trackId: UUID): Boolean = false
    fun prefetch(trackId: UUID)
    fun cancelPrefetch()
}
