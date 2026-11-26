package dev.behradhz.meowzix.feature.telegram

import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.forwardTimestampSnapshot
import dev.behradhz.meowzix.domain.playback.livePosition
import dev.behradhz.meowzix.domain.telegram.formatTelegramMediaTimestamp
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ForwardTimestampTest {
    @Test fun formattingUsesTelegramMediaTimecode() {
        assertEquals("0:00", formatTelegramMediaTimestamp(0))
        assertEquals("1:42", formatTelegramMediaTimestamp(102))
        assertEquals("1:02:03", formatTelegramMediaTimestamp(3723))
    }

    @Test fun notificationQueueIdentityIsNotThePlaybackInstanceIdentity() {
        val track = UUID.randomUUID()
        val queue = UUID.randomUUID()
        val playback = UUID.randomUUID()
        val state = PlaybackState(
            currentTrack = NowPlayingTrack(track, "Song", null, null),
            positionMs = 120000,
            playbackOccurrenceId = playback,
            queueOccurrenceId = queue,
        )
        val notification = dev.behradhz.meowzix.domain.playback.ForwardTimestampSnapshot(100000, null, queue)
        assertEquals(120000L, notification.livePosition(state, track))
        assertNull(notification.livePosition(state.copy(queueOccurrenceId = UUID.randomUUID()), track))
        val bound = requireNotNull(state.forwardTimestampSnapshot(track))
        assertNull(bound.livePosition(state.copy(playbackOccurrenceId = UUID.randomUUID()), track))
    }

    @Test fun capturesPositionAndFollowsOnlyTheOriginalOccurrence() {
        val id = UUID.randomUUID()
        val occurrence = UUID.randomUUID()
        val state = PlaybackState(
            currentTrack = NowPlayingTrack(id, "Song", null, null),
            positionMs = 102345,
            durationMs = 180000,
            playbackOccurrenceId = occurrence,
        )
        val snapshot = requireNotNull(state.forwardTimestampSnapshot(id))
        assertEquals(102345L, snapshot.positionMs)
        assertEquals(110000L, snapshot.livePosition(state.copy(positionMs = 110000), id))
        assertEquals(180000L, snapshot.livePosition(state.copy(positionMs = 190000), id))
        assertNull(snapshot.livePosition(state.copy(playbackOccurrenceId = UUID.randomUUID()), id))
        assertNull(snapshot.livePosition(state.copy(currentTrack = NowPlayingTrack(UUID.randomUUID(), "Other", null, null)), id))
        assertNull(state.forwardTimestampSnapshot(UUID.randomUUID()))
    }
}
