package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartShuffleFutureOrderTest {
    @Test fun `ranked prefix is followed by all unscored eligible tracks exactly once`() {
        val ids = (0..8).map { UUID.nameUUIDFromBytes("track-$it".toByteArray()) }
        val future = ids.drop(1)
        val eligible = setOf(ids[1], ids[2], ids[3], ids[5], ids[6], ids[7])
        val ranked = listOf(ids[5], ids[2], ids[5], ids[0], ids[8])
        assertEquals(
            listOf(ids[5], ids[2], ids[1], ids[3], ids[6], ids[7]),
            SmartShuffleFutureOrder.order(future, ranked, eligible),
        )
    }

    @Test fun `Library Smart cycle keeps every candidate even when the ranker returns no eligible set`() {
        val ids = (0..199).map { UUID.nameUUIDFromBytes("smart-library-$it".toByteArray()) }
        val future = ids.drop(1)
        val chosen = listOf(ids[90], ids[25])
        val ranked = SmartShuffleFutureOrder.completeLibraryCycle(
            future, chosen, setOf(ids[25], ids[90]),
        )
        assertEquals(chosen, ranked.take(2))
        assertEquals(future.size, ranked.size)
        assertEquals(future.toSet(), ranked.toSet())
        assertEquals(future, SmartShuffleFutureOrder.completeLibraryCycle(future, emptyList(), emptySet()))
    }

    @Test fun `empty ranking keeps eligible collection order`() {
        val ids = (0..4).map { UUID.nameUUIDFromBytes("song-$it".toByteArray()) }
        assertEquals(
            listOf(ids[1], ids[3], ids[4]),
            SmartShuffleFutureOrder.order(ids.drop(1), emptyList(), setOf(ids[1], ids[3], ids[4])),
        )
    }
}
