package dev.behradhz.meowzix.domain.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramProxyBadgeTest {
    @Test fun disabledProxyCannotClaimConnection() {
        TelegramConnectionStatus.entries.forEach { status ->
            assertEquals(TelegramProxyBadge.DISABLED, telegramProxyBadge(false, status))
        }
    }

    @Test fun enabledProxyShowsTickOnlyWhenTdlibIsConnected() {
        assertEquals(TelegramProxyBadge.CONNECTED, telegramProxyBadge(true, TelegramConnectionStatus.CONNECTED))
        TelegramConnectionStatus.entries.filterNot { it == TelegramConnectionStatus.CONNECTED }.forEach { status ->
            assertEquals(TelegramProxyBadge.CONNECTING, telegramProxyBadge(true, status))
        }
    }
}
