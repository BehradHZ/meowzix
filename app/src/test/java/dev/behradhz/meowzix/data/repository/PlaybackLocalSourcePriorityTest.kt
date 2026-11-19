package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLocalSourcePriorityTest {
    private fun source(type: TrackSourceType) = TrackSourceEntity(
        id = "source", trackId = "track", type = type,
        availability = SourceAvailability.AVAILABLE_LOCAL,
        contentUri = null, localPath = null, mimeType = null,
        fileSizeBytes = null, contentHashSha256 = null,
        trainingEligible = false, createdAtEpochMs = 0L, lastVerifiedAtEpochMs = null,
    )

    @Test fun keepsPlaybackAndOfflineReuseOrdering() {
        assertEquals(0, playbackLocalSourcePriority(source(TrackSourceType.APP_OFFLINE_COPY)))
        assertEquals(1, playbackLocalSourcePriority(source(TrackSourceType.TDLIB_LOCAL)))
        assertEquals(2, playbackLocalSourcePriority(source(TrackSourceType.LOCAL_MEDIASTORE)))
        assertEquals(3, playbackLocalSourcePriority(source(TrackSourceType.TELEGRAM_REMOTE)))
    }
}
