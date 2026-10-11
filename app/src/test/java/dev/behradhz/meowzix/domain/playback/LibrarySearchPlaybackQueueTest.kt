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
    fun `non search library playback retains existing ordered tail behavior`() {
        assertEquals(ids.drop(700), buildLibraryPlaybackQueue(ids, ids[700], PlaybackMode.ORDERED, fromSearch = false))
    }

    @Test
    fun `missing selection falls back safely to requested track`() {
        val missing = UUID.randomUUID()
        assertEquals(listOf(missing), buildLibraryPlaybackQueue(ids, missing, PlaybackMode.ORDERED, fromSearch = true))
        assertEquals(listOf(missing), buildLibraryPlaybackQueue(emptyList(), missing, PlaybackMode.ORDERED, fromSearch = true))
    }

    @Test
    fun `duplicate source ids never create duplicate queue entries`() {
        val duplicated = listOf(ids[0], ids[1], ids[0], ids[2])
        assertEquals(listOf(ids[1], ids[2], ids[0]), buildLibraryPlaybackQueue(duplicated, ids[1], PlaybackMode.ORDERED, fromSearch = true))
    }
}
