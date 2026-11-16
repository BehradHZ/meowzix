package dev.behradhz.meowzix.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSessionRefreshPolicyTest {
    @Test fun firstForegroundSessionLoadsTheFeed() {
        assertTrue(HomeSessionRefreshPolicy.needsRefresh(1L, 0L, false))
    }

    @Test fun tabRevisitsAndPlaybackChangesDoNotReloadInTheSameSession() {
        assertFalse(HomeSessionRefreshPolicy.needsRefresh(7L, 7L, true))
    }

    @Test fun newForegroundSessionReloadsEvenWithAReadySnapshot() {
        assertTrue(HomeSessionRefreshPolicy.needsRefresh(8L, 7L, true))
    }

    @Test fun failedOrIncompleteLoadCanRetryInSameSession() {
        assertTrue(HomeSessionRefreshPolicy.needsRefresh(7L, 7L, false))
    }

    @Test fun preForegroundCompositionDoesNotStartWork() {
        assertFalse(HomeSessionRefreshPolicy.needsRefresh(0L, 0L, false))
    }

    @Test fun sessionIdsAreMonotonicallyIncreasing() {
        val first = HomeSessionIds.next()
        val second = HomeSessionIds.next()
        assertTrue(second > first)
    }
}
