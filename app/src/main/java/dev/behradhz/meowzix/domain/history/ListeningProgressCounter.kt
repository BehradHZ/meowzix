package dev.behradhz.meowzix.domain.history

import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus

/** In-memory accounting only. Position ticks never become database events or training samples. */
object ListeningProgressCounter {
    fun delta(previous: PlaybackState, current: PlaybackState, seekPending: Boolean = false): Long {
        if (seekPending || current.seekRevision != previous.seekRevision ||
            previous.status != PlaybackStatus.PLAYING || current.currentTrack?.id != previous.currentTrack?.id ||
            current.playbackOccurrenceId != previous.playbackOccurrenceId) return 0L
        val delta = current.positionMs - previous.positionMs
        // Conservative after a stalled collector: do not count a large discontinuity as listening.
        return if (delta in 0L..5_000L) delta else 0L
    }
}
