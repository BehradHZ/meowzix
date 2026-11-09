package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.behradhz.meowzix.playback.ExternalPlaybackAction
import dev.behradhz.meowzix.playback.ExternalPlaybackDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun WidgetAction.toExternalPlaybackAction(): ExternalPlaybackAction? = when (this) {
    WidgetAction.PLAY_PAUSE -> ExternalPlaybackAction.TogglePlayPause
    WidgetAction.PREVIOUS -> ExternalPlaybackAction.Previous
    WidgetAction.NEXT -> ExternalPlaybackAction.Next
    WidgetAction.FAVORITE -> ExternalPlaybackAction.Favorite
    WidgetAction.FORWARD -> ExternalPlaybackAction.Forward
    WidgetAction.SHUFFLE -> ExternalPlaybackAction.Shuffle
    WidgetAction.REPEAT -> ExternalPlaybackAction.Repeat
    WidgetAction.MENU -> null
}

/** Non-exported launcher action receiver; validates the widget and live queue identity. */
class PlaybackWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CONTROL && intent?.action != ACTION_QUEUE) return
        val widgetId = intent.data?.pathSegments?.firstOrNull()?.toIntOrNull() ?: return
        val validIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java),
        )
        if (widgetId !in validIds) return

        if (intent.action == ACTION_QUEUE) {
            val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
            val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID)?.takeIf(String::isNotBlank)
                ?: return
            if (index < 0) return
            ExternalPlaybackDispatcher.enqueue(
                context,
                ExternalPlaybackAction.QueueSelection(index, mediaId),
                goAsync(),
            )
            return
        }

        val action = WidgetAction.entries.firstOrNull {
            it.name == intent.getStringExtra(EXTRA_ACTION)
        } ?: return
        if (action == WidgetAction.MENU) {
            val pending = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            scope.launch {
                try {
                    PlaybackWidgetSnapshotStore.toggleMenu(context, widgetId)
                    PlaybackWidgetProvider.refreshCached(context, widgetId)
                } finally {
                    pending.finish()
                    scope.cancel()
                }
            }
            return
        }
        val playbackAction = action.toExternalPlaybackAction() ?: return
        ExternalPlaybackDispatcher.enqueue(context, playbackAction, goAsync())
    }

    companion object {
        const val ACTION_CONTROL = "dev.behradhz.meowzix.widget.PLAYBACK_CONTROL"
        const val ACTION_QUEUE = "dev.behradhz.meowzix.widget.PLAYBACK_SELECT"
        const val EXTRA_ACTION = "widget_control"
        const val EXTRA_QUEUE_INDEX = "queue_index"
        const val EXTRA_MEDIA_ID = "queue_media_id"
    }
}
