package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueItemOrigin
import dev.behradhz.meowzix.domain.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMergeRemapTest {
    @Test
    fun persistedQueueCanonicalizesMergedIdsAndTelegramTrackQuery() {
        val oldId = "11111111-1111-1111-1111-111111111111"
        val survivor = "22222222-2222-2222-2222-222222222222"
        val session = PersistedPlaybackSession(
            items = listOf(
                PersistedPlaybackItem(
                    mediaId = oldId,
                    uri = "meowzix-tdlib://audio/99?trackId=$oldId",
                    title = "Track",
                    artist = null,
                    artworkUri = null,
                    durationMs = 1_000L,
                ),
            ),
            currentIndex = 0,
            positionMs = 123L,
            playbackMode = PlaybackMode.ORDERED,
            repeatMode = RepeatMode.OFF,
            logicalMediaIds = listOf(oldId),
            logicalOrigins = listOf(QueueItemOrigin.MANUAL),
            logicalCurrentIndex = 0,
            materializedEndExclusive = 1,
        )

        val mapped = remapPersistedPlaybackSession(session, mapOf(oldId to survivor))

        assertEquals(survivor, mapped.items.single().mediaId)
        assertEquals("meowzix-tdlib://audio/99?trackId=$survivor", mapped.items.single().uri)
        assertEquals(listOf(survivor), mapped.logicalMediaIds)
        assertEquals(session.positionMs, mapped.positionMs)
    }
}
