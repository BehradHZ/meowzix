package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.telegram.TelegramTrackOrigin
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.ParsedLyrics
import dev.behradhz.meowzix.domain.lyrics.TimedLyricLine
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                        onDismiss = { expanded = false },                         onWhyThisSong = {}, onContinueVibe = {}, onOpenLyrics = {},
                        onImportLrc = { imported++ }, onPasteLyrics = {},
                        onAdjustDelay = { delay += it }, onSelectVersion = { selectedId = it },
                        onDeleteVersion = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("glass-player-actions-menu").assertIsDisplayed()
        compose.onNodeWithTag("player-action-sleep-timer").assertIsDisplayed()
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

    @Test fun viewInChatIsOnlyShownForTelegramSourcesAndInvokesTheSelectedTrackAction() {
        val trackId = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var hasSource by mutableStateOf(false)
        var opened by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = expanded,
                    trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                    lyricsExpanded = false,
                    sleepTimerLabel = "Off",
                    onDismiss = { expanded = false },
                    onWhyThisSong = {},
                    onContinueVibe = {},
                    onViewInChat = if (hasSource) ({ opened++ }) else null,
                    onOpenLyrics = {},
                    onImportLrc = {},
                    onPasteLyrics = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    onDeleteVersion = {},
                )
            }
        }
        compose.onNodeWithTag("player-action-view-in-chat").assertDoesNotExist()
        compose.runOnIdle { hasSource = true }
        compose.onNodeWithTag("player-action-view-in-chat").assertIsDisplayed()
        compose.onNodeWithTag("player-action-view-in-chat").performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals(false, expanded)
        }
    }

    @Test fun localOriginShowsDisabledLocalLabel() {
        var opened by mutableIntStateOf(0)
        val id = UUID.randomUUID()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = id,
                    lyricsState = NowPlayingLyricsState(id, TrackLyrics(id)),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onWhyThisSong = {}, onContinueVibe = {},
                    onViewInChat = { opened++ }, isLocalSource = true,
                    onOpenLyrics = {}, onImportLrc = {}, onPasteLyrics = {},
                    onAdjustDelay = {}, onSelectVersion = {}, onDeleteVersion = {},
                )
            }
        }
        compose.onNodeWithTag("player-action-view-in-chat").assertIsNotEnabled()
        compose.onNodeWithText("Local").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
    }

    @Test fun sourceActionIsPresentBeforeLookupAndRenamesToViewProfileAfterLookup() {
        val id = UUID.randomUUID()
        var origin by mutableStateOf<TelegramTrackOrigin?>(null)
        var opened by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = id,
                    lyricsState = NowPlayingLyricsState(id, TrackLyrics(id)),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onWhyThisSong = {}, onContinueVibe = {},
                    onViewInChat = { opened++ }, viewInChatOrigin = origin,
                    onOpenLyrics = {}, onImportLrc = {}, onPasteLyrics = {},
                    onAdjustDelay = {}, onSelectVersion = {}, onDeleteVersion = {},
                )
            }
        }
        compose.onNodeWithTag("player-action-view-in-chat").assertIsDisplayed()
        compose.runOnIdle {
            origin = TelegramTrackOrigin(123L, "Friend", null, isProfileMusic = true)
        }
        compose.onNodeWithText("View Profile").assertIsDisplayed()
        compose.onNodeWithText("View in Chat").assertDoesNotExist()
        compose.onNodeWithTag("player-action-view-in-chat").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun removeSongMorphsIntoSelectionPanelAndRequiresConfirmation() {
        val id = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var removal: Pair<Boolean, Boolean>? = null
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box {
                    Button(onClick = { expanded = true }) { Text("Open menu") }
                    PlayerActionsMenu(
                        expanded = expanded,
                        trackId = id,
                        trackTitle = "Test track",
                        lyricsState = NowPlayingLyricsState(id, TrackLyrics(id)),
                        lyricsExpanded = false,
                        sleepTimerLabel = "Off",
                        onDismiss = { expanded = false },
                        onWhyThisSong = {},
                        onContinueVibe = {},
                        onOpenLyrics = {},
                        onImportLrc = {},
                        onPasteLyrics = {},
                        onAdjustDelay = {},
                        onSelectVersion = {},
                        onDeleteVersion = {},
                        canRemoveOffline = false,
                        onRemoveFromLocations = { offline, library -> removal = offline to library },
                    )
                }
            }
        }
        compose.onNodeWithTag("player-action-remove-song").performClick()
        compose.onNodeWithTag("player-removal-panel").assertIsDisplayed()
        compose.onNodeWithText("Test track").assertIsDisplayed()
        compose.onNodeWithTag("player-removal-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("player-removal-library").performClick()
        compose.onNodeWithTag("player-removal-confirm").assertIsEnabled()
        compose.onNodeWithTag("player-removal-confirm").performClick()
        compose.runOnIdle {
            assertEquals(false to true, removal)
            assertEquals(false, expanded)
        }
    }

    @Test fun primaryMenuUsesRowsWithInlineTimerStatusAndPreservesActions() {
        val trackId = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var whyOpened by mutableIntStateOf(0)
        var vibeOpened by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box {
                    Button(onClick = { expanded = true }) { Text("Open menu") }
                    PlayerActionsMenu(
                        expanded = expanded,
                        trackId = trackId,
                        lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                        lyricsExpanded = false,
                        sleepTimerLabel = "Off",
                        onDismiss = { expanded = false },
                        onWhyThisSong = { whyOpened++ },
                        onContinueVibe = { vibeOpened++ },
                        onOpenLyrics = {},
                        onImportLrc = {},
                        onPasteLyrics = {},
                        onAdjustDelay = {},
                        onSelectVersion = {},
                        onDeleteVersion = {},
                    )
                }
            }
        }
        compose.onNodeWithText("More options").assertIsDisplayed()
        compose.onNodeWithText("Quick actions").assertDoesNotExist()
        compose.onNodeWithTag("player-action-sleep-timer").assertIsDisplayed()
        compose.onNodeWithTag("player-action-why-this-song").assertIsDisplayed()
        compose.onNodeWithTag("player-action-continue-the-vibe").assertIsDisplayed()
        compose.onNodeWithTag("player-action-lyrics").assertIsDisplayed()
        compose.onNodeWithText("Off").assertIsDisplayed()

        compose.onNodeWithTag("player-action-sleep-timer").performClick()
        compose.onNodeWithTag("player-timer-page").assertIsDisplayed()
        compose.runOnIdle { assertEquals(true, expanded) }
        compose.onNodeWithTag("player-timer-back").performClick()
        compose.onNodeWithTag("player-actions-page").assertIsDisplayed()
        compose.onNodeWithTag("player-action-why-this-song").performClick()
        compose.runOnIdle { assertEquals(1, whyOpened); assertEquals(false, expanded) }
        compose.onNodeWithText("Open menu").performClick()
        compose.onNodeWithTag("player-action-continue-the-vibe").performClick()
        compose.runOnIdle { assertEquals(1, vibeOpened); assertEquals(false, expanded) }
    }

    @Test fun primaryActionOrderEndsWithCenteredExplanationLink() {
        val trackId = UUID.randomUUID()
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = true, trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                    lyricsExpanded = false, sleepTimerLabel = "Off",
                    onDismiss = {}, onWhyThisSong = {}, onContinueVibe = {},
                    onViewInChat = {},
                    onOpenLyrics = {}, onImportLrc = {}, onPasteLyrics = {},
                    onAdjustDelay = {}, onSelectVersion = {}, onDeleteVersion = {},
                )
            }
        }
        val ordered = listOf(
            "player-action-playback-speed",
            "player-action-sleep-timer",
            "player-action-lyrics",
            "player-action-continue-the-vibe",
            "player-action-view-in-chat",
            "player-action-why-this-song",
        )
        val tops = ordered.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Player actions should match the requested vertical order", tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun quickTimerSwitchTogglesWithoutOpeningTimerConfiguration() {
        val trackId = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var requestedEnabled by mutableStateOf<Boolean?>(null)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box {
                    Button(onClick = { expanded = true }) { Text("Open menu") }
                    PlayerActionsMenu(
                        expanded = expanded,
                        trackId = trackId,
                        lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                        lyricsExpanded = false,
                        sleepTimerLabel = "Off · Last: 30 min",
                        onDismiss = { expanded = false },
                        onWhyThisSong = {},
                        onContinueVibe = {},
                        onOpenLyrics = {},
                        onImportLrc = {},
                        onPasteLyrics = {},
                        onAdjustDelay = {},
                        onSelectVersion = {},
                        onDeleteVersion = {},
                        sleepTimerActive = false,
                        onSleepTimerEnabledChange = { requestedEnabled = it },
                    )
                }
            }
        }
        compose.onNodeWithTag("player-sleep-timer-switch").assertIsDisplayed()
        compose.onNodeWithTag("player-sleep-timer-switch").performClick()
        compose.runOnIdle {
            assertEquals(true, requestedEnabled)
            assertEquals(true, expanded)
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
                    onDismiss = {}, onWhyThisSong = {},
                    onContinueVibe = {}, onOpenLyrics = {}, onImportLrc = {},
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
                    onDismiss = {}, onWhyThisSong = {},
                    onContinueVibe = {}, onOpenLyrics = {}, onImportLrc = {},
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

    @Test fun showToggleKeepsLyricsIntactAndOpenLyricsRemainsAvailable() {
        val trackId = UUID.randomUUID()
        val lyric = version(trackId, "Detected", true).copy(sourceType = LyricsSourceType.EMBEDDED)
        var shown by mutableStateOf(true)
        var expanded by mutableStateOf(true)
        var opened by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = expanded,
                    trackId = trackId,
                    lyricsState = NowPlayingLyricsState(
                        trackId, TrackLyrics(trackId, lyric, listOf(lyric), showInPlayer = shown),
                    ),
                    lyricsExpanded = false,
                    sleepTimerLabel = "Off",
                    onDismiss = { expanded = false },
                    onWhyThisSong = {},
                    onContinueVibe = {},
                    onOpenLyrics = { opened++ },
                    onImportLrc = {},
                    onPasteLyrics = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    onDeleteVersion = {},
                    onShowLyricsChange = { shown = it },
                )
            }
        }
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-lyrics-show-toggle").assertIsOn().performClick()
        compose.onNodeWithTag("player-lyrics-show-toggle").assertIsOff()
        compose.onNodeWithTag("player-lyrics-version-" + lyric.id).assertIsDisplayed()
        compose.onNodeWithTag("player-action-remove-lyrics-from-track").assertDoesNotExist()
        compose.onNodeWithTag("player-action-open-lyrics").performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            assertEquals(false, expanded)
            assertEquals(false, shown)
        }
        compose.runOnIdle { expanded = true }
        compose.onNodeWithTag("player-action-lyrics").performClick()
        compose.onNodeWithTag("player-lyrics-show-toggle").assertIsOff().performClick()
        compose.onNodeWithTag("player-lyrics-show-toggle").assertIsOn()
    }

    @Test fun speedPageKeepsPopupOpenAndAppliesFineAdjustments() {
        val trackId = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var selectedSpeed by mutableFloatStateOf(2f)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = expanded,
                    trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                    lyricsExpanded = false,
                    sleepTimerLabel = "Off",
                    onDismiss = { expanded = false },
                    onWhyThisSong = {},
                    onContinueVibe = {},
                    onOpenLyrics = {},
                    onImportLrc = {},
                    onPasteLyrics = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    onDeleteVersion = {},
                    playbackSpeed = selectedSpeed,
                    onChangePlaybackSpeed = { selectedSpeed = it },
                )
            }
        }
        compose.onNodeWithTag("player-action-playback-speed").performClick()
        compose.onNodeWithTag("player-speed-page").assertIsDisplayed()
        compose.onNodeWithTag("speed-option-5").performClick()
        compose.onNodeWithTag("speed-step-plus").performClick()
        compose.runOnIdle {
            assertEquals(2.1f, selectedSpeed, 0.001f)
            assertEquals(true, expanded)
        }
        compose.onNodeWithTag("player-speed-back").performClick()
        compose.onNodeWithTag("player-actions-page").assertIsDisplayed()
    }

    @Test fun timerPageKeepsPresetsAndCustomControlsInsidePopup() {
        val trackId = UUID.randomUUID()
        var expanded by mutableStateOf(true)
        var requestedMinutes by mutableIntStateOf(0)
        var endOfTrackRequests by mutableIntStateOf(0)
        var extended by mutableIntStateOf(0)
        var shortened by mutableIntStateOf(0)
        var state by mutableStateOf(SleepTimerState.Off)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlayerActionsMenu(
                    expanded = expanded,
                    trackId = trackId,
                    lyricsState = NowPlayingLyricsState(trackId, TrackLyrics(trackId)),
                    lyricsExpanded = false,
                    sleepTimerLabel = "Off",
                    onDismiss = { expanded = false },
                    onWhyThisSong = {},
                    onContinueVibe = {},
                    onOpenLyrics = {},
                    onImportLrc = {},
                    onPasteLyrics = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    onDeleteVersion = {},
                    sleepTimerState = state,
                    sleepTimerLastMinutes = 45,
                    sleepTimerLastMode = SleepTimerPresetMode.DURATION,
                    onSetSleepTimerMinutes = {
                        requestedMinutes = it
                        state = SleepTimerState(SleepTimerMode.DURATION, it * 60_000L)
                    },
                    onSetSleepTimerEndOfTrack = { endOfTrackRequests++ },
                    onExtendSleepTimer = { extended++ },
                    onShortenSleepTimer = { shortened++ },
                )
            }
        }
        compose.onNodeWithTag("player-action-sleep-timer").performClick()
        compose.onNodeWithTag("player-timer-page").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-end-of-track").performClick()
        compose.runOnIdle { assertEquals(1, endOfTrackRequests) }
        compose.onNodeWithTag("sleep-timer-option-custom").performClick()
        compose.onNodeWithTag("sleep-timer-custom-start").performClick()
        compose.runOnIdle {
            assertEquals(45, requestedMinutes)
            assertEquals(true, expanded)
        }
        compose.onNodeWithTag("sleep-timer-shorten").assertIsDisplayed().performClick()
        compose.onNodeWithTag("sleep-timer-extend").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, shortened)
            assertEquals(1, extended)
        }
        compose.onNodeWithTag("player-timer-back").performClick()
        compose.onNodeWithTag("player-actions-page").assertIsDisplayed()
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
