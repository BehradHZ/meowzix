package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import dev.behradhz.meowzix.MainActivity
import dev.behradhz.meowzix.R

class PlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_playback).apply {
            setOnClickPendingIntent(R.id.widget_logo, openAppIntent(context))
            setOnClickPendingIntent(
                R.id.widget_previous,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS, REQUEST_PREVIOUS),
            )
            setOnClickPendingIntent(
                R.id.widget_play_pause,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, REQUEST_PLAY_PAUSE),
            )
            setOnClickPendingIntent(
                R.id.widget_next,
                mediaButtonIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT, REQUEST_NEXT),
            )
        }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Route widget transport buttons through Media3's receiver. The receiver resolves the existing
     * MediaSessionService (PlaybackService), so the widget, notification, hardware keys and in-app
     * controls all operate the exact same player timeline and queue.
     */
    @OptIn(UnstableApi::class)
    private fun mediaButtonIntent(context: Context, keyCode: Int, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            component = ComponentName(context, MediaButtonReceiver::class.java)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_OPEN_APP = 4100
        const val REQUEST_PREVIOUS = 4101
        const val REQUEST_PLAY_PAUSE = 4102
        const val REQUEST_NEXT = 4103
    }
}
