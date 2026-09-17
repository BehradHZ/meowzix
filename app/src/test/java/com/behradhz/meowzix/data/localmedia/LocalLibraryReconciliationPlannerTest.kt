package com.behradhz.meowzix.data.localmedia

import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalLibraryReconciliationPlannerTest {
    @Test
    fun plan_marksSourcesMissingFromAuthoritativeScanAsStale() {
        val keep = existing("source-a", "content://audio/1")
        val stale = existing("source-b", "content://audio/2")

        val plan = LocalLibraryReconciliationPlanner.plan(
            scanned = listOf(scanned("content://audio/1")),
            existing = listOf(keep, stale),
        )

        assertEquals(listOf(stale), plan.staleSources)
        assertEquals(listOf("content://audio/1"), plan.discovered.map { it.contentUri })
    }

    @Test
    fun plan_deduplicatesDuplicateRowsByContentUri() {
        val plan = LocalLibraryReconciliationPlanner.plan(
            scanned = listOf(
                scanned("content://audio/1", title = "Old metadata"),
                scanned("content://audio/1", title = "Current metadata"),
            ),
            existing = emptyList(),
        )

        assertEquals(1, plan.discovered.size)
        assertEquals("Current metadata", plan.discovered.single().title)
    }

    @Test
    fun repeatedIdenticalSnapshot_hasNoStaleSourcesAndOneCanonicalDiscovery() {
        val existing = listOf(existing("source-a", "content://audio/1"))
        val snapshot = listOf(scanned("content://audio/1"))

        val first = LocalLibraryReconciliationPlanner.plan(snapshot, existing)
        val second = LocalLibraryReconciliationPlanner.plan(snapshot, existing)

        assertEquals(first, second)
        assertEquals(0, second.staleSources.size)
        assertEquals(1, second.discovered.size)
    }

    @Test
    fun planner_handlesOneThousandUniqueTracksWithoutDroppingItems() {
        val snapshot = (1L..1_000L).map { id ->
            scanned(
                uri = "content://audio/$id",
                title = "Song $id",
            )
        }

        val plan = LocalLibraryReconciliationPlanner.plan(
            scanned = snapshot,
            existing = emptyList(),
        )

        assertEquals(1_000, plan.discovered.size)
        assertEquals(0, plan.staleSources.size)
        assertEquals(1_000, plan.discovered.map { it.contentUri }.toSet().size)
    }

    private fun existing(sourceId: String, uri: String) = LocalMediaSourceEntity(
        trackSourceId = sourceId,
        mediaStoreId = uri.substringAfterLast('/').toLong(),
        contentUri = uri,
        relativePath = "Music/",
        displayName = "song.mp3",
        dateModifiedEpochSeconds = 1L,
    )

    private fun scanned(
        uri: String,
        title: String = "Song",
    ) = ScannedLocalTrack(
        mediaStoreId = uri.substringAfterLast('/').toLong(),
        contentUri = uri,
        title = title,
        normalizedTitle = title.lowercase(),
        artist = "Artist",
        normalizedArtist = "artist",
        album = "Album",
        durationMs = 180_000,
        trackNumber = 1,
        year = 2026,
        artworkRef = null,
        displayName = "song.mp3",
        mimeType = "audio/mpeg",
        fileSizeBytes = 1_024,
        relativePath = "Music/",
        dateModifiedEpochSeconds = 1L,
    )
}
