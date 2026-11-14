package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchPlaybackQueueTest {
    private val ids = List(1091) { index -> UUID.nameUUIDFromBytes("library-$index".toByteArray()) }

    @Test
    fun `search selection plays every library track in sorted cyclic order`() {
        val clicked = ids[700]
        val queue = buildLibraryPlaybackQueue(ids, clicked, PlaybackMode.ORDERED, fromSearch = true)

        assertEquals(ids.size, queue.size)
        assertEquals(clicked, queue.first())
        assertEquals(ids.drop(700) + ids.take(700), queue)
        assertEquals(ids.toSet(), queue.toSet())
    }

    @Test
    fun `search does not reduce shuffle candidates`() {
        for (mode in listOf(PlaybackMode.PURE_SHUFFLE, PlaybackMode.SMART_SHUFFLE)) {
            assertEquals(ids, buildLibraryPlaybackQueue(ids, ids[700], mode, fromSearch = true))
        }
    }

    @Test
    fun `normal Library selection also reaches the complete library after wrapping`() {
        val ordered = buildLibraryPlaybackQueue(ids, ids[700], PlaybackMode.ORDERED, fromSearch = false)
        assertEquals(ids.size, ordered.size)
        assertEquals(ids[700], ordered.first())
        assertEquals(ids.drop(700) + ids.take(700), ordered)
        assertEquals(ids.size, ordered.distinct().size)
    }

    @Test
    fun `missing selection remains first without discarding the rest of Library`() {
        val missing = UUID.randomUUID()
        assertEquals(listOf(missing) + ids, buildLibraryPlaybackQueue(ids, missing, PlaybackMode.ORDERED, fromSearch = true))
        assertEquals(listOf(missing), buildLibraryPlaybackQueue(emptyList(), missing, PlaybackMode.ORDERED, fromSearch = true))
        for (mode in listOf(PlaybackMode.PURE_SHUFFLE, PlaybackMode.SMART_SHUFFLE)) {
            assertEquals(listOf(missing) + ids, buildLibraryPlaybackQueue(ids, missing, mode, fromSearch = false))
        }
    }

    @Test
    fun `duplicate source ids never create duplicate queue entries`() {
        val duplicated = listOf(ids[0], ids[1], ids[0], ids[2])
        assertEquals(listOf(ids[1], ids[2], ids[0]), buildLibraryPlaybackQueue(duplicated, ids[1], PlaybackMode.ORDERED, fromSearch = true))
    }
}
