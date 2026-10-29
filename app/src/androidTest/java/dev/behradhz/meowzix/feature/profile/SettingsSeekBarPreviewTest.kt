package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.AudioSpectrumState
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.settings.PlayerSeekBarStyle
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSeekBarPreviewTest {
    @get:Rule val compose = createComposeRule()

    private val activeTrack = PlaybackState(
        currentTrack = NowPlayingTrack(UUID.randomUUID(), "Current song", "Artist", null),
        status = PlaybackStatus.PLAYING,
        durationMs = 210_000,
        positionMs = 51_000,
    )
    private val fft = AudioSpectrumState(
        bands = FloatArray(48) { 0.4f },
        sourceUri = "content://current-song",
    )

    @Test
    fun previewCaptureOnlyRunsWhenWaveformCardIsOnScreen() {
        val captureChanges = mutableListOf<Boolean>()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                LazyColumn(Modifier.fillMaxSize().testTag("settings-scroll")) {
                    item {
                        SettingsSeekBarStylePicker(
                            selected = PlayerSeekBarStyle.WAVEFORM,
                            onSelect = {},
                            playback = activeTrack,
                            spectrum = fft,
                            onPreviewCaptureChange = { captureChanges.add(it) },
                        )
                    }
                    item { Spacer(Modifier.height(1200.dp)) }
                }
            }
        }

        compose.waitForIdle()
        compose.runOnIdle { assertTrue(captureChanges.last()) }
        compose.onNodeWithTag("settings-scroll").performScrollToIndex(1)
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(captureChanges.last()) }
        compose.onNodeWithTag("settings-scroll").performScrollToIndex(0)
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(captureChanges.last()) }
    }

    @Test
    fun noPlaybackNeverRequestsLiveFft() {
        val captureChanges = mutableListOf<Boolean>()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SettingsSeekBarStylePicker(
                    selected = PlayerSeekBarStyle.WAVEFORM,
                    onSelect = {},
                    playback = PlaybackState(),
                    onPreviewCaptureChange = { captureChanges.add(it) },
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(captureChanges.isNotEmpty())
            assertEquals(false, captureChanges.last())
        }
    }
}
