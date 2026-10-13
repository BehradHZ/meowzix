package dev.behradhz.meowzix.feature.telegramauth

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.behradhz.meowzix.domain.telegram.TelegramProxyDraft
import dev.behradhz.meowzix.domain.telegram.TelegramProxyItem
import dev.behradhz.meowzix.domain.telegram.TelegramProxyKind
import dev.behradhz.meowzix.domain.telegram.TelegramProxyLinkParser
import dev.behradhz.meowzix.domain.telegram.TelegramProxyState
import dev.behradhz.meowzix.ui.components.TelegramGlassChrome
import dev.behradhz.meowzix.ui.components.TelegramGlassFrame
import dev.behradhz.meowzix.ui.components.TelegramGlassInput
import dev.behradhz.meowzix.ui.components.TelegramGlassPill
import dev.chrisbanes.haze.HazeState

/**
 * Forward-style glass proxy manager. Only inspect the clipboard while this page is foreground;
 * a detected link requires an explicit Add and never silently activates.
 */
@Composable
internal fun TelegramProxyPanel(
    state: TelegramProxyState,
    hazeState: HazeState,
    onAdd: (TelegramProxyDraft) -> Unit,
    onImport: (String) -> Unit,
    onRefresh: () -> Unit,
    onEnable: (Int) -> Unit,
    onDisable: () -> Unit,
    onRemove: (Int) -> Unit,
    onPing: (Int) -> Unit,
    onClearError: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var expanded by remember { mutableStateOf(false) }
    var suggestedLink by remember { mutableStateOf<String?>(null) }
    var lastSeenLink by remember { mutableStateOf<String?>(null) }
    var linkInput by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var manualKind by remember { mutableStateOf(TelegramProxyKind.MTPROTO) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }

    fun readLink(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        return value.takeIf { TelegramProxyLinkParser.findIn(it) != null }
    }

    DisposableEffect(lifecycle) {
        fun checkClipboard() {
            readLink()?.let { value ->
                if (value != lastSeenLink) {
                    lastSeenLink = value
                    suggestedLink = value
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkClipboard()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) checkClipboard()
        onDispose { lifecycle.removeObserver(observer) }
    }

    TelegramGlassFrame(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(46.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Cloud, contentDescription = null)
                    }
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Proxy settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val current = state.proxies.firstOrNull { it.enabled }
                    Text(
                        current?.let { "${it.kind.name} · ${it.server}" } ?: "Direct connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = TelegramGlassChrome.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Close" else "Manage")
                }
            }

            suggestedLink?.let { link ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    modifier = Modifier.fillMaxWidth().border(1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), RoundedCornerShape(18.dp)),
                ) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Telegram proxy link detected", modifier = Modifier.padding(start = 9.dp),
                                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Add this copied link? The proxy will not be enabled automatically.",
                            style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onImport(link); suggestedLink = null; expanded = true },
                                enabled = !state.isChanging, shape = RoundedCornerShape(18.dp)) {
                                Text("Add proxy")
                            }
                            TextButton(onClick = { suggestedLink = null }) { Text("Ignore") }
                        }
                    }
                }
            }

            state.errorMessage?.let { error ->
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your proxies", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onRefresh, enabled = !state.isLoading && !state.isChanging) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh proxies", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("Refresh")
                    }
                }
                if (state.isLoading || state.isChanging) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (state.isChanging) "Applying changes…" else "Loading proxies…",
                            modifier = Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall,
                            color = TelegramGlassChrome.muted)
                    }
                }
                DirectConnectionRow(state = state, onPing = { onPing(0) }, onDisable = onDisable)
                if (state.proxies.isEmpty() && !state.isLoading) {
                    Text("No saved proxies yet. Paste a link or add one manually.",
                        style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
                        modifier = Modifier.padding(vertical = 8.dp))
                }
                state.proxies.forEach { proxy ->
                    ProxyDestinationRow(
                        proxy = proxy,
                        changing = state.isChanging,
                        onPing = { onPing(proxy.id) },
                        onEnable = { onEnable(proxy.id) },
                        onRemove = { pendingRemoval = proxy.id },
                    )
                }
                Text("Add a proxy", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 6.dp, start = 3.dp))
                TelegramGlassInput(
                    value = linkInput, onValueChange = { linkInput = it },
                    label = "t.me/proxy, t.me/socks or tg:// link",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            val copied = readLink()
                            if (copied == null) manualError = "Clipboard has no valid Telegram proxy link."
                            else { linkInput = copied; manualError = null }
                        },
                        shape = RoundedCornerShape(19.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("Paste")
                    }
                    Button(onClick = { onImport(linkInput); linkInput = "" },
                        enabled = linkInput.isNotBlank() && !state.isChanging,
                        shape = RoundedCornerShape(19.dp), modifier = Modifier.weight(1f)) {
                        Text("Add link")
                    }
                }
                OutlinedButton(onClick = { showManual = !showManual }, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(if (showManual) "Hide manual setup" else "Add manually")
                }
                if (showManual) {
                    val capsule = RoundedCornerShape(23.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(TelegramGlassChrome.base.copy(alpha = 0.45f), capsule)
                            .border(1.dp, TelegramGlassChrome.stroke, capsule)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(TelegramGlassChrome.base.copy(alpha = 0.50f), CircleShape)
                                .border(1.dp, TelegramGlassChrome.stroke, CircleShape)
                                .padding(5.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TelegramProxyKind.entries.forEach { kind ->
                                TelegramGlassPill(title = kind.name, selected = manualKind == kind,
                                    onSelectedChange = { manualKind = kind }, modifier = Modifier.weight(1f))
                            }
                        }
                        TelegramGlassInput(host, { host = it }, "Server", Modifier.fillMaxWidth())
                        TelegramGlassInput(port, { port = it.filter(Char::isDigit).take(5) }, "Port (1–65535)",
                            Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
                        if (manualKind == TelegramProxyKind.MTPROTO) {
                            TelegramGlassInput(secret, { secret = it.trim() }, "MTProto secret (hex)",
                                Modifier.fillMaxWidth(), password = true)
                        } else {
                            TelegramGlassInput(username, { username = it }, "Username (optional)", Modifier.fillMaxWidth())
                            TelegramGlassInput(password, { password = it }, "Password (optional)",
                                Modifier.fillMaxWidth(), password = true)
                        }
                        Button(
                            onClick = {
                                try {
                                    val valid = TelegramProxyDraft(manualKind, host,
                                        port.toIntOrNull() ?: 0, username, password, secret).validated()
                                    manualError = null
                                    onAdd(valid)
                                    host = ""; port = ""; username = ""; password = ""; secret = ""
                                    showManual = false
                                } catch (error: IllegalArgumentException) {
                                    manualError = error.message ?: "Invalid proxy details."
                                }
                            },
                            enabled = !state.isChanging, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) { Text("Save proxy") }
                    }
                }
                manualError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Ping measures Telegram response time through the selected route at test time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelegramGlassChrome.muted,
                )
            }
        }
    }

    pendingRemoval?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove proxy?") },
            text = { Text("Remove this saved proxy? Your Telegram session and music are preserved.") },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onRemove(id) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DirectConnectionRow(state: TelegramProxyState, onPing: () -> Unit, onDisable: () -> Unit) {
    val selected = state.proxies.none { it.enabled }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(TelegramGlassChrome.base.copy(alpha = 0.28f), shape)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.055f), shape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(modifier = Modifier.size(42.dp), shape = CircleShape, color = TelegramGlassChrome.fill) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Cloud, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text("Direct connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    state.directTesting -> "Testing Telegram…"
                    state.directLatencyMs != null -> "${state.directLatencyMs} ms"
                    state.directPingError != null -> state.directPingError
                    else -> if (selected) "Selected route" else "No proxy"
                },
                style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
            )
        }
        TextButton(onClick = onPing, enabled = !state.directTesting) {
            Text(if (state.directTesting) "…" else "Ping")
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        else TextButton(onClick = onDisable, enabled = !state.isChanging) { Text("Use") }
    }
}

