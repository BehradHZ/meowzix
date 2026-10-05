package dev.behradhz.meowzix.data.backup

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

data class BackupRecord(val type: String, val fields: List<String?>)
data class DecodedBackup(
    val backupId: String,
    val createdAtEpochMs: Long,
    val includeHistory: Boolean,
    val records: List<BackupRecord>,
)

object BackupFormat {
    const val VERSION = 1
    const val MAX_BYTES = 16 * 1024 * 1024
    const val MAX_RECORDS = 500_000
    const val MAX_FIELD_BYTES = 1 * 1024 * 1024
    private const val MAGIC = "MEOWZIX_BACKUP"

    fun encode(
        backupId: String,
        createdAtEpochMs: Long,
        includeHistory: Boolean,
        records: List<BackupRecord>,
    ): ByteArray {
        require(records.size <= MAX_RECORDS)
        val body = buildString {
            appendLine(listOf(MAGIC, VERSION.toString(), backupId, createdAtEpochMs.toString(), if (includeHistory) "1" else "0").joinToString("|"))
            records.forEach { record ->
                require(record.type.matches(Regex("[A-Z_]{1,32}")))
                append(record.type)
                record.fields.forEach { field -> append('|').append(encodeField(field)) }
                append('\n')
            }
        }.toByteArray(StandardCharsets.UTF_8)
        require(body.size <= MAX_BYTES)
        val digest = sha256(body)
        return ("SHA256|$digest\n".toByteArray(StandardCharsets.UTF_8) + body)
    }

    fun decode(bytes: ByteArray): DecodedBackup {
        require(bytes.size <= MAX_BYTES) { "Backup is too large" }
        val text = bytes.toString(StandardCharsets.UTF_8)
        val firstBreak = text.indexOf('\n')
        require(firstBreak > 0) { "Missing backup checksum" }
        val checksumLine = text.substring(0, firstBreak).removeSuffix("\r")
        require(checksumLine.startsWith("SHA256|")) { "Missing backup checksum" }
        val bodyText = text.substring(firstBreak + 1)
        val bodyBytes = bodyText.toByteArray(StandardCharsets.UTF_8)
        require(sha256(bodyBytes).equals(checksumLine.substringAfter('|'), ignoreCase = true)) { "Backup checksum mismatch" }
        val lines = bodyText.lineSequence().filter(String::isNotEmpty).iterator()
        require(lines.hasNext()) { "Missing backup header" }
        val header = lines.next().removeSuffix("\r").split('|')
        require(header.size == 5 && header[0] == MAGIC && header[1].toIntOrNull() == VERSION) { "Unsupported backup version" }
        val records = ArrayList<BackupRecord>()
        while (lines.hasNext()) {
            require(records.size < MAX_RECORDS) { "Too many backup records" }
            val parts = lines.next().removeSuffix("\r").split('|')
            if (parts.isEmpty() || parts[0].isBlank()) continue
            require(parts[0].matches(Regex("[A-Z_]{1,32}"))) { "Invalid record type" }
            records += BackupRecord(parts[0], parts.drop(1).map(::decodeField))
        }
        return DecodedBackup(
            backupId = header[2],
            createdAtEpochMs = header[3].toLongOrNull() ?: error("Invalid backup timestamp"),
            includeHistory = header[4] == "1",
            records = records,
        )
    }

    private fun encodeField(value: String?): String {
        if (value == null) return "~"
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_FIELD_BYTES) { "Backup field is too large" }
        return "=" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun decodeField(value: String): String? {
        if (value == "~") return null
        require(value.startsWith('=')) { "Invalid backup field" }
        val decoded = Base64.getUrlDecoder().decode(value.substring(1))
        require(decoded.size <= MAX_FIELD_BYTES) { "Backup field is too large" }
        return decoded.toString(StandardCharsets.UTF_8)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
