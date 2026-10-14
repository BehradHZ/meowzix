package dev.behradhz.meowzix.feature.queue

import dev.behradhz.meowzix.domain.playback.QueueItem
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueEntryIndexTest {
    @Test
    fun `duplicate tracks keep different original indices after filtering`() {
        val duplicateId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        val items = listOf(
            QueueItem(duplicateId, "Repeated", null, null),
            QueueItem(otherId, "Other", null, null),
            QueueItem(duplicateId, "Repeated", null, null),
        )

        val filtered = indexedQueueItems(items) { it.id == duplicateId }

        assertEquals(listOf(0, 2), filtered.map { it.index })
        assertEquals(listOf(duplicateId, duplicateId), filtered.map { it.value.id })
    }

    @Test
    fun `reordering a displayed duplicate preserves the intended source index`() {
        val repeated = UUID.randomUUID()
        val items = listOf(
            QueueItem(repeated, "Repeated", null, null),
            QueueItem(UUID.randomUUID(), "Other", null, null),
            QueueItem(repeated, "Repeated", null, null),
        )
        val rows = indexedQueueItems(items) { true }.toMutableList()
        val moved = rows.removeAt(2)
        rows.add(1, moved)

        assertEquals(2, moved.index)
        assertEquals(1, rows.indexOfFirst { it.index == moved.index })
    }
}
