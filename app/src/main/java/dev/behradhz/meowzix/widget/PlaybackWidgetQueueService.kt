package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.ProgressiveQueueSnapshot
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * The home-screen collection reads the same logical queue snapshot as QueueRoute,
 * not the Media3 materialized playback window. Android requests only visible rows,
 * so even a large library does not inflate a 10k-item RemoteViews parcel.
 */
@AndroidEntryPoint
class PlaybackWidgetQueueService : RemoteViewsService() {
    @Inject lateinit var progressiveQueue: ProgressiveQueue

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        QueueFactory(applicationContext, progressiveQueue)

    private class QueueFactory(
        private val context: Context,
        private val progressiveQueue: ProgressiveQueue,
    ) : RemoteViewsFactory {
        private var snapshot: ProgressiveQueueSnapshot? = null
        private var fallbackState = PlaybackWidgetState.Empty
        private var palette = WidgetPalette(
            android.graphics.Color.BLACK,
            android.graphics.Color.WHITE,
            android.graphics.Color.LTGRAY,
            android.graphics.Color.WHITE,
        )

        override fun onCreate() = onDataSetChanged()

        override fun onDataSetChanged() {
            snapshot = progressiveQueue.snapshot()
            fallbackState = PlaybackWidgetSnapshotStore.load(context)
            palette = runBlocking { widgetPalette(context) }
        }

        override fun onDestroy() {
            snapshot = null
            fallbackState = PlaybackWidgetState.Empty
        }

        override fun getCount(): Int = snapshot?.let {
            (it.tracks.size - it.currentIndex.coerceAtLeast(0)).coerceAtLeast(0)
        } ?: fallbackState.queue.count { it.index >= fallbackState.queueIndex.coerceAtLeast(0) }

        private fun rowAt(position: Int): WidgetQueueTrack? {
            val active = snapshot
            if (active != null) {
                val index = active.currentIndex.coerceAtLeast(0) + position
                val track = active.tracks.getOrNull(index) ?: return null
                return WidgetQueueTrack(
                    index = index,
                    mediaId = track.id.toString(),
                    title = track.title,
                    artist = track.artist,
                    occurrenceId = active.occurrenceIds.getOrNull(index)?.toString(),
                    origin = active.origins.getOrNull(index)?.name ?: QueueItemOrigin.GENERATED.name,
                )
            }
            return fallbackState.queue.filter {
                it.index >= fallbackState.queueIndex.coerceAtLeast(0)
            }.getOrNull(position)
        }

        override fun getViewAt(position: Int): RemoteViews? =
            rowAt(position)?.let { item ->
                val currentIndex = snapshot?.currentIndex ?: fallbackState.queueIndex
                PlaybackWidgetProvider.queueRow(
                    context, currentIndex, item, palette,
                )
            }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int): Long =
            rowAt(position)?.occurrenceId?.hashCode()?.toLong()
                ?: rowAt(position)?.index?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
    }
}