@Composable
private fun ProxyDestinationRow(
    proxy: TelegramProxyItem,
    changing: Boolean,
    onPing: () -> Unit,
    onEnable: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(TelegramGlassChrome.base.copy(alpha = 0.28f), shape)
            .border(1.dp, if (proxy.enabled) accent.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.055f), shape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(42.dp), shape = CircleShape,
                color = if (proxy.enabled) accent.copy(alpha = 0.17f) else TelegramGlassChrome.fill) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Cloud, contentDescription = null,
                        tint = if (proxy.enabled) accent else Color.White.copy(alpha = 0.7f))
                }
            }
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text("${proxy.server}:${proxy.port}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        proxy.testing -> "${proxy.kind.name} · Testing…"
                        proxy.latencyMs != null -> "${proxy.kind.name} · ${proxy.latencyMs} ms"
                        proxy.pingError != null -> "${proxy.kind.name} · ${proxy.pingError}"
                        else -> "${proxy.kind.name} · Not tested"
                    },
                    style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (proxy.enabled) Icon(Icons.Rounded.Check, contentDescription = "Active", tint = accent)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPing, enabled = !proxy.testing) {
                if (proxy.testing) CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                else Text("Ping")
            }
            TextButton(onClick = onRemove, enabled = !changing) { Text("Remove") }
            if (!proxy.enabled) {
                Button(onClick = onEnable, enabled = !changing,
                    shape = RoundedCornerShape(17.dp)) { Text("Connect") }
            } else Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = accent,
                modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}
