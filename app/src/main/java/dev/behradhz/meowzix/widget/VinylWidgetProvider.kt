package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.os.BundleCompat
import android.util.SizeF
import android.util.TypedValue
import kotlin.math.min

/** Always fit a centered circle within the actual widget bounds, including 1x1 and 3x4. */
internal fun vinylDiameter(widthDp: Float, heightDp: Float): Float =
    (min(widthDp, heightDp) - 8f).coerceAtLeast(32f)
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
        val saved = PlaybackWidgetSnapshotStore.load(context)
        ids.forEach { id ->
            manager.updateAppWidget(id, responsiveViews(context, id, saved, VinylRecordArtwork.snapshotFrame()))
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
                controller = withTimeout(6_000L) { connecting.await() }
                val state = PlaybackWidgetState.fromPlayer(controller)
                updateAll(context, state.copy(
                    isFavorite = saved.mediaId == state.mediaId && saved.isFavorite,
                ))
            } catch (_: Exception) {
                // Keep the last usable record instead of blanking the widget.
            } finally {
                if (controller != null) controller.release() else future?.let(MediaController::releaseFuture)
                pending.finish()
                scope.cancel()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
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
            PlaybackWidgetSnapshotStore.save(context, state)
            latestState = state
            val manager = AppWidgetManager.getInstance(context)
            widgetIds.forEach { id ->
                manager.updateAppWidget(id, responsiveViews(context, id, state, record))
            }
        }

        fun advanceRotation(context: Context) {
            val widgetIds = ids(context)
            if (widgetIds.isEmpty()) return
            val frame = VinylRecordArtwork.nextFrame() ?: return
            val state = latestState
            val manager = AppWidgetManager.getInstance(context)
            // Full updates release previously cached bitmaps on Android 17+.
            widgetIds.forEach { id -> manager.updateAppWidget(id, responsiveViews(context, id, state, frame)) }
        }

        /**
         * Android 12+ lets the launcher choose the correct layout for its exact cells.
         * A 3x4 host therefore contains a centered 3x3 disc, not a stretched oval.
         */
        private fun responsiveViews(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            record: Bitmap?,
        ): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
                val sizes = BundleCompat.getParcelableArrayList(
                    options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java,
                )?.filter { it.width > 0f && it.height > 0f }?.distinct()?.take(8)
                val viewsBySize = if (!sizes.isNullOrEmpty()) {
                    sizes.associateWith { size ->
                        views(context, widgetId, state, record, vinylDiameter(size.width, size.height))
                    }
                } else mapOf(
                    SizeF(40f, 40f) to views(context, widgetId, state, record, 32f),
                    SizeF(104f, 104f) to views(context, widgetId, state, record, 96f),
                    SizeF(176f, 176f) to views(context, widgetId, state, record, 168f),
                    SizeF(252f, 252f) to views(context, widgetId, state, record, 244f),
                )
                return RemoteViews(viewsBySize)
            }
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val side = vinylDiameter(width.toFloat(), height.toFloat())
            return views(context, widgetId, state, record, side)
        }

        private fun views(
            context: Context,
            widgetId: Int,
            state: PlaybackWidgetState,
            record: Bitmap?,
            discSizeDp: Float,
        ) = RemoteViews(context.packageName, R.layout.widget_vinyl_disc_only).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setViewLayoutWidth(R.id.vinyl_widget_disc_frame, discSizeDp, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.vinyl_widget_disc_frame, discSizeDp, TypedValue.COMPLEX_UNIT_DIP)
            }
            // Do not bind a click to the root or disc: some launchers route every
            // child touch to the root activity PendingIntent, swallowing controls.
            // The nine transparent cells in widget_vinyl.xml own every hit region.
            val open = openPlayer(context)
            setOnClickPendingIntent(R.id.vinyl_widget_open_top_left, open)
            setOnClickPendingIntent(R.id.vinyl_widget_open_top_right, open)
            setOnClickPendingIntent(R.id.vinyl_widget_open_bottom_left, open)
            setOnClickPendingIntent(R.id.vinyl_widget_open_bottom_right, open)
            setOnClickPendingIntent(R.id.vinyl_widget_center, tap(context, widgetId, VinylZone.CENTER))
            setOnClickPendingIntent(R.id.vinyl_widget_left, tap(context, widgetId, VinylZone.LEFT))
            setOnClickPendingIntent(R.id.vinyl_widget_right, tap(context, widgetId, VinylZone.RIGHT))
            setOnClickPendingIntent(R.id.vinyl_widget_top, tap(context, widgetId, VinylZone.TOP))
            setOnClickPendingIntent(R.id.vinyl_widget_bottom, tap(context, widgetId, VinylZone.BOTTOM))
            if (record != null) setImageViewBitmap(R.id.vinyl_widget_disc, record)
            else setImageViewResource(R.id.vinyl_widget_disc, R.drawable.widget_vinyl_placeholder)
            setContentDescription(
                R.id.vinyl_widget_center,
                if (state.isPlaying) "Pause" else "Play",
            )
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
