package dev.behradhz.meowzix

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import javax.inject.Inject

/**
 * User-opted-in player that occludes the secure keyguard without unlocking it.
 * Uses the existing NowPlayingRoute and QueueRepository; never creates another player.
 */
@AndroidEntryPoint
class LockScreenPlayerActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    private val keyguard by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_SCREEN_OFF) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()
        setContent {
            val appearance by settingsRepository.appearanceSettings
                .collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            val darkTheme = when (appearance.theme) {
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            MeowzixTheme(
                darkTheme = darkTheme,
                colorTheme = appearance.colorTheme,
                customColorSeed = appearance.customColorSeed,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LockScreenPlayerContent(onClose = ::finish)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    override fun onResume() {
        super.onResume()
        if (!keyguard.isKeyguardLocked) finish()
    }

    override fun onStop() {
        unregisterReceiver(screenReceiver)
        super.onStop()
        // Do not leave a stale lock-screen Activity on top when the display goes dark.
        if (!(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) finish()
    }
}

/**
 * The lock screen and main player use one identical morphing player and one Queue
 * interaction surface. No duplicate scroll list or diverging swipe behavior.
 */
@Composable
private fun LockScreenPlayerContent(onClose: () -> Unit) {
    NowPlayingRoute(
        lockScreenMode = true,
        onBack = onClose,
        onOpenQueue = {},
    )
}
