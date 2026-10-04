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
    @Test fun timeMixTitlesUseExistingBuckets() {
        assertEquals("Late Night Mix", RecommendationSection(RecommendationSectionKind.TIME_MIX, emptyList(), TimeBucket.LATE_NIGHT).title())
        assertEquals("Morning Mix", RecommendationSection(RecommendationSectionKind.TIME_MIX, emptyList(), TimeBucket.MORNING).title())
    }
}
