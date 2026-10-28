package dev.behradhz.meowzix.feature.search

import dev.behradhz.meowzix.core.common.TextNormalizer

/**
 * A global query may match multiple fields of an entity (e.g. album + artist).
 * Blank queries never yield category suggestions.
 */
internal fun matchesGlobalSearch(query: String, vararg fields: String?): Boolean {
    val normalized = TextNormalizer.normalize(query) ?: return false
    val haystack = fields.joinToString(" ") { TextNormalizer.normalize(it).orEmpty() }
    return normalized.split(' ').filter(String::isNotBlank).all(haystack::contains)
}
