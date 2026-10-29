package dev.behradhz.meowzix.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One delayed re-post for the latest track transition. This schedules work inside the active
 * playback service only; it does not wake a stopped service or force SystemUI/AOD to redraw.
 */
internal class LiveSongTransitionRetry(
    private val scope: CoroutineScope,
    private val retryDelayMs: Long,
    private val retry: (String) -> Unit,
) {
    private var pending: Job? = null

    fun schedule(mediaId: String?) {
        cancel()
        if (mediaId == null) return
        pending = scope.launch {
            delay(retryDelayMs)
            retry(mediaId)
        }
    }

    fun cancel() {
        pending?.cancel()
        pending = null
    }
}
