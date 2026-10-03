package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RewardBuilderTest {
    private fun reward(types: Set<String>, ratio: Double? = null) =
        PersonalizationRewardBuilder.reward(PlaybackOutcome(UUID(0, 1), types, ratio))

    @Test fun allActionAndOutcomeCombinationsUseTheVersionedRewardContract() {
        val actions = listOf("MANUAL_SELECTED" to 0.35, "REPLAYED" to 0.40, "FAVORITED" to 0.50)
        val outcomes = listOf(
            Triple("PLAY_STOPPED", 0.2, 0.0), Triple("PLAY_STOPPED", 0.75, 0.20),
            Triple("SKIPPED_LATE", 0.40, -0.15), Triple("SKIPPED_LATE", 0.75, 0.05),
            Triple("SKIPPED_EARLY", 0.10, -0.70), Triple("PLAY_COMPLETED", 0.95, 0.60),
        )
        for (mask in 0 until 8) for ((type, ratio, outcomeReward) in outcomes) {
            val selected = actions.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
            val types = selected.map { it.first }.toSet() + setOf("PLAY_STARTED", "AUTO_SELECTED", type)
            assertEquals("mask=$mask type=$type", (selected.sumOf { it.second } + outcomeReward).coerceIn(-1.0, 1.0), reward(types, ratio), 1e-12)
        }
    }
    @Test fun unFavoriteCancelsFavoriteRewardWithinSameInstance() {
        assertEquals(0.0, reward(setOf("FAVORITED", "UNFAVORITED", "PLAY_STOPPED")), 0.0)
    }
    @Test fun finalFavoriteActionHandlesRepeatedToggles() {
        assertEquals(0.5, PersonalizationRewardBuilder.reward(PlaybackOutcome(UUID(0, 1),
            setOf("FAVORITED", "UNFAVORITED", "PLAY_STOPPED"), lastFavoriteAction = true)), 0.0)
        assertEquals(0.0, PersonalizationRewardBuilder.reward(PlaybackOutcome(UUID(0, 1),
            setOf("FAVORITED", "UNFAVORITED", "PLAY_STOPPED"), lastFavoriteAction = false)), 0.0)
    }
    @Test fun autoplayAndSeeksAreNeutral() {
        assertEquals(0.0, reward(setOf("AUTO_SELECTED", "PLAY_STARTED", "SEEKED")), 0.0)
    }
    @Test fun queueRemovalOnlyPenalizesUnplayedOccurrence() {
        assertEquals(-0.20, reward(setOf("QUEUE_REMOVED")), 0.0)
        assertEquals(0.0, reward(setOf("QUEUE_REMOVED", "PLAY_STARTED")), 0.0)
    }
    @Test fun completionClassificationWinsOverContradictorySkip() {
        assertEquals(0.60, reward(setOf("PLAY_COMPLETED", "SKIPPED_EARLY")), 0.0)
    }
    @Test fun nonFiniteAndOutOfRangeRatiosCannotBecomePartialRewards() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 2.0).forEach { assertEquals(0.0, reward(setOf("PLAY_STOPPED"), it), 0.0) }
    }
    @Test fun sixtyPercentBoundaryIsInclusiveAndNinetyExcludedFromPartial() {
        assertEquals(0.0, reward(setOf("PLAY_STOPPED"), 0.5999), 0.0)
        assertEquals(0.20, reward(setOf("PLAY_STOPPED"), 0.60), 0.0)
        assertEquals(0.0, reward(setOf("PLAY_STOPPED"), 0.90), 0.0)
    }
    @Test fun duplicateActionTypesCannotAccumulateUnboundedReward() {
        assertEquals(0.35, reward(listOf("MANUAL_SELECTED", "MANUAL_SELECTED").toSet()), 0.0)
        assertEquals(1.0, reward(setOf("MANUAL_SELECTED", "FAVORITED", "REPLAYED", "PLAY_COMPLETED")), 0.0)
    }
}
