package dev.behradhz.meowzix.feature.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.playback.RepeatMode
import dev.behradhz.meowzix.domain.settings.LiveSongCardStyle
import dev.behradhz.meowzix.domain.settings.LiveSongSideAction
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPlaybackControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allPlaybackModesFitAndSelect() {
        var result = PlaybackMode.ORDERED
        compose.setContent {
            var selected by remember { mutableStateOf(PlaybackMode.ORDERED) }
            MeowzixTheme { SettingsPlaybackModeSelector(selected) { selected = it; result = it } }
        }
        PlaybackMode.entries.forEach {
            compose.onNodeWithTag("settings-playback-mode-${it.name}").assertIsDisplayed()
        }
        compose.onNodeWithTag("settings-playback-mode-SMART_SHUFFLE").performClick()
        compose.runOnIdle { assertEquals(PlaybackMode.SMART_SHUFFLE, result) }
    }

    @Test fun repeatCardCyclesOffOneAll() {
        var result = RepeatMode.OFF
        compose.setContent {
            var selected by remember { mutableStateOf(RepeatMode.OFF) }
            MeowzixTheme { SettingsRepeatCycleRow(selected) { selected = it; result = it } }
        }
        compose.onNodeWithTag("settings-repeat-icon-OFF").assertIsDisplayed()
        compose.onNodeWithText("Tap to cycle: Off · One · All").assertDoesNotExist()
        compose.onNodeWithTag("settings-repeat-cycle").performClick()
        compose.onNodeWithTag("settings-repeat-icon-ONE").assertIsDisplayed()
        compose.runOnIdle { assertEquals(RepeatMode.ONE, result) }
        compose.onNodeWithTag("settings-repeat-cycle").performClick()
        compose.onNodeWithTag("settings-repeat-icon-ALL").assertIsDisplayed()
        compose.runOnIdle { assertEquals(RepeatMode.ALL, result) }
        compose.onNodeWithTag("settings-repeat-cycle").performClick()
        compose.onNodeWithTag("settings-repeat-icon-OFF").assertIsDisplayed()
        compose.runOnIdle { assertEquals(RepeatMode.OFF, result) }
    }

    @Test fun switchingSideActionUpdatesAnAnimatedCenteredIconSurface() {
        var setLeft: (() -> Unit)? = null
        compose.setContent {
            var left by remember { mutableStateOf(LiveSongSideAction.PREVIOUS) }
            setLeft = { left = LiveSongSideAction.SHUFFLE }
            MeowzixTheme {
                SettingsLiveUpdatePreview(
                    playback = PlaybackState(),
                    style = LiveSongCardStyle.CONTROLS,
                    leftAction = left,
                    rightAction = LiveSongSideAction.NEXT,
                )
            }
        }
        compose.onNodeWithTag("settings-live-left-icon").assertIsDisplayed()
        compose.onNodeWithTag("settings-live-middle-icon").assertIsDisplayed()
        compose.onNodeWithTag("settings-live-right-icon").assertIsDisplayed()
        compose.runOnIdle { setLeft?.invoke() }
        compose.waitForIdle()
        compose.onNodeWithTag("settings-live-left-icon").assertIsDisplayed()
    }

    @Test fun liveCardPreviewHasEmptyStateAndRespondsToLayout() {
        var setSong: (() -> Unit)? = null
        var setStyle: (() -> Unit)? = null
        compose.setContent {
            var playback by remember { mutableStateOf(PlaybackState()) }
            var style by remember { mutableStateOf(LiveSongCardStyle.MINIMAL) }
            setSong = {
                playback = PlaybackState(
                    status = PlaybackStatus.PLAYING,
                    currentTrack = NowPlayingTrack(UUID.randomUUID(), "Current song", "Current artist", null),
                    positionMs = 30_000, durationMs = 120_000,
                )
            }
            setStyle = { style = LiveSongCardStyle.CONTROLS }
            MeowzixTheme {
                SettingsLiveUpdatePreview(
                    playback, style, LiveSongSideAction.PREVIOUS, LiveSongSideAction.NEXT,
                )
            }
        }
        compose.onNodeWithTag("settings-live-update-placeholder").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("settings-live-update-controls").fetchSemanticsNodes().size)
        compose.runOnIdle { setSong?.invoke(); setStyle?.invoke() }
        compose.onNodeWithTag("settings-live-update-track").assertIsDisplayed()
        compose.onNodeWithTag("settings-live-update-controls").assertIsDisplayed()
    }
}
