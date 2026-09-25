package dev.behradhz.meowzix

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.core.performance.PerformanceMetrics
import dev.behradhz.meowzix.navigation.MeowzixApp
import dev.behradhz.meowzix.ui.haptics.meowzixInteractionHaptics
import dev.behradhz.meowzix.ui.theme.MeowzixTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openNowPlaying by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openNowPlaying = intent?.action == ACTION_OPEN_NOW_PLAYING
        enableEdgeToEdge()
        setContent {
            MeowzixTheme {
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
