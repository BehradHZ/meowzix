package dev.behradhz.meowzix.feature.telegram

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * Notification action hand-off without ever rendering Meowzix's main UI.
 *
 * An actual (NoDisplay) Activity launch lets Android dismiss the notification shade through
 * its normal system-managed activity transition. Do not use restricted CLOSE_SYSTEM_DIALOGS,
 * status-bar reflection or an Accessibility service to force the shade closed.
 */
class TelegramForwardLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trackId = intent.getStringExtra(EXTRA_TRACK_ID)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val artist = intent.getStringExtra(EXTRA_ARTIST)
        if (Settings.canDrawOverlays(this)) {
            runCatching {
                startService(TelegramForwardOverlayService.intent(this, trackId, title, artist))
            }.onFailure { error ->
                Log.w(TAG, "Unable to open the floating Telegram picker", error)
                launchPermissionGateway(trackId, title, artist)
            }
        } else {
            // One-time explicit permission is the only case in which we present a visible page.
            launchPermissionGateway(trackId, title, artist)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    private fun launchPermissionGateway(trackId: String?, title: String?, artist: String?) {
        startActivity(
            TelegramForwardActivity.intent(this, trackId, title, artist).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            },
        )
    }

    companion object {
        private const val TAG = "MeowzixForward"
        private const val EXTRA_TRACK_ID = "forward_track_id"
        private const val EXTRA_TITLE = "forward_track_title"
        private const val EXTRA_ARTIST = "forward_track_artist"

        fun intent(context: Context, trackId: String?, title: String?, artist: String?): Intent =
            Intent(context, TelegramForwardLaunchActivity::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
    }
}
