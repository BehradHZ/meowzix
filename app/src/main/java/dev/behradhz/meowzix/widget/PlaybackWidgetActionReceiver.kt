package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.behradhz.meowzix.playback.LiveSongSessionActions
import dev.behradhz.meowzix.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Non-exported: widget clicks cannot be used as an external playback-control API. */
@OptIn(markerClass = [UnstableApi::class])
class PlaybackWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CONTROL && intent?.action != ACTION_QUEUE) return
        val widgetId = intent.data?.pathSegments?.firstOrNull()?.toIntOrNull() ?: return
        val validIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, PlaybackWidgetProvider::class.java),
        )
        if (widgetId !in validIds) return

        val action = if (intent.action == ACTION_CONTROL) {
            WidgetAction.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_ACTION) }
                ?: return
        } else null
        val result = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var controller: MediaController? = null
            var future: ListenableFuture<MediaController>? = null
            try {
                if (action == WidgetAction.MENU) {
                    PlaybackWidgetSnapshotStore.toggleMenu(context, widgetId)
                    PlaybackWidgetProvider.refreshCached(context, widgetId)
                    return@launch
                }
                val connecting = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                ).buildAsync()
                future = connecting
                controller = withTimeout(6_000L) { connecting.await() }
                if (action == null) {
                    val index = intent.getIntExtra(EXTRA_QUEUE_INDEX, -1)
                    val mediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: return@launch
                    if (index !in 0 until controller.mediaItemCount ||
                        !controller.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM) ||
                        !controller.isCommandAvailable(Player.COMMAND_GET_TIMELINE) ||
                        controller.getMediaItemAt(index).mediaId != mediaId
                    ) return@launch
                    controller.seekTo(index, 0L)
                    if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) controller.play()
                    return@launch
                }
                when (action) {
                    WidgetAction.PLAY_PAUSE ->
                        if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                    WidgetAction.PREVIOUS ->
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                            controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                    WidgetAction.NEXT ->
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                            controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                    WidgetAction.FAVORITE ->
                        if (controller.currentMediaItem != null)
                            controller.widgetCommand(LiveSongSessionActions.FAVORITE)
                    WidgetAction.FORWARD ->
                        if (controller.currentMediaItem != null)
                            controller.widgetCommand(ACTION_FORWARD)
                    WidgetAction.SHUFFLE ->
                        if (controller.currentMediaItem != null)
                            controller.widgetCommand(LiveSongSessionActions.SHUFFLE)
                    WidgetAction.REPEAT ->
                        if (controller.currentMediaItem != null)
                            controller.widgetCommand(LiveSongSessionActions.REPEAT)
                    WidgetAction.MENU -> Unit
                }
            } catch (_: Exception) {
                // Unavailable playback service, removed queue item, or timed out connection.
            } finally {
                if (controller != null) controller.release() else future?.let(MediaController::releaseFuture)
                result.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_CONTROL = "dev.behradhz.meowzix.widget.PLAYBACK_CONTROL"
        const val ACTION_QUEUE = "dev.behradhz.meowzix.widget.PLAYBACK_SELECT"
        const val EXTRA_ACTION = "widget_control"
        const val EXTRA_QUEUE_INDEX = "queue_index"
        const val EXTRA_MEDIA_ID = "queue_media_id"
        private const val ACTION_FORWARD = "dev.behradhz.meowzix.action.OPEN_TELEGRAM_FORWARD"
    }
}

private suspend fun MediaController.widgetCommand(action: String) {
    val command = SessionCommand(action, Bundle.EMPTY)
    if (isSessionCommandAvailable(command)) {
        withTimeout(5_000L) { sendCustomCommand(command, Bundle.EMPTY).await() }
    }
}
