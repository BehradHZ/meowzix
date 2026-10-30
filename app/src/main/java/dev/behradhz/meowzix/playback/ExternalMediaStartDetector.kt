package dev.behradhz.meowzix.playback

/**
 * Public AudioPlaybackCallback exposes active media player counts, not the identities of
 * other applications' players. Compare the estimated external count across callbacks so
 * starting or stopping Meowzix (including its crossfade player) is not an interruption.
 *
 * This is a best-effort early signal. Media3 retains Android audio-focus ownership
 * and handles losses that are not observable through playback configurations.
 */
internal class ExternalMediaStartDetector {
    private var initialized = false
    var externalCount: Int = 0
        private set

    fun baseline(activeMediaPlayers: Int, audibleLocalPlayers: Int) {
        externalCount = estimatedExternalCount(activeMediaPlayers, audibleLocalPlayers)
        initialized = true
    }

    fun observe(
        activeMediaPlayers: Int,
        audibleLocalPlayers: Int,
        playbackRequested: Boolean,
    ): Boolean {
        val currentExternalCount = estimatedExternalCount(activeMediaPlayers, audibleLocalPlayers)
        val newExternalPlayback = initialized && currentExternalCount > externalCount
        externalCount = currentExternalCount
        initialized = true
        return playbackRequested && newExternalPlayback
    }

    private fun estimatedExternalCount(activeMediaPlayers: Int, audibleLocalPlayers: Int): Int =
        (activeMediaPlayers.coerceAtLeast(0) - audibleLocalPlayers.coerceAtLeast(0))
            .coerceAtLeast(0)
}
