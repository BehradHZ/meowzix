package dev.behradhz.meowzix.domain.telegram

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

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
            val value = secret.trim()
            require(value.length in 32..512 && value.length % 2 == 0 &&
                value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "MTProto secret must be an even-length hexadecimal value (at least 32 characters)."
            }
            return copy(server = host, secret = value.lowercase())
        }
        return copy(server = host)
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

data class TelegramProxyState(
    val proxies: List<TelegramProxyItem> = emptyList(),
    val isLoading: Boolean = false,
    val isChanging: Boolean = false,
    val errorMessage: String? = null,
)

enum class TelegramConnectionStatus {
    STARTING, WAITING_FOR_NETWORK, CONNECTING_TO_PROXY, CONNECTING, UPDATING, CONNECTED
}

/** Reads only Telegram's documented proxy links, never arbitrary URLs or clipboard content. */
object TelegramProxyLinkParser {
    private val candidate = Regex(
        """(?i)(?:tg://|https?://|(?:www\.)?(?:t\.me|telegram\.me)/)(?:proxy|socks)\?[^\s<>"']+""",
    )

    fun findIn(text: String): TelegramProxyDraft? =
        candidate.find(text)?.value?.let(::parse)

    fun parse(text: String): TelegramProxyDraft? = runCatching {
        val value = text.trim().trimEnd('.', ',', ')', ']', '}')
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
