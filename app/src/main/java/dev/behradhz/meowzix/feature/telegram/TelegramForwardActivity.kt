package dev.behradhz.meowzix.feature.telegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.theme.MeowzixTheme

/**
 * Glass Telegram picker reached from the phone media notification.
 *
 * Playback may connect after this activity's first composition. The picker must be observed
 * OPEN before a later CLOSED state is allowed to finish the activity: its initial state is
 * CLOSED, and finishing on that first emission made the notification action appear inert.
 */
@AndroidEntryPoint
class TelegramForwardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: NowPlayingViewModel = hiltViewModel()
            val playbackState by viewModel.state.collectAsStateWithLifecycle()
            val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
            var openRequested by remember { mutableStateOf(false) }
            val visibilityGate = remember { ForwardPickerVisibilityGate() }

            // Use the actual track ID from the connected controller, not a possibly stale
            // state.value read during the first frame. The ViewModel pins its identity on open.
            LaunchedEffect(playbackState.currentTrack?.id) {
                val trackId = playbackState.currentTrack?.id
                if (trackId != null && !openRequested) {
                    openRequested = true
                    viewModel.openForwardPickerForTrack(trackId)
                }
            }

            LaunchedEffect(forwardState.isOpen) {
                if (visibilityGate.shouldFinish(forwardState.isOpen)) finish()
            }

            MeowzixTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val selectedTrack = forwardState.track
                    if (forwardState.isOpen && selectedTrack != null) {
                        TelegramForwardSheet(
                            track = selectedTrack,
                            query = forwardState.query,
                            chats = forwardState.chats,
                            isSearching = forwardState.isSearching,
                            isSending = forwardState.isSending,
                            errorMessage = forwardState.errorMessage,
                            defaults = forwardState.defaults,
                            onQueryChange = viewModel::searchForwardChats,
                            onForward = viewModel::forwardCurrentTrack,
                            onDismiss = viewModel::dismissForwardPicker,
                        )
                    } else if (!visibilityGate.hasShownPicker) {
                        // No blank flash while the media controller and chat picker initialize.
                        Surface(
                            modifier = Modifier
                                .padding(24.dp)
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(24.dp),
                                ),
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xED171716),
                            contentColor = Color.White,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(3.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("Preparing Telegram sharing…")
                            }
                        }
                    }
                }
            }
        }
    }
}
