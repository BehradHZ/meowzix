package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsSourceType
import dev.behradhz.meowzix.domain.lyrics.LyricsVersion
import dev.behradhz.meowzix.domain.lyrics.ParsedLyrics
import dev.behradhz.meowzix.domain.lyrics.TimedLyricLine
import dev.behradhz.meowzix.domain.lyrics.TrackLyrics
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpandedLyricsPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun expandedLyricsOwnTheViewportWithoutASecondVisibleOptionsToolbar() {
        val trackId = UUID.randomUUID()
        val selected = LyricsVersion(
            id = UUID.randomUUID(),
            trackId = trackId,
            sourceType = LyricsSourceType.USER_LRC,
            sourceLabel = "test",
            rawText = "[00:01.00] Active line",
            parsed = ParsedLyrics(LyricsContentType.TIMED, timedLines = listOf(TimedLyricLine(1000L, "Active line", 0))),
            selected = true,
            userSelected = true,
            userDelayMs = 0L,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val lyrics = NowPlayingLyricsState(trackId, TrackLyrics(trackId, selected, listOf(selected)), activeLineIndex = 0)
        compose.setContent {
            Box(Modifier.size(width = 360.dp, height = 500.dp)) {
                ExpandedLyricsPanel(
                    state = lyrics,
                    onLineSeek = {},
                    onImportLrc = {},
                    onPaste = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    reduceMotion = true,
                    showToolbar = false,
                )
            }
        }
        compose.onNodeWithTag("lyrics-toolbar").assertDoesNotExist()
        compose.onNodeWithText("Lyrics options").assertDoesNotExist()
        compose.onNodeWithText("Delay +0 ms").assertDoesNotExist()
        compose.onNodeWithText("Active line").assertIsDisplayed()
    }

    @Test fun missingLyricsStillOffersOneEmptyStateWithImportAndPaste() {
        compose.setContent {
            Box(Modifier.size(width = 360.dp, height = 500.dp)) {
                ExpandedLyricsPanel(
                    state = NowPlayingLyricsState(trackId = UUID.randomUUID()),
                    onLineSeek = {},
                    onImportLrc = {},
                    onPaste = {},
                    onAdjustDelay = {},
                    onSelectVersion = {},
                    reduceMotion = true,
                    showToolbar = false,
                )
            }
        }
        compose.onNodeWithTag("lyrics-empty").assertIsDisplayed()
        compose.onNodeWithText("Import .lrc").assertIsDisplayed()
        compose.onNodeWithText("Paste lyrics").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-toolbar").assertDoesNotExist()
    }
}
