package dev.behradhz.meowzix.data.telegram

/** Converts Iranian domestic mobile numbers to the international format expected by TDLib. */
internal fun normalizeTelegramPhoneNumber(input: String): String {
    val number = buildString {
        input.trim().forEach { char ->
            when {
                char.isWhitespace() || char == '-' || char == '(' || char == ')' -> Unit
                char.isDigit() -> append('0' + char.digitToInt())
                else -> append(char)
            }
        }
    }
    return if (number.length == 11 && number.startsWith("09") && number.all { it in '0'..'9' }) {
        "+98${number.drop(1)}"
    } else {
        number
    }
}
