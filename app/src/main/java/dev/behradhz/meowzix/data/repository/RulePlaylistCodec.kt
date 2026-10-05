package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import java.nio.charset.StandardCharsets
import java.util.Base64

/** Versioned, bounded persistence format. It stores typed rules, never executable SQL. */
object RulePlaylistCodec {
    private const val VERSION = "v1"
    private const val MAX_RULES = 32
    private const val MAX_VALUE_BYTES = 512

    fun encode(rules: List<PlaylistRule>): String = buildString {
        appendLine(VERSION)
        rules.take(MAX_RULES).forEach { rule ->
            val encodedValue = rule.value
                ?.toByteArray(StandardCharsets.UTF_8)
                ?.takeIf { it.size <= MAX_VALUE_BYTES }
                ?.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
                .orEmpty()
            append(rule.kind.name).append('\t').append(encodedValue).append('\n')
        }
    }

    fun decode(payload: String): List<PlaylistRule> {
        val lines = payload.lineSequence().toList()
        if (lines.firstOrNull() != VERSION) return emptyList()
        return lines.drop(1).take(MAX_RULES).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t', limit = 2)
            val kind = runCatching { RuleKind.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val value = parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { encoded ->
                runCatching {
                    Base64.getUrlDecoder().decode(encoded)
                        .takeIf { it.size <= MAX_VALUE_BYTES }
                        ?.toString(StandardCharsets.UTF_8)
                }.getOrNull()
            }
            PlaylistRule(kind, value)
        }
    }
}
