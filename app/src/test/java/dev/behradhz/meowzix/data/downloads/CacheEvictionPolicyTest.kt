package dev.behradhz.meowzix.data.downloads

import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheEvictionPolicyTest {
    @Test
    fun `pinned files are never selected for cache eviction`() {
        val pinned = record("pinned", true)
        val cache = record("cache", false)

        assertEquals(listOf(cache), CacheEvictionPolicy.evictable(listOf(pinned, cache)))
    }

    private fun record(id: String, pinned: Boolean) = DownloadRecordEntity(
        id = id,
        trackId = id,
        trackSourceId = "source-$id",
        tdFileId = null,
        status = "COMPLETED",
        downloadedBytes = 1L,
        totalBytes = 1L,
        localPath = null,
        pinned = pinned,
        failureReason = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}
