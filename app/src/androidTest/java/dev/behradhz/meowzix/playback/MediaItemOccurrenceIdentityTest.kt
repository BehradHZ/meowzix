package dev.behradhz.meowzix.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaItemOccurrenceIdentityTest {
    private val track = PlayableTrack(
        id = UUID.nameUUIDFromBytes("same-music".toByteArray()),
        title = "Same song",
        artist = "Artist",
        album = null,
        durationMs = 120_000L,
        artworkRef = null,
        contentUri = "content://test/track",
    )

    @Test fun duplicateSongMediaItemsKeepDistinctQueueOccurrenceIdentifiers() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val itemA = track.toMediaItem(PlaybackMode.ORDERED, RepeatMode.OFF, first)
        val itemB = track.toMediaItem(PlaybackMode.ORDERED, RepeatMode.OFF, second)
        assertEquals(itemA.mediaId, itemB.mediaId)
        assertEquals(first, itemA.queueOccurrenceId())
        assertEquals(second, itemB.queueOccurrenceId())
        assertNotEquals(itemA.queueOccurrenceId(), itemB.queueOccurrenceId())
    }

    @Test fun changingQueuePlaybackPolicyNeverStripsOccurrenceIdentity() {
        val occurrenceId = UUID.randomUUID()
        val item = track.toMediaItem(PlaybackMode.ORDERED, RepeatMode.OFF, occurrenceId)
        val updated = item.withQueuePolicy(PlaybackMode.SMART_SHUFFLE, RepeatMode.ALL)

        assertEquals(occurrenceId, updated.queueOccurrenceId())
        assertEquals(PlaybackMode.SMART_SHUFFLE, updated.playbackMode())
        assertEquals(RepeatMode.ALL, updated.repeatMode())
        assertEquals(track.id.toString(), updated.mediaId)
    }
}
