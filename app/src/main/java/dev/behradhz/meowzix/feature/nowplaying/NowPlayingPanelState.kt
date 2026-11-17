package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Queue temporarily sits above Lyrics. Closing it restores the panel that was
 * visible before the queue opened, without changing lyrics scroll/follow state.
 */
internal class NowPlayingPanelState {
    var lyricsExpanded by mutableStateOf(false)
        private set
    var queueExpanded by mutableStateOf(false)
        private set
    private var restoreLyricsOnQueueClose = false

    fun setQueueExpanded(expanded: Boolean) {
        if (expanded) {
            if (!queueExpanded) restoreLyricsOnQueueClose = lyricsExpanded
            queueExpanded = true
            lyricsExpanded = false
        } else if (queueExpanded) {
            queueExpanded = false
            lyricsExpanded = restoreLyricsOnQueueClose
            restoreLyricsOnQueueClose = false
        }
    }

    fun toggleQueue() = setQueueExpanded(!queueExpanded)

    fun expandLyrics() {
        queueExpanded = false
        restoreLyricsOnQueueClose = false
        lyricsExpanded = true
    }

    fun toggleLyrics() {
        if (queueExpanded) {
            expandLyrics()
        } else {
            lyricsExpanded = !lyricsExpanded
        }
    }

    /** Return true when an expanded surface consumed Back. */
    fun collapseForeground(): Boolean = when {
        queueExpanded -> {
            setQueueExpanded(false)
            true
        }
        lyricsExpanded -> {
            lyricsExpanded = false
            true
        }
        else -> false
    }

    fun onTrackChanged() {
        lyricsExpanded = false
        restoreLyricsOnQueueClose = false
    }
}
