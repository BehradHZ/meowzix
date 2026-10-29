package dev.behradhz.meowzix.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Shared SessionCommand identifiers: existing service remains the playback authority. */
internal object LiveSongSessionActions {
    const val FAVORITE = "dev.behradhz.meowzix.action.TOGGLE_FAVORITE"
    const val SHUFFLE = "dev.behradhz.meowzix.action.TOGGLE_SHUFFLE"
    const val REPEAT = "dev.behradhz.meowzix.action.CYCLE_REPEAT"
}

/**
 * The receiver is non-exported and handles only explicit immutable PendingIntents.
 * Use the existing Media3 session, not an exposed custom action on the playback service.
 */
@UnstableApi
class LiveSongControlsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val requested = intent?.action?.removePrefix("${context.packageName}.live_song.") ?: return
        val action = LiveSongAction.entries.firstOrNull { it.name == requested } ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            var controller: MediaController? = null
            try {
                controller = withTimeout(5_000L) { future.await() }
                val player = controller
                // Ignore stale action clicks after the original playback has stopped.
                if (player.currentMediaItem != null && player.playWhenReady) {
                    when (action) {
                        LiveSongAction.PAUSE ->
                            if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.pause()
                        LiveSongAction.PREVIOUS ->
                            if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) &&
                                player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                        LiveSongAction.NEXT ->
                            if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) &&
                                player.hasNextMediaItem()) player.seekToNextMediaItem()
                        LiveSongAction.FAVORITE -> player.liveSongCustomCommand(LiveSongSessionActions.FAVORITE)
                        LiveSongAction.SHUFFLE -> player.liveSongCustomCommand(LiveSongSessionActions.SHUFFLE)
                        LiveSongAction.REPEAT -> player.liveSongCustomCommand(LiveSongSessionActions.REPEAT)
                    }
                }
            } catch (_: Exception) {
                // No reachable session; do not start a second player or crash SystemUI.
            } finally {
                if (controller != null) controller.release() else MediaController.releaseFuture(future)
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    internal companion object {
        fun actionName(context: Context, action: LiveSongAction): String =
            "${context.packageName}.live_song.${action.name}"
    }
}

private fun MediaController.liveSongCustomCommand(name: String) {
    val command = SessionCommand(name, Bundle.EMPTY)
    if (availableSessionCommands.contains(command)) sendCustomCommand(command, Bundle.EMPTY)
}
