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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.behradhz.meowzix.domain.telegram.TelegramProxyDraft
import dev.behradhz.meowzix.domain.telegram.TelegramProxyItem
import dev.behradhz.meowzix.domain.telegram.TelegramProxyKind
import dev.behradhz.meowzix.domain.telegram.TelegramProxyLinkParser
import dev.behradhz.meowzix.domain.telegram.TelegramProxyState
import dev.behradhz.meowzix.domain.telegram.matchesEndpoint
import dev.behradhz.meowzix.domain.telegram.rankTelegramProxiesByLatency
import dev.behradhz.meowzix.ui.components.TelegramGlassChrome
import dev.behradhz.meowzix.ui.components.TelegramGlassInput
import dev.behradhz.meowzix.ui.components.TelegramGlassPill

/**
 * The shield is always accessible in the Telegram header, including during login.
 * Clipboard content is inspected only while the dialog is visible and foregrounded.
 */
@Composable
internal fun TelegramProxyPanel(
    state: TelegramProxyState,
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
    var open by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }
    var lastSeenClipboard by remember { mutableStateOf<String?>(null) }
    val currentProxy = state.proxies.firstOrNull { it.enabled }
    val sortedProxies = rankTelegramProxiesByLatency(state.proxies)

    Surface(
        onClick = { open = true },
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = if (currentProxy != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f),
        contentColor = if (currentProxy != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Shield, contentDescription = "Proxy settings", modifier = Modifier.size(22.dp))
        }
    }

    LaunchedEffect(open) {
        if (open) onRefresh()
    }
    // Ping every saved proxy automatically whenever the manager opens, including newly loaded IDs.
    // Sort the key to prevent re-pinging when TDLib merely changes list order.
    LaunchedEffect(open, state.proxies.map { it.id }.sorted()) {
        if (open) state.proxies.forEach { onPing(it.id) }
    }
    DisposableEffect(lifecycle, open, state.proxies) {
        fun scanClipboard() {
            if (!open) return
            val text = readProxyClipboard(context) ?: return
            val draft = TelegramProxyLinkParser.findIn(text) ?: return
            if (state.proxies.any { it.matchesEndpoint(draft) }) {
                clipboardSuggestion = null
                lastSeenClipboard = text
            } else if (text != lastSeenClipboard) {
                lastSeenClipboard = text
                clipboardSuggestion = text
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) scanClipboard()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) scanClipboard()
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp),
                shape = RoundedCornerShape(28.dp),
                color = TelegramGlassChrome.base,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Proxy", modifier = Modifier.weight(1f).padding(start = 10.dp),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { adding = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add proxy")
                        }
                        IconButton(onClick = { open = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close proxy settings")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Use proxy", style = MaterialTheme.typography.titleMedium)
                            Text(
                                currentProxy?.let { "${it.server}:${it.port}" } ?: "Direct connection",
                                style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = currentProxy != null,
                            enabled = !state.isChanging,
                            onCheckedChange = { enabled ->
                                if (!enabled) onDisable()
                                else if (sortedProxies.isEmpty()) adding = true
                                else onEnable(sortedProxies.first().id)
                            },
                        )
                    }
                    state.errorMessage?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onClearError) { Text("Dismiss") }
                        }
                    }
                    if (state.isLoading || state.isChanging) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(if (state.isChanging) "Applying changes…" else "Loading proxies…",
                                modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text("Saved proxies · lowest ping first", style = MaterialTheme.typography.labelMedium,
                        color = TelegramGlassChrome.muted)
                    if (sortedProxies.isEmpty() && !state.isLoading) {
                        Text("No saved proxies. Tap + to add one.", style = MaterialTheme.typography.bodySmall,
                            color = TelegramGlassChrome.muted)
                    }
                    sortedProxies.forEach { proxy ->
                        val selected = proxy.enabled
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape,
                                )
                                .border(
                                    1.dp, if (selected) MaterialTheme.colorScheme.primary
                                    else TelegramGlassChrome.stroke, shape,
                                )
                                .clickable(enabled = !state.isChanging) { onEnable(proxy.id) }
                                .padding(start = 12.dp, end = 3.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected) {
                                Icon(Icons.Rounded.Check, contentDescription = "Selected proxy",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                            } else {
                                Icon(Icons.Rounded.Shield, contentDescription = null,
                                    tint = TelegramGlassChrome.muted, modifier = Modifier.size(19.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("${proxy.server}:${proxy.port}",
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${proxy.kind.name} · " + when {
                                        proxy.testing -> "Pinging…"
                                        proxy.latencyMs != null -> "${proxy.latencyMs} ms"
                                        proxy.pingError != null -> proxy.pingError
                                        else -> "Not tested"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (proxy.testing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            }
                            IconButton(onClick = { pendingRemoval = proxy.id }, enabled = !state.isChanging) {
                                Icon(Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete ${proxy.server} proxy",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                            }
                        }
                    }
                    clipboardSuggestion?.let { link ->
                        val duplicate = TelegramProxyLinkParser.findIn(link)?.let { draft ->
                            state.proxies.any { it.matchesEndpoint(draft) }
                        } ?: true
                        if (!duplicate) {
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ContentPaste, contentDescription = null,
                                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("Copied proxy detected", modifier = Modifier.padding(start = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text("Add the copied Telegram proxy? It won't connect automatically.",
                                        style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted)
                                    Row {
                                        TextButton(
                                            onClick = { onImport(link); clipboardSuggestion = null },
                                            enabled = !state.isChanging,
                                        ) { Text("Add") }
                                        TextButton(onClick = { clipboardSuggestion = null }) { Text("Ignore") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) {
        AddTelegramProxyDialog(
            changing = state.isChanging,
            onDismiss = { adding = false },
            onAdd = { draft -> onAdd(draft); adding = false },
            onImport = { link -> onImport(link); adding = false },
        )
    }
    pendingRemoval?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Delete proxy?") },
            text = { Text("This removes the saved proxy. Your Telegram account and music are preserved.") },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onRemove(id) },
                    enabled = !state.isChanging) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}

private fun readProxyClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context).toString()
        .takeIf { TelegramProxyLinkParser.findIn(it) != null }
}

@Composable
private fun AddTelegramProxyDialog(
    changing: Boolean,
    onDismiss: () -> Unit,
    onAdd: (TelegramProxyDraft) -> Unit,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TelegramProxyKind.MTPROTO) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp), color = TelegramGlassChrome.base,
            modifier = Modifier.fillMaxWidth().heightIn(max = 650.dp), tonalElevation = 10.dp) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add proxy", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                TelegramGlassInput(link, { link = it }, "Telegram proxy link", Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { link = readProxyClipboard(context).orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Paste link")
                }
                Button(
                    onClick = {
                        if (TelegramProxyLinkParser.findIn(link) == null) {
                            error = "Enter a valid Telegram proxy link."
                        } else onImport(link)
                    },
                    enabled = !changing && link.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add from link") }
                Text("Or enter connection details", style = MaterialTheme.typography.labelLarge,
                    color = TelegramGlassChrome.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TelegramProxyKind.entries.forEach { value ->
                        TelegramGlassPill(title = value.name, selected = kind == value,
                            onSelectedChange = { kind = value }, modifier = Modifier.weight(1f))
                    }
                }
                TelegramGlassInput(host, { host = it }, "Server", Modifier.fillMaxWidth())
                TelegramGlassInput(port, { port = it.filter(Char::isDigit).take(5) },
                    "Port (1–65535)", Modifier.fillMaxWidth(), keyboardType = KeyboardType.Number)
                if (kind == TelegramProxyKind.MTPROTO) {
                    TelegramGlassInput(secret, { secret = it }, "MTProto secret", Modifier.fillMaxWidth(),
                        password = true)
                } else {
                    TelegramGlassInput(username, { username = it }, "Username (optional)", Modifier.fillMaxWidth())
                    TelegramGlassInput(password, { password = it }, "Password (optional)", Modifier.fillMaxWidth(),
                        password = true)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = {
                        try {
                            val draft = TelegramProxyDraft(kind, host, port.toIntOrNull() ?: 0,
                                username, password, secret).validated()
                            onAdd(draft)
                        } catch (invalid: IllegalArgumentException) {
                            error = invalid.message ?: "Invalid proxy details."
                        }
                    },
                    enabled = !changing, modifier = Modifier.fillMaxWidth(),
                ) { Text("Save proxy") }
            }
        }
    }
}
