package dev.behradhz.meowzix.core.common

import java.text.Normalizer

object TextNormalizer {
    fun normalize(value: String?): String? = value
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotBlank() }
}
