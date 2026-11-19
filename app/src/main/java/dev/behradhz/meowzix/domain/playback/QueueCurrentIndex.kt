package dev.behradhz.meowzix.domain.playback

/** Prefer the playback occurrence index, then the queue index, then the first matching track. */
fun resolveCurrentQueueIndex(state: PlaybackState, queueState: QueueState): Int {
    val currentTrackId = state.currentTrack?.id ?: return -1
    if (state.queueIndex in queueState.items.indices &&
        queueState.items[state.queueIndex].id == currentTrackId
    ) return state.queueIndex
    if (queueState.currentIndex in queueState.items.indices &&
        queueState.items[queueState.currentIndex].id == currentTrackId
    ) return queueState.currentIndex
    return queueState.items.indexOfFirst { it.id == currentTrackId }
}
