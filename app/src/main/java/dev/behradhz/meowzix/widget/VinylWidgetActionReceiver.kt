package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dev.behradhz.meowzix.playback.ExternalPlaybackAction
import dev.behradhz.meowzix.playback.ExternalPlaybackDispatcher

internal enum class VinylZone { CENTER, LEFT, RIGHT, TOP, BOTTOM }

internal fun VinylZone.toExternalPlaybackAction(): ExternalPlaybackAction = when (this) {
    VinylZone.CENTER -> ExternalPlaybackAction.TogglePlayPause
    VinylZone.LEFT -> ExternalPlaybackAction.Previous
    VinylZone.RIGHT -> ExternalPlaybackAction.Next
    VinylZone.TOP -> ExternalPlaybackAction.Favorite
    VinylZone.BOTTOM -> ExternalPlaybackAction.Forward
}

/** Explicit, non-exported receiver; gesture zones are invisible but independently clickable. */
class VinylWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TAP) return
        val zone = VinylZone.entries.firstOrNull {
            it.name == intent.getStringExtra(EXTRA_ZONE)
        } ?: return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val validIds = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, VinylWidgetProvider::class.java),
        )
        if (id !in validIds) return

        val doubleTap = detector.register(id, zone.name, SystemClock.elapsedRealtime())
        if (zone != VinylZone.CENTER && !doubleTap) return

        ExternalPlaybackDispatcher.enqueue(context, zone.toExternalPlaybackAction(), goAsync())
    }

    companion object {
        const val ACTION_TAP = "dev.behradhz.meowzix.widget.VINYL_TAP"
        const val EXTRA_ZONE = "vinyl_zone"
        private val detector = VinylTapDetector()
    }
}
