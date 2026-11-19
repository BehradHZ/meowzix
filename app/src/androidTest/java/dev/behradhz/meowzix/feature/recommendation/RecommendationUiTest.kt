package dev.behradhz.meowzix.feature.recommendation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecommendationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun explanationsRenderActualEvidenceAndDismiss() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                RecommendationActionContent(
                    state = RecommendationActionState(
                        UUID(0, 1),
                        "Track",
                        why = true,
                        reasons = listOf(RecommendationReason.FAVORITE, RecommendationReason.NOT_PLAYED_RECENTLY),
                    ),
                    dismiss = { dismissed = true },
                    play = {},
                    playMix = {},
                    moreLikeThis = {},
                    suggestLess = {},
                    snooze = {},
                    undoFeedback = {},
                    openInsights = {},
                )
            }
        }
        compose.onNodeWithText("It's one of your favorites").assertIsDisplayed()
        compose.onNodeWithText("You haven't played it recently").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        assertTrue(dismissed)
    }
    @Test fun coldStartVibeHasAnHonestEmptyState() {
        compose.setContent {
            MaterialTheme {
                RecommendationActionContent(
                    state = RecommendationActionState(UUID(0, 1), "Track"),
                    dismiss = {},
                    play = {},
                    playMix = {},
                    moreLikeThis = {},
                    suggestLess = {},
                    snooze = {},
                    undoFeedback = {},
                    openInsights = {},
                )
            }
        }
        compose.onNodeWithText("Continue the Vibe").assertIsDisplayed()
        compose.onNodeWithText("More music or listening history will help us find a matching mix.").assertIsDisplayed()
    }
    @Test fun inlineWhyShowsReasonsAndKeepsFeedbackActionsInThePanel() {
        val trackId = UUID(0, 42)
        var more = 0
        var less = 0
        var snoozed = 0
        var undo = 0
        var insights = 0
        compose.setContent {
            MaterialTheme {
                RecommendationInlineWhyContent(
                    state = RecommendationActionState(
                        trackId = trackId,
                        title = "Sample song",
                        why = true,
                        inlineWhy = true,
                        reasons = listOf(RecommendationReason.FAVORITE),
                        feedbackNotice = "More like this saved",
                        feedbackTrackId = trackId,
                    ),
                    moreLikeThis = { if (it == trackId) more++ },
                    suggestLess = { if (it == trackId) less++ },
                    snooze = { if (it == trackId) snoozed++ },
                    undoFeedback = { if (it == trackId) undo++ },
                    openInsights = { insights++ },
                )
            }
        }
        compose.onNodeWithTag("player-why-content").assertIsDisplayed()
        compose.onNodeWithText("It's one of your favorites", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("player-why-more-like").performClick()
        compose.onNodeWithTag("player-why-suggest-less").performClick()
        compose.onNodeWithTag("player-why-snooze").performScrollTo().performClick()
        compose.onNodeWithTag("player-why-undo").performClick()
        compose.onNodeWithTag("player-why-insights").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, more)
            assertEquals(1, less)
            assertEquals(1, snoozed)
            assertEquals(1, undo)
            assertEquals(1, insights)
        }
    }

    @Test fun inlineInsightsStayInThePanelInsteadOfLaunchingAnotherWindow() {
        compose.setContent {
            MaterialTheme {
                RecommendationInlineWhyContent(
                    state = RecommendationActionState(
                        trackId = UUID(0, 2),
                        why = true,
                        inlineWhy = true,
                        showInsights = true,
                        insightsText = "Local metrics report",
                    ),
                    moreLikeThis = {},
                    suggestLess = {},
                    snooze = {},
                    undoFeedback = {},
                    openInsights = {},
                )
            }
        }
        compose.onNodeWithText("Local metrics report").assertIsDisplayed()
        compose.onNodeWithTag("player-why-more-like").assertDoesNotExist()
    }

    @Test fun timeMixTitlesUseExistingBuckets() {
        assertEquals("Late Night Mix", RecommendationSection(RecommendationSectionKind.TIME_MIX, emptyList(), TimeBucket.LATE_NIGHT).title())
        assertEquals("Morning Mix", RecommendationSection(RecommendationSectionKind.TIME_MIX, emptyList(), TimeBucket.MORNING).title())
    }
}
