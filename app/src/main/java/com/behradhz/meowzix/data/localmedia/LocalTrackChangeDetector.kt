package com.behradhz.meowzix.data.localmedia

import com.behradhz.meowzix.core.model.SourceAvailability
import com.behradhz.meowzix.core.model.TrackSourceType
import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity
import com.behradhz.meowzix.data.db.entity.TrackEntity
import com.behradhz.meowzix.data.db.entity.TrackSourceEntity

object LocalTrackChangeDetector {
    fun trackChanged(existing: TrackEntity, scanned: ScannedLocalTrack): Boolean =
        existing.title != scanned.title ||
            existing.normalizedTitle != scanned.normalizedTitle ||
            existing.artist != scanned.artist ||
            existing.normalizedArtist != scanned.normalizedArtist ||
            existing.album != scanned.album ||
            existing.durationMs != scanned.durationMs ||
            existing.trackNumber != scanned.trackNumber ||
            existing.year != scanned.year ||
            existing.artworkRef != scanned.artworkRef

    fun sourceChanged(existing: TrackSourceEntity, scanned: ScannedLocalTrack): Boolean =
        existing.type != TrackSourceType.LOCAL_MEDIASTORE ||
            existing.availability != SourceAvailability.AVAILABLE_LOCAL ||
            existing.contentUri != scanned.contentUri ||
            existing.localPath != null ||
            existing.mimeType != scanned.mimeType ||
            existing.fileSizeBytes != scanned.fileSizeBytes ||
            existing.contentHashSha256 != null ||
            !existing.trainingEligible

    fun localSourceChanged(existing: LocalMediaSourceEntity, scanned: ScannedLocalTrack): Boolean =
        existing.mediaStoreId != scanned.mediaStoreId ||
            existing.contentUri != scanned.contentUri ||
            existing.relativePath != scanned.relativePath ||
            existing.displayName != scanned.displayName ||
            existing.dateModifiedEpochSeconds != scanned.dateModifiedEpochSeconds
}
