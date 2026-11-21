package dev.behradhz.meowzix.feature.telegramauth

/** -1 means the live TDLib step; null means leave the Telegram destination. */
internal fun telegramPreviousStepIndex(currentIndex: Int, historySize: Int): Int? = when {
    currentIndex > 0 -> currentIndex - 1
    currentIndex == 0 -> null
    historySize > 0 -> historySize - 1
    else -> null
}
