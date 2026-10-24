package dev.behradhz.meowzix.feature.telegram

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID

/**
 * Transparent, real Activity for the notification's Forward command.
 *
 * A normal visible Activity transition lets Android dismiss its notification shade. An
 * application overlay launched through a NoDisplay trampoline does not reliably do so.
 * Keeping the window transparent preserves the user's previous app behind the picker;
 * no SYSTEM_ALERT_WINDOW permission or black fullscreen scrim is needed.
 */
@AndroidEntryPoint
class TelegramForwardActivity : ComponentActivity() {
    private var requestVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        setContent {
            MeowzixTheme(darkTheme = true) {
                NotificationForwardPicker()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestVersion++
    }

    @Composable
    private fun NotificationForwardPicker() {
        val viewModel: NowPlayingViewModel = hiltViewModel()
        val playbackState by viewModel.state.collectAsStateWithLifecycle()
        val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
        val visibilityGate = remember { ForwardPickerVisibilityGate() }
        var openRequested by remember(requestVersion) { mutableStateOf(false) }

        LaunchedEffect(requestVersion, playbackState.currentTrack?.id) {
            val pinnedId = intent?.getStringExtra(EXTRA_TRACK_ID)
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val trackId = pinnedId ?: playbackState.currentTrack?.id
            if (trackId != null && !openRequested) {
                openRequested = true
                val snapshot = pinnedId?.let { id ->
                    NowPlayingTrack(
                        id = id,
                        title = intent?.getStringExtra(EXTRA_TITLE)
                            ?.takeIf(String::isNotBlank) ?: "Current track",
                        artist = intent?.getStringExtra(EXTRA_ARTIST),
                        artworkRef = null,
                    )
                }
                viewModel.openForwardPickerForTrack(trackId, fallbackTrack = snapshot)
            }
        }
        LaunchedEffect(forwardState.isOpen) {
            if (visibilityGate.shouldFinish(forwardState.isOpen)) finish()
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val availableHeight = maxHeight
            Box(
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (forwardState.isOpen) viewModel.dismissForwardPicker() else finish()
                },
            )
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
                    .testTag("notification-telegram-forward-sheet")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                val selectedTrack = forwardState.track
                if (forwardState.isOpen && selectedTrack != null) {
                    TelegramForwardSheet(
                        trackId = selectedTrack.id,
                        title = selectedTrack.title,
                        artist = selectedTrack.artist,
                        query = forwardState.query,
                        chats = forwardState.chats,
                        isSearching = forwardState.isSearching,
                        isSending = forwardState.isSending,
                        errorMessage = forwardState.errorMessage,
                        defaults = forwardState.defaults,
                        onQueryChange = viewModel::searchForwardChats,
                        onForward = viewModel::forwardCurrentTrack,
                        onDismiss = viewModel::dismissForwardPicker,
                        floatingOverlay = true,
                        overlayMaxHeight = availableHeight * 0.82f,
                    )
                } else if (!visibilityGate.hasShownPicker) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = Color(0xF21A191A),
                        contentColor = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Preparing Telegram sharing…")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_TRACK_ID = "forward_track_id"
        private const val EXTRA_TITLE = "forward_track_title"
        private const val EXTRA_ARTIST = "forward_track_artist"

        fun intent(context: Context, trackId: String?, title: String?, artist: String?): Intent =
            Intent(context, TelegramForwardActivity::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
    }
}
