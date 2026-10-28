package dev.behradhz.meowzix.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForwardFolderMemoryCacheTest {
    @Test fun repeatedOpenReusesSameAccountSnapshotWithoutReload() {
        val cache = ForwardFolderMemoryCache<String>()
        val session = Any()
        cache.save("account-a", session, revision = 4L, nowMillis = 100L, folders = listOf("Friends", "Music"))
        assertEquals(listOf("Friends", "Music"), cache.snapshot("account-a", session, 4L, 200L))
        assertEquals(listOf("Friends", "Music"), cache.snapshot("account-a", session, 4L, 500L))
    }

    @Test fun emptySnapshotsAreAlsoCached() {
        val cache = ForwardFolderMemoryCache<String>()
        val session = Any()
        cache.save("account-a", session, 1L, 1_000L, emptyList())
        assertEquals(emptyList<String>(), cache.snapshot("account-a", session, 1L, 2_000L))
    }

    @Test fun anotherAccountOrTdlibClientCannotReuseOldFolders() {
        val cache = ForwardFolderMemoryCache<String>()
        val firstClient = Any()
        cache.save("account-a", firstClient, 2L, 100L, listOf("Private"))
        assertNull(cache.snapshot("account-b", firstClient, 2L, 200L))
        assertNull(cache.snapshot("account-a", Any(), 2L, 200L))
    }

    @Test fun tdlibFolderUpdatesInvalidateEvenWhenNamesDidNotChange() {
        val cache = ForwardFolderMemoryCache<String>()
        val session = Any()
        cache.save("account-a", session, 8L, 100L, listOf("Friends"))
        assertNull(cache.snapshot("account-a", session, 9L, 200L))
        cache.save("account-a", session, 9L, 200L, listOf("Friends", "Channels"))
        assertEquals(listOf("Friends", "Channels"), cache.snapshot("account-a", session, 9L, 300L))
    }

    @Test fun cacheExpiresAfterFiveMinutesAndManualRefreshReplacesValue() {
        val cache = ForwardFolderMemoryCache<String>()
        val session = Any()
        cache.save("account-a", session, 1L, 100L, listOf("Old"))
        assertEquals(listOf("Old"), cache.snapshot("account-a", session, 1L, 300_099L))
        assertNull(cache.snapshot("account-a", session, 1L, 300_100L))
        cache.save("account-a", session, 1L, 301_000L, listOf("New"))
        assertEquals(listOf("New"), cache.snapshot("account-a", session, 1L, 302_000L))
    }
}
