package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeletedTelegramSourceEventsTest {
    @Test fun aConfirmedDeletionImmediatelyReachesActivePlayback() = runTest {
        val events = DeletedTelegramSourceEvents()
        val songId = UUID.randomUUID()
        val received = async(start = CoroutineStart.UNDISPATCHED) {
            events.deleted.first()
        }

        events.notifyDeleted(songId)

        assertEquals(songId, received.await())
    }
}
