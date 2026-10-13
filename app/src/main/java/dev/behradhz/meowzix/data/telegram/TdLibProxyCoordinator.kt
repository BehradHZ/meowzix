package dev.behradhz.meowzix.data.telegram

import dev.behradhz.meowzix.domain.telegram.TelegramProxyDraft
import dev.behradhz.meowzix.domain.telegram.TelegramProxyItem
import dev.behradhz.meowzix.domain.telegram.TelegramProxyKind
import dev.behradhz.meowzix.domain.telegram.TelegramProxyLinkParser
import dev.behradhz.meowzix.domain.telegram.TelegramProxyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.TdApi
import kotlin.math.roundToLong

/**
 * The TDLib database is the only store for proxies (including credentials). No duplicate
 * plaintext preferences or logs. All operations are legal before account authorization.
 */
internal class TdLibProxyCoordinator(
    private val scope: CoroutineScope,
    private val activeClient: () -> TdLibClientAdapter?,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow(TelegramProxyState())
    val state: StateFlow<TelegramProxyState> = _state.asStateFlow()

    fun refresh() = scope.launch {
        val client = activeClient() ?: return@launch
        _state.update { it.copy(isLoading = true) }
        try {
            val proxies = withTimeout(12_000) { client.send(TdApi.GetProxies()) }.proxies
            _state.update { previous ->
                val prior = previous.proxies.associateBy { it.id }
                previous.copy(
                    isLoading = false,
                    errorMessage = null,
                    proxies = proxies.map { proxy ->
                        val old = prior[proxy.id]
                        TelegramProxyItem(
                            id = proxy.id,
                            server = proxy.server,
                            port = proxy.port,
                            kind = when (proxy.type) {
                                is TdApi.ProxyTypeMtproto -> TelegramProxyKind.MTPROTO
                                is TdApi.ProxyTypeSocks5 -> TelegramProxyKind.SOCKS5
                                else -> TelegramProxyKind.HTTP
                            },
                            enabled = proxy.isEnabled,
                            latencyMs = old?.latencyMs,
                            testing = old?.testing ?: false,
                            pingError = old?.pingError,
                        )
                    },
                )
            }
        } catch (_: TimeoutCancellationException) {
            _state.update { it.copy(isLoading = false, errorMessage = "Proxy list timed out. Retry or reset Telegram.") }
        } catch (cancelled: CancellationException) {
            _state.update { it.copy(isLoading = false) }
            throw cancelled
        } catch (_: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = "Unable to read Telegram proxies. Retry or reset the Telegram connection.") }
        }
    }

    fun importLink(value: String) {
        val draft = TelegramProxyLinkParser.findIn(value)
        if (draft == null) {
            _state.update { it.copy(errorMessage = "Paste a valid t.me/proxy, t.me/socks or tg:// proxy link.") }
        } else add(draft)
    }

    fun add(draft: TelegramProxyDraft) = change {
        val valid = draft.validated()
        val type = when (valid.kind) {
            TelegramProxyKind.MTPROTO -> TdApi.ProxyTypeMtproto(valid.secret)
            TelegramProxyKind.SOCKS5 -> TdApi.ProxyTypeSocks5(valid.username, valid.password)
            TelegramProxyKind.HTTP -> TdApi.ProxyTypeHttp(valid.username, valid.password, false)
        }
        // Explicit selection, never switch the live route merely by pasting a link.
        send(TdApi.AddProxy(valid.server, valid.port, false, type))
    }

    fun enable(id: Int) = change { send(TdApi.EnableProxy(id)) }
    fun disable() = change { send(TdApi.DisableProxy()) }
    fun remove(id: Int) = change { send(TdApi.RemoveProxy(id)) }
    fun clearError() { _state.update { it.copy(errorMessage = null) } }

    fun ping(id: Int) = scope.launch {
        val client = activeClient() ?: run {
            _state.update { it.copy(errorMessage = "Telegram is starting. Try again shortly.") }
            return@launch
        }
        if (id != 0 && _state.value.proxies.none { it.id == id }) return@launch
        if (id != 0 && _state.value.proxies.first { it.id == id }.testing) return@launch
        _state.update { current -> current.copy(proxies = current.proxies.map {
            if (it.id == id) it.copy(testing = true, latencyMs = null, pingError = null) else it
        }) }
        try {
            // TDLib sends a request to Telegram *through* this proxy; this is not ICMP.
            val seconds = withTimeout(15_000) { client.send(TdApi.PingProxy(id)) }.value
            val latency = (seconds * 1_000.0).roundToLong().coerceAtLeast(0)
            _state.update { current -> current.copy(
                errorMessage = if (id == 0) "Direct connection: ${latency} ms" else current.errorMessage,
                proxies = current.proxies.map { if (it.id == id) it.copy(testing = false, latencyMs = latency, pingError = null) else it },
            ) }
        } catch (_: TimeoutCancellationException) {
            _state.update { current -> current.copy(
                errorMessage = if (id == 0) "Direct connection test timed out." else current.errorMessage,
                proxies = current.proxies.map { if (it.id == id) it.copy(testing = false, latencyMs = null, pingError = "Timed out") else it },
            ) }
        } catch (cancelled: CancellationException) {
            _state.update { current -> current.copy(proxies = current.proxies.map { if (it.id == id) it.copy(testing = false) else it }) }
            throw cancelled
        } catch (_: Throwable) {
            _state.update { current -> current.copy(
                errorMessage = if (id == 0) "Direct connection test failed." else current.errorMessage,
                proxies = current.proxies.map { if (it.id == id) it.copy(testing = false, latencyMs = null, pingError = "Unreachable or timed out") else it },
            ) }
        }
    }

    private fun change(action: suspend TdLibClientAdapter.() -> Unit) = scope.launch {
        lock.withLock {
            val client = activeClient() ?: run {
                _state.update { it.copy(errorMessage = "Telegram is starting. Try again shortly.") }
                return@withLock
            }
            _state.update { it.copy(isChanging = true, errorMessage = null) }
            try {
                withTimeout(15_000) { client.action() }
                val result = withTimeout(12_000) { client.send(TdApi.GetProxies()) }.proxies
                _state.update { old ->
                    old.copy(
                        isChanging = false,
                        proxies = result.map { p ->
                            TelegramProxyItem(
                                id = p.id, server = p.server, port = p.port,
                                kind = when (p.type) {
                                    is TdApi.ProxyTypeMtproto -> TelegramProxyKind.MTPROTO
                                    is TdApi.ProxyTypeSocks5 -> TelegramProxyKind.SOCKS5
                                    else -> TelegramProxyKind.HTTP
                                },
                                enabled = p.isEnabled,
                                latencyMs = old.proxies.firstOrNull { it.id == p.id }?.latencyMs,
                            )
                        },
                    )
                }
            } catch (_: TimeoutCancellationException) {
                _state.update { it.copy(isChanging = false, errorMessage = "Proxy request timed out. Check the connection and retry.") }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(isChanging = false) }
                throw cancelled
            } catch (error: IllegalArgumentException) {
                _state.update { it.copy(isChanging = false, errorMessage = error.message ?: "Invalid proxy details.") }
            } catch (_: Throwable) {
                // Do not render TDLib's raw exception: it may embed proxy credentials.
                _state.update { it.copy(isChanging = false, errorMessage = "Proxy operation failed. Check the server or try again.") }
            }
        }
    }
}
