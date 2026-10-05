package dev.behradhz.meowzix.core.common

import java.text.Normalizer

/** Shared normalization for persisted search keys and user queries. */
object TextNormalizer {
    private val combiningMarks = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val whitespace = Regex("\\s+")

    fun normalize(value: String?): String? {
        if (value == null) return null
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        val out = buildString(normalized.length) {
            normalized.forEach { ch ->
                when (ch) {
                    '\u064A', '\u0649' -> append('\u06CC') // Arabic Yeh/Alef Maksura -> Persian Yeh
                    '\u0643' -> append('\u06A9') // Arabic Kaf -> Persian Keheh
                    '\u0640' -> Unit // Tatweel
                    '\u200C', '\u200D', '\u00A0' -> append(' ') // ZWNJ/ZWJ/NBSP are token boundaries
                    in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
                    in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
                    else -> append(ch)
                }
            }
        }
            .replace(combiningMarks, "")
            .lowercase()
            .trim()
            .replace(whitespace, " ")
        return out.takeIf(String::isNotBlank)
    }
}
