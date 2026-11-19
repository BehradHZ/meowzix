package dev.behradhz.meowzix.data.repository

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeRecommendationSnapshotCodecTest {
    @Test fun roundTripPreservesOrderAndAcceptsEmptySnapshot() {
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        assertEquals(ids, HomeRecommendationSnapshotCodec.decode(HomeRecommendationSnapshotCodec.encode(ids)))
        assertEquals(emptyList<UUID>(), HomeRecommendationSnapshotCodec.decode(""))
    }

    @Test fun damagedOrDuplicatedSnapshotsRequireOneScheduledRebuild() {
        val id = UUID.randomUUID()
        assertNull(HomeRecommendationSnapshotCodec.decode(null))
        assertNull(HomeRecommendationSnapshotCodec.decode("invalid-uuid"))
        assertNull(HomeRecommendationSnapshotCodec.decode("$id,$id"))
        assertNull(HomeRecommendationSnapshotCodec.decode(List(15) { UUID.randomUUID() }.joinToString(",")))
    }
}
