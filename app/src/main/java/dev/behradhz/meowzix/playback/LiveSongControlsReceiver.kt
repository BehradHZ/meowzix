package dev.behradhz.meowzix.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Shared SessionCommand identifiers: existing service remains the playback authority. */
internal object LiveSongSessionActions {
    const val FAVORITE = "dev.behradhz.meowzix.action.TOGGLE_FAVORITE"
    const val SHUFFLE = "dev.behradhz.meowzix.action.TOGGLE_SHUFFLE"
    const val REPEAT = "dev.behradhz.meowzix.action.CYCLE_REPEAT"
}

internal fun LiveSongAction.toExternalPlaybackAction(): ExternalPlaybackAction = when (this) {
    LiveSongAction.PAUSE -> ExternalPlaybackAction.Pause
    LiveSongAction.PREVIOUS -> ExternalPlaybackAction.Previous
    LiveSongAction.NEXT -> ExternalPlaybackAction.Next
    LiveSongAction.FAVORITE -> ExternalPlaybackAction.Favorite
    LiveSongAction.SHUFFLE -> ExternalPlaybackAction.Shuffle
    LiveSongAction.REPEAT -> ExternalPlaybackAction.Repeat
}

/**
 * An explicit, non-exported PendingIntent receiver. Do not build and immediately
 * disconnect a separate MediaController here; widget and Live Song actions use
 * the same shared dispatcher and, when running, the existing PlaybackService.
 */
class LiveSongControlsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefix = "${context.packageName}.live_song."
        val name = intent?.action?.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix) ?: return
        val action = LiveSongAction.entries.firstOrNull { it.name == name } ?: return
        ExternalPlaybackDispatcher.enqueue(context, action.toExternalPlaybackAction(), goAsync())
    }

    internal companion object {
        fun actionName(context: Context, action: LiveSongAction): String =
            "${context.packageName}.live_song.${action.name}"
    }
}
