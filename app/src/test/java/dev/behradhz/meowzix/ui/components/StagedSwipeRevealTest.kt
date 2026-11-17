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

    @Test fun primaryLayerStaysPutUntilSecondaryThreshold() {
        assertEquals(0f, stagedSwipeActionSlidePx(100f, 0f, 20f), 0.001f)
        assertEquals(0f, stagedSwipeActionSlidePx(-100f, 0f, 20f), 0.001f)
    }

    @Test fun primarySlidesPastRoundedCornerToRevealOnlyRemove() {
        // At full exit, the primary action must begin beyond the moving
        // card's curved edge so it cannot leak through corner cutouts.
        assertEquals(170f, stagedSwipeActionSlidePx(150f, 1f, 20f), 0.001f)
        assertEquals(-170f, stagedSwipeActionSlidePx(-150f, 1f, 20f), 0.001f)
        assertEquals(85f, stagedSwipeActionSlidePx(150f, 0.5f, 20f), 0.001f)
        assertEquals(-85f, stagedSwipeActionSlidePx(-150f, 0.5f, 20f), 0.001f)
    }

    @Test fun slideProgressIsBoundedAndZeroOffsetNeverMovesActions() {
        assertEquals(0f, stagedSwipeActionSlidePx(0f, 1f, 20f), 0.001f)
        assertEquals(0f, stagedSwipeActionSlidePx(150f, -2f, 20f), 0.001f)
        assertEquals(170f, stagedSwipeActionSlidePx(150f, 2f, 20f), 0.001f)
    }

    @Test fun preservesOriginalTweenDurationAndPresentDayGlassForeground() {
        assertEquals(190, SWIPE_ACTION_EXIT_DURATION_MS)
        assertEquals(0.30f, TRACK_CARD_ALPHA, 0.001f)
    }
}
