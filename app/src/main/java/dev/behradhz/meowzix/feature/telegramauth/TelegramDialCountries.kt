package dev.behradhz.meowzix.feature.telegramauth

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.util.Locale

/** Region IDs are distinct even when several countries share the same calling code. */
internal data class TelegramDialCountry(
    val region: String,
    val name: String,
    val englishName: String,
    val dialCode: String,
) {
    val flag: String
        get() = region.uppercase(Locale.ROOT).map { letter ->
            String(Character.toChars(0x1F1E6 + (letter - 'A')))
        }.joinToString("")
}

internal fun telegramDialCountries(locale: Locale = Locale.getDefault()): List<TelegramDialCountry> {
    val phoneUtil = PhoneNumberUtil.getInstance()
    return phoneUtil.supportedRegions
        .asSequence()
        .filter { it.length == 2 && it.all(Char::isLetter) }
        .map { region ->
            val localCountry = Locale("", region)
            TelegramDialCountry(
                region = region,
                name = localCountry.getDisplayCountry(locale),
                englishName = localCountry.getDisplayCountry(Locale.ENGLISH),
                dialCode = "+${phoneUtil.getCountryCodeForRegion(region)}",
            )
        }
        .sortedWith(compareBy<TelegramDialCountry> { it.name.lowercase(locale) }.thenBy { it.region })
        .toList()
}

internal fun searchTelegramCountries(
    countries: List<TelegramDialCountry>,
    query: String,
): List<TelegramDialCountry> {
    val text = query.trim().lowercase(Locale.ROOT)
    if (text.isEmpty()) return countries
    val digits = asciiDigits(text.filter { it.isDigit() })
    return countries.filter { country ->
        country.name.lowercase(Locale.ROOT).contains(text) ||
            country.englishName.lowercase(Locale.ROOT).contains(text) ||
            country.region.lowercase(Locale.ROOT).contains(text) ||
            (digits.isNotEmpty() && country.dialCode.drop(1).startsWith(digits))
    }
}

/** National input only; the country picker provides the international prefix. */
internal fun telegramInternationalPhone(
    country: TelegramDialCountry,
    nationalInput: String,
): String? {
    val national = asciiDigits(
        nationalInput.filterNot { it.isWhitespace() || it == '-' || it == '(' || it == ')' },
    )
    if (national.isEmpty() || !national.all { it in '0'..'9' }) return null
    val util = PhoneNumberUtil.getInstance()
    return runCatching {
        val parsed = util.parse(national, country.region)
        if (!util.isPossibleNumber(parsed) ||
            parsed.countryCode != util.getCountryCodeForRegion(country.region)
        ) null else util.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
    }.getOrNull()
}

/** Convert Persian/Arabic decimal digits to ASCII, not to locale-dependent glyphs. */
private fun asciiDigits(text: String): String = buildString {
    text.forEach { char ->
        append(if (char.isDigit()) ('0' + char.digitToInt()) else char)
    }
}
