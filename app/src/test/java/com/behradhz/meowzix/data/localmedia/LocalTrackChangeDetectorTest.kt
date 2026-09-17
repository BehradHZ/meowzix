package com.behradhz.meowzix.data.localmedia

import com.behradhz.meowzix.core.model.SourceAvailability
import com.behradhz.meowzix.core.model.TrackSourceType
import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity
import com.behradhz.meowzix.data.db.entity.TrackEntity
import com.behradhz.meowzix.data.db.entity.TrackSourceEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackChangeDetectorTest {
    @Test
    fun identicalScan_isNotMeaningfullyChanged() {
        val scanned = scanned()

        assertFalse(LocalTrackChangeDetector.trackChanged(track(), scanned))
        assertFalse(LocalTrackChangeDetector.sourceChanged(source(), scanned))
        assertFalse(LocalTrackChangeDetector.localSourceChanged(local(), scanned))
    }

    @Test
    fun metadataChange_isDetected() {
        assertTrue(
            LocalTrackChangeDetector.trackChanged(
                track(),
                scanned().copy(title = "Renamed", normalizedTitle = "renamed"),
            ),
        )
    }

    @Test
    fun fileMetadataChange_isDetected() {
        assertTrue(
            LocalTrackChangeDetector.localSourceChanged(
                local(),
                scanned().copy(dateModifiedEpochSeconds = 2L),
            ),
        )
    }

    private fun track() = TrackEntity(
        id = "track",
        title = "Song",
        normalizedTitle = "song",
        artist = "Artist",
        normalizedArtist = "artist",
        album = "Album",
        durationMs = 180_000,
        trackNumber = 1,
        year = 2026,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun source() = TrackSourceEntity(
        id = "source",
        trackId = "track",
        type = TrackSourceType.LOCAL_MEDIASTORE,
        availability = SourceAvailability.AVAILABLE_LOCAL,
        contentUri = "content://audio/1",
        localPath = null,
        mimeType = "audio/mpeg",
        fileSizeBytes = 1_024,
        contentHashSha256 = null,
        trainingEligible = true,
        createdAtEpochMs = 1L,
        lastVerifiedAtEpochMs = 1L,
    )

    private fun local() = LocalMediaSourceEntity(
        trackSourceId = "source",
        mediaStoreId = 1L,
        contentUri = "content://audio/1",
        relativePath = "Music/",
        displayName = "song.mp3",
        dateModifiedEpochSeconds = 1L,
    )

    private fun scanned() = ScannedLocalTrack(
        mediaStoreId = 1L,
        contentUri = "content://audio/1",
        title = "Song",
        normalizedTitle = "song",
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
