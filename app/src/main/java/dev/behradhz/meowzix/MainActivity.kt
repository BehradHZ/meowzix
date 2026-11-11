package dev.behradhz.meowzix

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.core.performance.PerformanceMetrics
import dev.behradhz.meowzix.navigation.MeowzixApp
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import javax.inject.Inject
import dev.behradhz.meowzix.ui.haptics.meowzixInteractionHaptics
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.behradhz.meowzix.ui.theme.LauncherIconManager
import dev.behradhz.meowzix.ui.theme.selectLauncherIconForSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    private var openNowPlaying by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNowPlaying = intent?.action == ACTION_OPEN_NOW_PLAYING
        enableEdgeToEdge()
        setContent {
            // Do not publish a temporary default icon before saved settings have loaded.
            val appearanceFlow = remember {
                settingsRepository.appearanceSettings.map<AppearanceSettings, AppearanceSettings?> { it }
            }
            val savedAppearance by appearanceFlow.collectAsStateWithLifecycle(initialValue = null)
            val appearance = savedAppearance ?: AppearanceSettings()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (appearance.theme) {
                ThemePreference.SYSTEM -> systemDark
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            MeowzixTheme(darkTheme = darkTheme, colorTheme = appearance.colorTheme, customColorSeed = appearance.customColorSeed) {
                // Match status and navigation icon contrast to the current app appearance.
                val lightSystemBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
                SideEffect {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightSystemBars
                        isAppearanceLightNavigationBars = lightSystemBars
                    }
                }
                val wallpaperPrimary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    dynamicLightColorScheme(this@MainActivity).primary.toArgb()
                } else MaterialTheme.colorScheme.primary.toArgb()
                LaunchedEffect(Unit) {
                    // Wait until Compose reaches a rendered frame instead of treating onCreate as
                    // startup completion. Macrobenchmark can then correlate this with fully-drawn.
                    withFrameNanos { }
                    PerformanceMetrics.markFirstFrame()
                    reportFullyDrawn()
                    if (BuildConfig.DEBUG) Log.d(PERF_TAG, PerformanceMetrics.report())
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .meowzixInteractionHaptics(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    MeowzixApp(
                        openNowPlayingRequest = openNowPlaying,
                        onNowPlayingRequestConsumed = { openNowPlaying = false },
                        onApplyLauncherIcon = { source ->
                            // Called only from the explicit Apply icon confirmation.
                            lifecycleScope.launch {
                                try {
                                    val variant = selectLauncherIconForSource(
                                        source, appearance.colorTheme, appearance.customColorSeed,
                                        false, wallpaperPrimary,
                                    )
                                    withContext(Dispatchers.IO) {
                                        LauncherIconManager.apply(applicationContext, variant)
                                    }
                                    // The alias update uses DONT_KILL_APP; do not restart the task.
                                    settingsRepository.setLauncherIconSource(source)
                                } catch (error: Exception) {
                                    Log.e("MeowzixLauncher", "Unable to apply launcher icon", error)
                                    Toast.makeText(
                                        this@MainActivity, "Launcher icon could not be updated",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_NOW_PLAYING) openNowPlaying = true
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "dev.behradhz.meowzix.OPEN_NOW_PLAYING"
        private const val PERF_TAG = "MeowzixPerf"
    }
}
