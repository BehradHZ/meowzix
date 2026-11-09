package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
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

internal enum class VinylZone { CENTER, LEFT, RIGHT, TOP, BOTTOM }

/** Explicit, non-exported receiver: no separate player or direct database writes. */
@OptIn(markerClass = [UnstableApi::class])
class VinylWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TAP) return
        val zone = VinylZone.entries.firstOrNull {
            it.name == intent.getStringExtra(EXTRA_ZONE)
        } ?: return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (id < 0 || !VinylWidgetProvider.hasWidgets(context)) return

        val doubleTap = detector.register(id, zone.name, SystemClock.elapsedRealtime())
        if (zone != VinylZone.CENTER && !doubleTap) return

        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var controller: MediaController? = null
            var future: ListenableFuture<MediaController>? = null
            try {
                val connecting = MediaController.Builder(
                    context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                ).buildAsync()
                future = connecting
                controller = withTimeout(5_000L) { connecting.await() }
                when (zone) {
                    VinylZone.CENTER -> if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) {
                        if (controller.isPlaying) controller.pause() else controller.play()
                    }
                    VinylZone.LEFT ->
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                            controller.hasPreviousMediaItem()) controller.seekToPreviousMediaItem()
                    VinylZone.RIGHT ->
                        if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                            controller.hasNextMediaItem()) controller.seekToNextMediaItem()
                    VinylZone.TOP ->
                        if (controller.currentMediaItem != null)
                            controller.dispatchVinylCommand(LiveSongSessionActions.FAVORITE)
                    VinylZone.BOTTOM ->
                        if (controller.currentMediaItem != null)
                            controller.dispatchVinylCommand(ACTION_FORWARD)
                }
            } catch (_: Exception) {
                // Session unavailable: no phantom playback commands.
            } finally {
                if (controller != null) controller.release() else future?.let(MediaController::releaseFuture)
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "dev.behradhz.meowzix.widget.VINYL_TAP"
        const val EXTRA_ZONE = "vinyl_zone"
        private const val ACTION_FORWARD = "dev.behradhz.meowzix.action.OPEN_TELEGRAM_FORWARD"
        private val detector = VinylTapDetector()
    }
}

private suspend fun MediaController.dispatchVinylCommand(action: String) {
    val command = SessionCommand(action, Bundle.EMPTY)
    if (isSessionCommandAvailable(command)) {
        withTimeout(5_000L) { sendCustomCommand(command, Bundle.EMPTY).await() }
    }
}
