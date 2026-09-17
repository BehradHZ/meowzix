package dev.behradhz.meowzix.playback.persistence

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSessionCodecTest {
    @Test
    fun `round trip preserves ordered queue and unicode metadata`() {
        val session = PersistedPlaybackSession(
            items = listOf(
                PersistedPlaybackItem(
                    mediaId = "track|1",
                    uri = "content://media/external/audio/media/1",
                    title = "آهنگ\nیک",
                    artist = "Artist | One",
                    artworkUri = null,
                    durationMs = 123_456,
                ),
                PersistedPlaybackItem(
                    mediaId = "track-2",
                    uri = "content://media/external/audio/media/2",
                    title = "Track Two",
                    artist = null,
                    artworkUri = "content://art/2",
                    durationMs = 654_321,
                ),
            ),
            currentIndex = 1,
            positionMs = 42_000,
            playbackMode = PlaybackMode.ORDERED,
            repeatMode = RepeatMode.ALL,
        )

        assertEquals(session, PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session)))
    }

    @Test
    fun `decode clamps an invalid queue index`() {
        val encoded = PlaybackSessionCodec.encode(
            PersistedPlaybackSession(
                items = listOf(
                    PersistedPlaybackItem("id", "uri", "title", null, null, 1),
                ),
                currentIndex = 99,
                positionMs = -1,
                playbackMode = PlaybackMode.ORDERED,
                repeatMode = RepeatMode.OFF,
            ),
        )

        val restored = requireNotNull(PlaybackSessionCodec.decode(encoded))

        assertEquals(0, restored.currentIndex)
        assertEquals(0, restored.positionMs)
    }

    @Test
    fun `decode rejects corrupt state`() {
        assertNull(PlaybackSessionCodec.decode("not-a-session"))
        assertNull(PlaybackSessionCodec.decode("v1|x|0|ORDERED|OFF"))
    }
}
