package dev.behradhz.meowzix.data.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSafetyTest {
    @Test
    fun unknownSizeKeepsMinimumFreeSpaceFloor() {
        assertEquals(64L * 1024L * 1024L, StorageSafety.requiredFreeBytes(null, copiesNeeded = 2))
    }

    @Test
    fun knownPayloadIncludesCopiesAndSafetyOverhead() {
        val payload = 100L * 1024L * 1024L
        val required = StorageSafety.requiredFreeBytes(payload, copiesNeeded = 2)

        assertEquals(payload * 2 + payload / 10, required)
    }

    @Test
    fun capacityCheckRejectsUnsafeDownload() {
        val payload = 100L * 1024L * 1024L
        val required = StorageSafety.requiredFreeBytes(payload, copiesNeeded = 2)

        assertFalse(StorageSafety.hasCapacity(required - 1, payload, copiesNeeded = 2))
        assertTrue(StorageSafety.hasCapacity(required, payload, copiesNeeded = 2))
    }

    @Test
    fun overflowBecomesConservativeInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, StorageSafety.requiredFreeBytes(Long.MAX_VALUE, copiesNeeded = 2))
    }
}
