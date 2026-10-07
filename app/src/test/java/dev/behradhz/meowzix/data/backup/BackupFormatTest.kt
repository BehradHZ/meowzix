package dev.behradhz.meowzix.data.backup

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {
    @Test
    fun roundTripPreservesNullEmptyUnicodeAndDelimiters() {
        val records = listOf(
            BackupRecord("TRACK", listOf(null, "", "سلام|hello", "line\n2")),
            BackupRecord("SETTING", listOf("theme", "DARK")),
        )
        val decoded = BackupFormat.decode(BackupFormat.encode("backup-1", 123L, false, records))
        assertEquals(BackupFormat.VERSION, decoded.version)
        assertEquals("backup-1", decoded.backupId)
        assertEquals(records, decoded.records)
    }

    @Test
    fun legacyVersionOneRemainsDecodable() {
        val current = BackupFormat.encode(
            backupId = "legacy-backup",
            createdAtEpochMs = 123L,
            includeHistory = true,
            records = listOf(BackupRecord("SETTING", listOf("theme", "DARK"))),
        ).toString(Charsets.UTF_8)
        val legacyBody = current.substringAfter('\n')
            .replace(
                "MEOWZIX_BACKUP|${BackupFormat.VERSION}|",
                "MEOWZIX_BACKUP|${BackupFormat.LEGACY_VERSION}|",
            )
        val decoded = BackupFormat.decode(withChecksum(legacyBody))
        assertEquals(BackupFormat.LEGACY_VERSION, decoded.version)
        assertEquals(true, decoded.includeHistory)
    }

    @Test
    fun tamperingIsRejected() {
        val bytes = BackupFormat.encode("backup-1", 123L, false, listOf(BackupRecord("TRACK", listOf("x"))))
        val changed = bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(changed) }
    }

    @Test
    fun malformedFieldIsRejected() {
        val body = "MEOWZIX_BACKUP|${BackupFormat.VERSION}|backup-1|123|0\nSETTING|not-base64\n"
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(withChecksum(body)) }
    }

    @Test
    fun invalidHistoryFlagIsRejected() {
        val body = "MEOWZIX_BACKUP|${BackupFormat.VERSION}|backup-1|123|yes\n"
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(withChecksum(body)) }
    }

    @Test
    fun oversizedInputIsRejectedBeforeParsing() {
        val bytes = ByteArray(BackupFormat.MAX_BYTES + 1)
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(bytes) }
    }

    @Test
    fun unknownVersionIsRejectedEvenWithValidChecksum() {
        val body = "MEOWZIX_BACKUP|99|backup-1|123|0\n"
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(withChecksum(body)) }
    }

    private fun withChecksum(body: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SHA256|$digest\n$body".toByteArray()
    }
}
