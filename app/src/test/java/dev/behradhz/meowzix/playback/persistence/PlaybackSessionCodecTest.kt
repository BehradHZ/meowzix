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
            logicalMediaIds = listOf("track-0", "track|1", "track-2", "track-3"),
            logicalCurrentIndex = 2,
            materializedStartIndex = 1,
            materializedEndExclusive = 4,
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
                logicalMediaIds = listOf("id"),
                logicalCurrentIndex = 99,
                materializedEndExclusive = 1,
            ),
        )

        val restored = requireNotNull(PlaybackSessionCodec.decode(encoded))

        assertEquals(0, restored.currentIndex)
        assertEquals(0, restored.logicalCurrentIndex)
        assertEquals(0, restored.positionMs)
    }

    @Test
    fun `decode rejects corrupt state`() {
        assertNull(PlaybackSessionCodec.decode("not-a-session"))
        assertNull(PlaybackSessionCodec.decode("v1|x|0|ORDERED|OFF"))
    }

    @Test
    fun `round trip preserves pure shuffle logical cycle and seed`() {
        val session = PersistedPlaybackSession(
            items = listOf(
                PersistedPlaybackItem("second", "content://2", "Second", null, null, 2),
                PersistedPlaybackItem("first", "content://1", "First", null, null, 1),
            ),
            currentIndex = 1,
            positionMs = 250,
            playbackMode = PlaybackMode.PURE_SHUFFLE,
            repeatMode = RepeatMode.ALL,
            logicalMediaIds = listOf("third", "second", "first", "fourth"),
            logicalCurrentIndex = 2,
            materializedStartIndex = 1,
            materializedEndExclusive = 4,
            shuffleSeed = 8675309,
        )

        assertEquals(session, PlaybackSessionCodec.decode(PlaybackSessionCodec.encode(session)))
    }

    @Test
    fun `legacy v1 sessions upgrade to a logical queue`() {
        val legacy = """
            v1|1|250|ORDERED|OFF
            i|Zmlyc3Q|Y29udGVudDovLzE|Rmlyc3Q|-|-|1
            i|c2Vjb25k|Y29udGVudDovLzI|U2Vjb25k|-|-|2
        """.trimIndent()

        val restored = requireNotNull(PlaybackSessionCodec.decode(legacy))

        assertEquals(listOf("first", "second"), restored.logicalMediaIds)
        assertEquals(1, restored.logicalCurrentIndex)
        assertEquals(2, restored.materializedEndExclusive)
    }
}
