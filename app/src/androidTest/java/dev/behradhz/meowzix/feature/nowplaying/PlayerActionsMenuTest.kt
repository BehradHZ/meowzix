package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.ParsedLyrics
import dev.behradhz.meowzix.domain.lyrics.TimedLyricLine
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerActionsMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lyricsPageRetainsPopupAndAdjustsTimingAndSwitchesVersions() {
        val trackId = UUID.randomUUID()
        val first = version(trackId, "First", true)
        val second = version(trackId, "Second", false)
        var selectedId by mutableStateOf(first.id)
        var delay by mutableLongStateOf(0L)
        var expanded by mutableStateOf(true)
        var imported by mutableIntStateOf(0)
        compose.setContent {
            val versions = listOf(first, second).map {
                it.copy(selected = it.id == selectedId, userDelayMs = if (it.id == selectedId) delay else 0L)
            }
            val selected = versions.first { it.selected }
            MeowzixTheme(darkTheme = true) {
                Box {
                    Button(onClick = { expanded = true }) { Text("Open menu") }
                    PlayerActionsMenu(
                        expanded = expanded, trackId = trackId,
                        lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId, selected, versions)),
                        lyricsExpanded = false, sleepTimerLabel = "Off",
                        onDismiss = { expanded = false }, onOpenSleepTimer = {},
                        onWhyThisSong = {}, onContinueVibe = {}, onToggleLyrics = {},
                        onImportLrc = { imported++ }, onPasteLyrics = {},
                        onAdjustDelay = { delay += it }, onSelectVersion = { selectedId = it },
                        onDeleteVersion = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("glass-player-actions-menu").assertIsDisplayed()
        compose.onNodeWithTag("player-quick-sleep-timer").assertIsDisplayed()
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-lyrics-page").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-delay-plus").performClick()
        compose.onNodeWithText("Delay +250 ms").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-delay-minus").performClick()
        compose.onNodeWithText("Delay +0 ms").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-next-version").performClick()
        compose.runOnIdle { assertEquals(second.id, selectedId) }
        compose.onNodeWithTag("player-lyrics-previous-version").performClick()
        compose.runOnIdle { assertEquals(first.id, selectedId) }
        compose.onNodeWithTag("player-lyrics-version-" + second.id).performClick()
        compose.runOnIdle { assertEquals(second.id, selectedId) }
        compose.onNodeWithTag("player-lyrics-page").assertIsDisplayed()
        compose.onNodeWithTag("player-lyrics-back").performClick()
        compose.onNodeWithTag("player-actions-page").assertIsDisplayed()
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-action-import-.lrc").performClick()
        compose.runOnIdle {
            assertEquals(1, imported)
            assertEquals(false, expanded)
        }
    }

    @Test fun deletingLyricsRequiresConfirmationAndAllowsCancel() {
        val trackId = UUID.randomUUID()
        val version = version(trackId, "My lyrics", true)
        var deletedId by mutableStateOf<UUID?>(null)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId, version, listOf(version))),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onOpenSleepTimer = {}, onWhyThisSong = {},
                    onContinueVibe = {}, onToggleLyrics = {}, onImportLrc = {},
                    onPasteLyrics = {}, onAdjustDelay = {}, onSelectVersion = {},
                    onDeleteVersion = { deletedId = it },
                )
            }
        }
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-lyrics-delete-" + version.id).performClick()
        compose.onNodeWithText("Delete lyrics?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, deletedId) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete lyrics?").assertDoesNotExist()
        compose.onNodeWithTag("player-lyrics-delete-" + version.id).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(version.id, deletedId) }
    }

    @Test fun emptyLyricsStillOffersAddAndPaste() {
        val trackId = UUID.randomUUID()
        var pasted by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onOpenSleepTimer = {}, onWhyThisSong = {},
                    onContinueVibe = {}, onToggleLyrics = {}, onImportLrc = {},
                    onPasteLyrics = { pasted++ }, onAdjustDelay = {}, onSelectVersion = {},
                    onDeleteVersion = {},
                )
            }
        }
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-lyrics-empty").assertIsDisplayed()
        compose.onNodeWithTag("player-action-paste-lyrics").performClick()
        compose.runOnIdle { assertEquals(1, pasted) }
    }

    @Test fun removeAllLyricsRequiresConfirmationThenOffersRestore() {
        val trackId = UUID.randomUUID()
        val embedded = version(trackId, "Detected", true).copy(sourceType = LyricsSourceType.EMBEDDED)
        var removed by mutableIntStateOf(0)
        var restored by mutableIntStateOf(0)
        var disabled by mutableStateOf(false)
        compose.setContent {
            val trackLyrics = if (disabled) TrackLyrics(trackId, automaticDiscoveryDisabled = true)
                else TrackLyrics(trackId, embedded, listOf(embedded))
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, trackLyrics),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onOpenSleepTimer = {}, onWhyThisSong = {},
                    onContinueVibe = {}, onToggleLyrics = {},
                    onImportLrc = {}, onPasteLyrics = {}, onAdjustDelay = {},
                    onSelectVersion = {}, onDeleteVersion = {},
                    onRemoveAllLyrics = { removed++; disabled = true },
                    onRestoreAutomaticLyrics = { restored++ },
                )
            }
        }
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-action-remove-lyrics-from-track").performClick()
        compose.onNodeWithText("Remove lyrics from track?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, removed) }
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Remove lyrics from track?").assertDoesNotExist()
        compose.onNodeWithTag("player-action-remove-lyrics-from-track").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.runOnIdle { assertEquals(1, removed) }
        compose.onNodeWithTag("player-lyrics-empty").assertIsDisplayed()
        compose.onNodeWithTag("player-action-restore-detected-lyrics").performClick()
        compose.runOnIdle { assertEquals(1, restored) }
    }

    private fun version(trackId: UUID, label: String, selected: Boolean): LyricsVersion =
        LyricsVersion(
            id = UUID.randomUUID(), trackId = trackId,
            sourceType = LyricsSourceType.USER_LRC, sourceLabel = label,
            rawText = "[00:01.00] Line",
            parsed = ParsedLyrics(LyricsContentType.TIMED,
                timedLines = listOf(TimedLyricLine(1000L, "Line", 0))),
            selected = selected, userSelected = selected, userDelayMs = 0L,
            createdAtEpochMs = 1L, updatedAtEpochMs = 1L,
        )
}
