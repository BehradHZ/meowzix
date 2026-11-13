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

    @Test fun `empty ranking keeps eligible collection order`() {
        val ids = (0..4).map { UUID.nameUUIDFromBytes("song-$it".toByteArray()) }
        assertEquals(
            listOf(ids[1], ids[3], ids[4]),
            SmartShuffleFutureOrder.order(ids.drop(1), emptyList(), setOf(ids[1], ids[3], ids[4])),
        )
    }
}
