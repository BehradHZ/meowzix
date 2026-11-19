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

    @Test fun connectionLabelsDescribeTheActualTransportState() {
        assertEquals("Connecting to proxy…",
            telegramProxyConnectionLabel(true, TelegramConnectionStatus.CONNECTING_TO_PROXY))
        assertEquals("Connecting via proxy…",
            telegramProxyConnectionLabel(true, TelegramConnectionStatus.CONNECTING))
        assertEquals("Connected via proxy",
            telegramProxyConnectionLabel(true, TelegramConnectionStatus.CONNECTED))
        assertEquals("Connected directly",
            telegramProxyConnectionLabel(false, TelegramConnectionStatus.CONNECTED))
        assertEquals("Waiting for network",
            telegramProxyConnectionLabel(true, TelegramConnectionStatus.WAITING_FOR_NETWORK))
        assertEquals("Syncing Telegram…",
            telegramProxyConnectionLabel(true, TelegramConnectionStatus.UPDATING))
    }
}
