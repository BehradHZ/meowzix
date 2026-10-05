package dev.behradhz.meowzix.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFormatTest {
    @Test fun `round trip preserves null empty unicode and delimiters`() {
        val records = listOf(
            BackupRecord("TRACK", listOf(null, "", "سلام|hello", "line\n2")),
            BackupRecord("SETTING", listOf("theme", "DARK")),
        )
        val decoded = BackupFormat.decode(BackupFormat.encode("backup-1", 123L, false, records))
        assertEquals("backup-1", decoded.backupId)
        assertEquals(records, decoded.records)
    }

    @Test fun `tampering is rejected`() {
        val bytes = BackupFormat.encode("backup-1", 123L, false, listOf(BackupRecord("TRACK", listOf("x"))))
        val changed = bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(changed) }
    }

    @Test fun `unknown version is rejected even with valid checksum`() {
        val original = BackupFormat.encode("backup-1", 123L, false, emptyList()).toString(Charsets.UTF_8)
        val body = original.substringAfter('\n').replace("MEOWZIX_BACKUP|1|", "MEOWZIX_BACKUP|99|")
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(body.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(("SHA256|$digest\n$body").toByteArray()) }
    }
}
