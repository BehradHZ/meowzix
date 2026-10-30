package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/** Resolves a non-local track into a concrete local Media3-playable source on demand. */
interface RemoteTrackPlaybackResolver {
    suspend fun prepareForPlayback(trackId: UUID): PlayableTrack?
    /** Bypass a failed app-owned local copy after a Media3 read/decoder error. */
    suspend fun prepareRemoteForPlayback(trackId: UUID): PlayableTrack? = prepareForPlayback(trackId)
    fun prefetch(trackId: UUID)
    fun cancelPrefetch()
}
