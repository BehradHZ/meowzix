package dev.behradhz.meowzix.feature.telegram

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout

/**
 * Opaque, real Activity transition for a MediaSession custom notification action.
 *
 * On Android 13+ the media Forward slot sends a command to PlaybackService, NOT an
 * activity PendingIntent from SystemUI. Starting the translucent picker directly
 * can leave Quick Settings/notification shade above it. First move a regular opaque
 * Activity to the foreground. Once its window has focus, hand off to the existing
 * translucent picker and finish this gateway so the previous app can show behind it.
 *
 * No hidden status-bar API, ACTION_CLOSE_SYSTEM_DIALOGS, overlay permission, or delay.
 * OEM SystemUI still owns whether the shade is collapsed.
 */
class NotificationForwardGatewayActivity : Activity() {
    private var resumed = false
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // An actual opaque first frame matters: NoDisplay/translucent gateways are not
        // considered foreground app transitions by all SystemUI implementations.
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(21, 20, 19))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (window.decorView.hasWindowFocus()) handOffWhenForeground()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handedOff = false
        if (resumed && window.decorView.hasWindowFocus()) handOffWhenForeground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) handOffWhenForeground()
    }

    private fun handOffWhenForeground() {
        if (!resumed || handedOff || isFinishing || !window.decorView.hasWindowFocus()) return
        // Post out of the focus callback so window/shade transition completes before
        // the translucent destination is introduced.
        window.decorView.post {
            if (!resumed || handedOff || isFinishing || !window.decorView.hasWindowFocus()) return@post
            handedOff = true
            startActivity(
                TelegramForwardActivity.intent(
                    this,
                    intent.getStringExtra(EXTRA_TRACK_ID),
                    intent.getStringExtra(EXTRA_TITLE),
                    intent.getStringExtra(EXTRA_ARTIST),
                ).apply {
                    // Reuse the existing picker on rapid/repeated Forward taps rather
                    // than stacking older selected-track sheets in the same task.
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                },
            )
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val EXTRA_TRACK_ID = "forward_track_id"
        private const val EXTRA_TITLE = "forward_track_title"
        private const val EXTRA_ARTIST = "forward_track_artist"

        fun intent(context: Context, trackId: String?, title: String?, artist: String?): Intent =
            Intent(context, NotificationForwardGatewayActivity::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
    }
}
