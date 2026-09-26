package dev.behradhz.meowzix.feature.telegram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingViewModel
import dev.behradhz.meowzix.ui.theme.MeowzixTheme

/**
 * Lightweight entry point used by the system media controls.
 *
 * It intentionally renders the same [TelegramForwardSheet] used by Now Playing rather than a
 * second notification-specific picker. The active playback controller is the source of truth for
 * the track, so a notification tap always targets the song that is current when this activity
 * opens.
 */
@AndroidEntryPoint
class TelegramForwardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: NowPlayingViewModel = hiltViewModel()
            val playbackState by viewModel.state.collectAsStateWithLifecycle()
            val forwardState by viewModel.forwardState.collectAsStateWithLifecycle()
            var pickerWasOpened by rememberSaveable { mutableStateOf(false) }
            val track = playbackState.currentTrack

            LaunchedEffect(track?.id, pickerWasOpened) {
                if (track != null && !pickerWasOpened) {
                    pickerWasOpened = true
                    viewModel.openForwardPicker()
                }
            }

            LaunchedEffect(pickerWasOpened, forwardState.isOpen) {
                if (pickerWasOpened && !forwardState.isOpen) finish()
            }

            MeowzixTheme {
                Box(Modifier.fillMaxSize()) {
                    if (forwardState.isOpen && track != null) {
                        TelegramForwardSheet(
                            track = track,
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
                    }
                }
            }
        }
    }
}
