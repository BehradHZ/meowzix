package dev.behradhz.meowzix.domain.telegram

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class TelegramProxyKind { MTPROTO, SOCKS5, HTTP }

data class TelegramProxyDraft(
    val kind: TelegramProxyKind,
    val server: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val secret: String = "",
) {
    fun validated(): TelegramProxyDraft {
        val host = server.trim()
        require(host.isNotEmpty() && host.length <= 253 &&
            host.none { it.isWhitespace() || it in "/?#@\\" } &&
            !host.contains("..")) { "Enter a valid proxy server." }
        require(port in 1..65535) { "Proxy port must be between 1 and 65535." }
        require(username.length <= 256 && password.length <= 256) { "Proxy credentials are too long." }
        if (kind == TelegramProxyKind.MTPROTO) {
            return copy(server = host, secret = normalizeMtprotoSecret(secret))
        }
        return copy(server = host)
    }
}

/** TDLib receives canonical hexadecimal secrets; Telegram links can also carry Base64URL. */
private fun normalizeMtprotoSecret(raw: String): String {
    val value = raw.trim()
    val isHex = value.length in 32..512 && value.length % 2 == 0 &&
        value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    if (isHex) return value.lowercase()

    // Base64 (both URL-safe and standard, padded or unpadded) represents the same bytes.
    // Bound the input before decoding; do not include credentials in validation errors.
    val encoded = value.takeIf {
        it.length in 22..344 && it.matches(Regex("[A-Za-z0-9+/_-]+={0,2}"))
    }
    val bytes = encoded?.let {
        runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull()
            ?: runCatching { Base64.getDecoder().decode(it) }.getOrNull()
    }
    require(bytes != null && bytes.size in 16..256) {
        "MTProto secret must be hexadecimal or Base64 (at least 16 bytes)."
    }
    val digits = "0123456789abcdef"
    return buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            append(digits[unsigned ushr 4])
            append(digits[unsigned and 0x0f])
        }
    }
}

data class TelegramProxyItem(
    val id: Int,
    val kind: TelegramProxyKind,
    val server: String,
    val port: Int,
    val enabled: Boolean,
    val latencyMs: Long? = null,
    val testing: Boolean = false,
    val pingError: String? = null,
)

/** Ready measurements rank first, then ascending latency. Unmeasured/unreachable entries follow. */
fun rankTelegramProxiesByLatency(proxies: List<TelegramProxyItem>): List<TelegramProxyItem> =
    proxies.sortedWith(compareBy<TelegramProxyItem> { it.latencyMs == null }
        .thenBy { it.latencyMs ?: Long.MAX_VALUE })

/** Endpoint-only comparison for suppressing already-saved clipboard suggestions. */
fun TelegramProxyItem.matchesEndpoint(draft: TelegramProxyDraft): Boolean =
    kind == draft.kind && port == draft.port && server.equals(draft.server, ignoreCase = true)

data class TelegramProxyState(
    val proxies: List<TelegramProxyItem> = emptyList(),
    val isLoading: Boolean = false,
    val isChanging: Boolean = false,
    val errorMessage: String? = null,
    val directLatencyMs: Long? = null,
    val directTesting: Boolean = false,
    val directPingError: String? = null,
)

enum class TelegramConnectionStatus {
    STARTING, WAITING_FOR_NETWORK, CONNECTING_TO_PROXY, CONNECTING, UPDATING, CONNECTED
}

enum class TelegramProxyBadge { DISABLED, CONNECTING, CONNECTED }

/** TDLib transport status, never inferred from a saved proxy or a successful ping. */
fun telegramProxyConnectionLabel(usingProxy: Boolean, status: TelegramConnectionStatus): String = when (status) {
    TelegramConnectionStatus.STARTING -> "Starting Telegram…"
    TelegramConnectionStatus.WAITING_FOR_NETWORK -> "Waiting for network"
    TelegramConnectionStatus.CONNECTING_TO_PROXY -> "Connecting to proxy…"
    TelegramConnectionStatus.CONNECTING -> if (usingProxy) "Connecting via proxy…" else "Connecting directly…"
    TelegramConnectionStatus.UPDATING -> "Syncing Telegram…"
    TelegramConnectionStatus.CONNECTED -> if (usingProxy) "Connected via proxy" else "Connected directly"
}

/** A selected proxy is not necessarily connected. Trust the TDLib transport state. */
fun telegramProxyBadge(enabled: Boolean, status: TelegramConnectionStatus): TelegramProxyBadge = when {
    !enabled -> TelegramProxyBadge.DISABLED
    status == TelegramConnectionStatus.CONNECTED -> TelegramProxyBadge.CONNECTED
    else -> TelegramProxyBadge.CONNECTING
}

/** Reads only Telegram's documented proxy links, never arbitrary URLs or clipboard content. */
object TelegramProxyLinkParser {
    private val candidate = Regex(
        """(?i)(?<![\w@./-])(?:tg://(?:proxy|socks)|(?:(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me)/(?:proxy|socks)))\?[^\s<>"']+""",
    )

    // Clipboard and pasted messages may contain several links: skip malformed ones.
    fun findIn(text: String): TelegramProxyDraft? =
        candidate.findAll(text).firstNotNullOfOrNull { parse(it.value) }

    fun parse(text: String): TelegramProxyDraft? = runCatching {
        val value = text.trim().replace("\\&", "&").trimEnd('.', ',', ')', ']', '}')
        val url = if (value.startsWith("tg://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)) value else "https://$value"
        val uri = URI(url)
        val kind = when {
            uri.scheme.equals("tg", ignoreCase = true) -> when (uri.host?.lowercase()) {
                "proxy" -> TelegramProxyKind.MTPROTO
                "socks" -> TelegramProxyKind.SOCKS5
                else -> return null
            }
            uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true) -> {
                if (uri.host?.lowercase() !in setOf("t.me", "www.t.me", "telegram.me", "www.telegram.me")) return null
                when (uri.path?.lowercase()) {
                    "/proxy" -> TelegramProxyKind.MTPROTO
                    "/socks" -> TelegramProxyKind.SOCKS5
                    else -> return null
                }
            }
            else -> return null
        }
        val parameters = uri.rawQuery?.split('&')?.mapNotNull {
            val index = it.indexOf('=')
            if (index < 0) null else decode(it.substring(0, index)).lowercase() to
                decode(it.substring(index + 1))
        }?.toMap() ?: return null
        val server = parameters["server"] ?: return null
        val port = parameters["port"]?.toIntOrNull() ?: return null
        TelegramProxyDraft(
            kind = kind,
            server = server,
            port = port,
            username = parameters["user"].orEmpty(),
            password = parameters["pass"].orEmpty(),
            secret = parameters["secret"].orEmpty(),
        ).validated()
    }.getOrNull()

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}
