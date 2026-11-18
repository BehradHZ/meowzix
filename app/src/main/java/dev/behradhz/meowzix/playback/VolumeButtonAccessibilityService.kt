package dev.behradhz.meowzix.playback

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class VolumeButtonAccessibilityService : AccessibilityService() {
    @Inject lateinit var settings: SettingsRepository

    private val audio by lazy { requireNotNull(getSystemService(AudioManager::class.java)) }
    private val handler = Handler(Looper.getMainLooper())
    private val holds = mutableMapOf<Pair<Int, Int>, Hold>()
    private var preferenceJob: Job? = null
    private var enabled = false

    override fun onServiceConnected() {
        preferenceJob?.cancel()
        preferenceJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            settings.playbackPreferenceSettings
                .map { it.volumeButtonTrackNavigationEnabled }
                .distinctUntilChanged()
                .collect { wanted ->
                    enabled = wanted
                    if (!wanted) clearHolds()
                    val info = serviceInfo ?: return@collect
                    val flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    info.flags = if (wanted) info.flags or flag else info.flags and flag.inv()
                    serviceInfo = info
                }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val next = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (!next && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val key = event.deviceId to event.keyCode

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !event.isCanceled) {
            holds.remove(key)?.let { handler.removeCallbacks(it.timeout) }
            if (!enabled || audio.mode != AudioManager.MODE_NORMAL) return false
            val playback = PlaybackServiceBridge.activeVolumeKeyPlayback ?: return false
            val hold = Hold(event.downTime, playback, next)
            holds[key] = hold
            handler.postAtTime(hold.timeout, event.downTime + HOLD_MS)
            return true
        }

        val hold = holds[key]?.takeIf { it.downTime == event.downTime } ?: return false
        if (event.isCanceled || event.action == KeyEvent.ACTION_UP) {
            holds.remove(key)
            handler.removeCallbacks(hold.timeout)
            if (!event.isCanceled && enabled && audio.mode == AudioManager.MODE_NORMAL && !hold.handled) {
                if (event.eventTime - event.downTime >= HOLD_MS) {
                    hold.timeout.run()
                } else {
                    try {
                        audio.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (next) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI,
                        )
                    } catch (_: SecurityException) {
                        // Android can reject volume changes that would toggle Do Not Disturb.
                    }
                }
            }
        }
        return true
    }

    private inner class Hold(val downTime: Long, playback: Any, next: Boolean) {
        var handled = false
        val timeout = Runnable {
            if (!handled) {
                handled = true
                if (enabled && audio.mode == AudioManager.MODE_NORMAL) {
                    PlaybackServiceBridge.skipWithVolumeKey(playback, next)
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        for (hold in holds.values) {
            hold.handled = true
            handler.removeCallbacks(hold.timeout)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        enabled = false
        preferenceJob?.cancel()
        preferenceJob = null
        clearHolds()
    }

    private fun clearHolds() {
        for (hold in holds.values) handler.removeCallbacks(hold.timeout)
        holds.clear()
    }

    companion object {
        private const val HOLD_MS = 500L

        fun hasAccess(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val component = ComponentName(context, VolumeButtonAccessibilityService::class.java)
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo.serviceInfo
                    ComponentName(service.packageName, service.name) == component
                }
        }
    }
}
