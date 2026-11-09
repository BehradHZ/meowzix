package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * Compatibility adapter for Android 8–11. Android 12+ receives the same queue
 * directly as RemoteCollectionItems, avoiding IPC per scroll row.
 */
class PlaybackWidgetQueueService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        QueueFactory(applicationContext)

    private class QueueFactory(private val context: Context) : RemoteViewsFactory {
        private var state = PlaybackWidgetState.Empty
        private var rows: List<WidgetQueueTrack> = emptyList()

        override fun onCreate() = onDataSetChanged()

        override fun onDataSetChanged() {
            state = PlaybackWidgetSnapshotStore.load(context)
            rows = state.queue
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount() = rows.size

        override fun getViewAt(position: Int): RemoteViews? =
            rows.getOrNull(position)?.let { PlaybackWidgetProvider.queueRow(context, state, it) }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int): Long = rows.getOrNull(position)?.index?.toLong() ?: position.toLong()
        override fun hasStableIds() = true
    }
}
