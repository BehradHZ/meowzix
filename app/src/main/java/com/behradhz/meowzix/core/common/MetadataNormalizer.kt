package com.behradhz.meowzix.core.common

import java.text.Normalizer
import java.util.Locale

object MetadataNormalizer {
    private val whitespace = Regex("\\s+")

    fun display(value: String?): String? = value
        ?.trim()
        ?.replace(whitespace, " ")
        ?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

    fun comparison(value: String?): String? = display(value)
        ?.let { Normalizer.normalize(it, Normalizer.Form.NFKC) }
        ?.lowercase(Locale.ROOT)

    fun title(title: String?, displayName: String?): String {
        val explicitTitle = display(title)
        if (explicitTitle != null) return explicitTitle

        val filename = display(displayName)
            ?.substringBeforeLast('.', missingDelimiterValue = display(displayName).orEmpty())
            ?.trim()

        return filename?.takeIf { it.isNotEmpty() } ?: "Unknown Track"
    }
}
