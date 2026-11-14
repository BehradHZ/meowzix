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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.queue.QueueViewModel
import dev.behradhz.meowzix.ui.components.TrackArtwork
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

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

@Composable
private fun LockScreenPlayerContent(
    onClose: () -> Unit,
    queueViewModel: QueueViewModel = hiltViewModel(),
) {
    val queue by queueViewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val start = queue.currentIndex.coerceAtLeast(0).coerceAtMost(queue.items.size)
    val remaining = queue.items.drop(start)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
        ) {
            item(key = "player") {
                Box(Modifier.fillMaxWidth().height(pageHeight)) {
                    NowPlayingRoute(
                        lockScreenMode = true,
                        onBack = onClose,
                        onOpenQueue = { scope.launch { scrollState.animateScrollToItem(1) } },
                    )
                }
            }
            item(key = "queue_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Up next", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("${remaining.size} tracks", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(
                items = remaining,
                key = { _, item -> item.occurrenceId.toString() },
            ) { offset, item ->
                val index = start + offset
                val current = index == queue.currentIndex
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { queueViewModel.play(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork(item.artworkRef, item.title, size = 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                            Text(item.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (current) {
                            Text("Playing", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item(key = "queue_bottom") {
                Spacer(Modifier.height(28.dp).navigationBarsPadding())
            }
        }
    }
}
