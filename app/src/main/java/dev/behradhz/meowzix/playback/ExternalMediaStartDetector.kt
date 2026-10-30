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
    private var previousTotalPlayers = 0
    private var previousLocalPlayers = 0
    var externalCount: Int = 0
        private set

    fun baseline(activeMediaPlayers: Int, audibleLocalPlayers: Int) {
        previousTotalPlayers = activeMediaPlayers.coerceAtLeast(0)
        previousLocalPlayers = audibleLocalPlayers.coerceAtLeast(0)
        externalCount = estimatedExternalCount(activeMediaPlayers, audibleLocalPlayers)
        initialized = true
    }

    fun observe(
        activeMediaPlayers: Int,
        audibleLocalPlayers: Int,
        playbackRequested: Boolean,
    ): Boolean {
        val total = activeMediaPlayers.coerceAtLeast(0)
        val local = audibleLocalPlayers.coerceAtLeast(0)
        val estimated = estimatedExternalCount(total, local)
        // Our own AudioTrack can remain in Android's active list for a callback
        // after ExoPlayer has been paused. That temporary mismatch must not be
        // interpreted as a NEW external player (or block a microphone resume).
        val onlyOurPlayerStopping = initialized &&
            local < previousLocalPlayers && total <= previousTotalPlayers
        val currentExternalCount =
            if (onlyOurPlayerStopping && estimated > externalCount) externalCount
            else estimated
        val newExternalPlayback = initialized && currentExternalCount > externalCount
        externalCount = currentExternalCount
        previousTotalPlayers = total
        previousLocalPlayers = local
        initialized = true
        return playbackRequested && newExternalPlayback
    }

    private fun estimatedExternalCount(activeMediaPlayers: Int, audibleLocalPlayers: Int): Int =
        (activeMediaPlayers.coerceAtLeast(0) - audibleLocalPlayers.coerceAtLeast(0))
            .coerceAtLeast(0)
}
