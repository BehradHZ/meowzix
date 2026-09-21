package dev.behradhz.meowzix.domain.playback

import java.util.UUID

/** Resolves a non-local track into a concrete local Media3-playable source on demand. */
interface RemoteTrackPlaybackResolver {
    suspend fun prepareForPlayback(trackId: UUID): PlayableTrack?
    fun prefetch(trackId: UUID)
    fun cancelPrefetch()
}
