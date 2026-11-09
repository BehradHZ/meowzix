package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(markerClass = [UnstableApi::class])
class VinylWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            manager.updateAppWidget(id, views(context, id, PlaybackWidgetState.Empty, null))
        }
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
                updateAll(context, PlaybackWidgetState.fromPlayer(controller))
            } catch (_: Exception) {
                // A disconnected session leaves a safe open-player fallback.
            } finally {
                if (controller != null) controller.release() else future?.let(MediaController::releaseFuture)
                pending.finish()
                scope.cancel()
            }
        }
    }

    companion object {
        @Volatile private var latestState = PlaybackWidgetState.Empty

        private fun ids(context: Context) = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, VinylWidgetProvider::class.java))

        fun hasWidgets(context: Context) = ids(context).isNotEmpty()

        suspend fun updateAll(context: Context, state: PlaybackWidgetState) {
            val widgetIds = ids(context)
            if (widgetIds.isEmpty()) return
            val record = VinylRecordArtwork.prepare(context, state.artworkRef)
            latestState = state
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.forEach { id ->
                manager.updateAppWidget(id, views(context, id, state, record))
            }
        }

        fun advanceRotation(context: Context) {
            val widgetIds = ids(context)
            if (widgetIds.isEmpty()) return
            val frame = VinylRecordArtwork.nextFrame() ?: return
            val state = latestState
            val manager = AppWidgetManager.getInstance(context)
            // Full updates release previously cached bitmaps on Android 17+.
            widgetIds.forEach { id -> manager.updateAppWidget(id, views(context, id, state, frame)) }
        }

        private fun views(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            record: Bitmap?,
        ) = RemoteViews(context.packageName, R.layout.widget_vinyl).apply {
            setOnClickPendingIntent(R.id.vinyl_widget_root, openPlayer(context))
            setOnClickPendingIntent(R.id.vinyl_widget_center, tap(context, widgetId, VinylZone.CENTER))
            setOnClickPendingIntent(R.id.vinyl_widget_left, tap(context, widgetId, VinylZone.LEFT))
            setOnClickPendingIntent(R.id.vinyl_widget_right, tap(context, widgetId, VinylZone.RIGHT))
            setOnClickPendingIntent(R.id.vinyl_widget_top, tap(context, widgetId, VinylZone.TOP))
            setOnClickPendingIntent(R.id.vinyl_widget_bottom, tap(context, widgetId, VinylZone.BOTTOM))
            if (record != null) setImageViewBitmap(R.id.vinyl_widget_disc, record)
            else setImageViewResource(R.id.vinyl_widget_disc, R.drawable.meowzix_logo)
            setImageViewResource(
                R.id.vinyl_widget_center,
                if (state.isPlaying) R.drawable.widget_pause else R.drawable.widget_play,
            )
            setContentDescription(R.id.vinyl_widget_center, if (state.isPlaying) "Pause" else "Play")
            setContentDescription(
                R.id.vinyl_widget_disc,
                state.title?.let { "$it vinyl record" } ?: "Meowzix vinyl record",
            )
            setContentDescription(
                R.id.vinyl_widget_top,
                if (state.isFavorite) "Double tap to unlike" else "Double tap to like",
            )
        }

        private fun openPlayer(context: Context) = PendingIntent.getActivity(
            context, 5200,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_NOW_PLAYING
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun tap(context: Context, id: Int, zone: VinylZone) = PendingIntent.getBroadcast(
            context, 5210 + zone.ordinal,
            Intent(context, VinylWidgetActionReceiver::class.java).apply {
                action = VinylWidgetActionReceiver.ACTION_TAP
                data = Uri.parse("meowzix-widget://tap/$id/${zone.name}")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(VinylWidgetActionReceiver.EXTRA_ZONE, zone.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
