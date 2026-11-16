package dev.behradhz.meowzix.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StagedSwipeRevealTest {
    private val octoberSecondaryThresholdPx = 150f

    @Test fun playNextRemainsVisibleUntilTheSecondStageIsReached() {
        assertEquals(0f, originalSwipeActionExitTarget(0f, octoberSecondaryThresholdPx), 0.001f)
        assertEquals(0f, originalSwipeActionExitTarget(75f, octoberSecondaryThresholdPx), 0.001f)
        assertEquals(0f, originalSwipeActionExitTarget(149.9f, octoberSecondaryThresholdPx), 0.001f)
        assertEquals(1f, originalSwipeActionExitTarget(150f, octoberSecondaryThresholdPx), 0.001f)
    }

    @Test fun layeredActionSwitchIsSymmetricAcrossBothSwipeDirections() {
        for (distance in listOf(0f, 75f, 149f, 150f, 300f)) {
            assertEquals(
                originalSwipeActionExitTarget(distance, octoberSecondaryThresholdPx),
                originalSwipeActionExitTarget(-distance, octoberSecondaryThresholdPx),
                0.001f,
            )
        }
        assertEquals(1f, originalSwipeActionExitTarget(-300f, octoberSecondaryThresholdPx), 0.001f)
    }

    @Test fun preservesOriginalTweenDurationAndPresentDayGlassForeground() {
        assertEquals(190, SWIPE_ACTION_EXIT_DURATION_MS)
        assertEquals(0.30f, TRACK_CARD_ALPHA, 0.001f)
    }
}
