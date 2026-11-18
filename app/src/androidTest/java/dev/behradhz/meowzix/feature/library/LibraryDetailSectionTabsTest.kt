package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDetailSectionTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedArtistCanNavigateToTheFullArtistsListAndOtherSections() {
        var selecting by mutableStateOf(false)
        var selectedSection by mutableStateOf(LibrarySectionV4.ARTISTS)
        var requestedSection: LibrarySectionV4? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                LibraryDetailSectionTabs(
                    visible = selecting,
                    activeSection = selectedSection,
                    onSelectSection = {
                        requestedSection = it
                        selectedSection = it
                    },
                )
            }
        }

        compose.onNodeWithText("Artists").assertDoesNotExist()
        compose.runOnIdle { selecting = true }
        compose.mainClock.advanceTimeBy(380)
        compose.onNodeWithTag("library-detail-section-tabs").assertExists()
        compose.onNodeWithText("Artists").performClick()
        compose.runOnIdle { assertEquals(LibrarySectionV4.ARTISTS, requestedSection) }

        compose.onNodeWithText("Tracks").performClick()
        compose.runOnIdle { assertEquals(LibrarySectionV4.TRACKS, requestedSection) }
        compose.onNodeWithText("Albums").performClick()
        compose.runOnIdle { assertEquals(LibrarySectionV4.ALBUMS, requestedSection) }

        compose.runOnIdle { selecting = false }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Tracks").assertDoesNotExist()
        compose.onNodeWithTag("library-detail-section-tabs").assertDoesNotExist()
    }
}
