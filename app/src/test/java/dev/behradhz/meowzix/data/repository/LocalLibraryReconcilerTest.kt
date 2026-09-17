package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.localmedia.ScannedLocalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryReconcilerTest {

    @Test
    fun `first scan creates every unique source`() {
        val scanned = listOf(track(1), track(2), track(3))

        val plan = LocalLibraryReconciler.plan(scanned, emptyList())

        assertEquals(3, plan.discovered)
        assertEquals(3, plan.toCreate.size)
        assertTrue(plan.toUpdate.isEmpty())
        assertTrue(plan.missingSourceIds.isEmpty())
    }

    @Test
    fun `repeated scan is idempotent and updates existing sources`() {
        val scanned = listOf(track(1), track(2))
        val existing = scanned.mapIndexed { index, item ->
            ExistingLocalSourceSnapshot(
                sourceId = "source-$index",
                trackId = "track-$index",
                contentUri = item.contentUri,
            )
        }

        val plan = LocalLibraryReconciler.plan(scanned, existing)

        assertTrue(plan.toCreate.isEmpty())
        assertEquals(2, plan.toUpdate.size)
        assertTrue(plan.missingSourceIds.isEmpty())
    }

    @Test
    fun `source removed from MediaStore is marked missing`() {
        val scanned = listOf(track(1))
        val existing = listOf(
            ExistingLocalSourceSnapshot("source-1", "track-1", track(1).contentUri),
            ExistingLocalSourceSnapshot("source-2", "track-2", track(2).contentUri),
        )

        val plan = LocalLibraryReconciler.plan(scanned, existing)

        assertEquals(listOf("source-2"), plan.missingSourceIds)
    }

    @Test
    fun `duplicate MediaStore rows do not create duplicate sources`() {
        val same = track(1)

        val plan = LocalLibraryReconciler.plan(listOf(same, same, same), emptyList())

        assertEquals(1, plan.toCreate.size)
        assertEquals(1, plan.discovered)
    }

    @Test
    fun `reconciles one thousand tracks without changing cardinality`() {
        val scanned = (0 until 1_000).map(::track)
        val existing = (0 until 500).map { index ->
            ExistingLocalSourceSnapshot(
                sourceId = "source-$index",
                trackId = "track-$index",
                contentUri = track(index).contentUri,
            )
        }

        val plan = LocalLibraryReconciler.plan(scanned, existing)

        assertEquals(1_000, plan.discovered)
        assertEquals(500, plan.toCreate.size)
        assertEquals(500, plan.toUpdate.size)
        assertTrue(plan.missingSourceIds.isEmpty())
    }

    private fun track(index: Int) = ScannedLocalTrack(
        mediaStoreId = index.toLong(),
        contentUri = "content://media/external/audio/media/$index",
        title = "Track $index",
        artist = "Artist ${index % 10}",
        album = "Album ${index % 5}",
        durationMs = 180_000L,
        trackNumber = index,
        year = 2026,
        artworkRef = null,
        mimeType = "audio/mpeg",
        fileSizeBytes = 5_000_000L,
        relativePath = "Music/",
        displayName = "track-$index.mp3",
        dateModifiedSeconds = 1_700_000_000L + index,
    )
}
