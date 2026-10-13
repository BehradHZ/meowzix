package dev.behradhz.meowzix.feature.telegramauth

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.behradhz.meowzix.domain.telegram.TelegramProxyDraft
import dev.behradhz.meowzix.domain.telegram.TelegramProxyKind
import dev.behradhz.meowzix.domain.telegram.TelegramProxyLinkParser
import dev.behradhz.meowzix.domain.telegram.TelegramProxyState
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.chrisbanes.haze.HazeState

/**
 * Phone-only proxy management. The clipboard is inspected ONLY when this screen resumes or
 * when Paste is tapped; valid links are suggested for confirmation, never silently activated.
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

    fun clipboardLink(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        // Retain the copied text only in the foreground composable; never log its secret.
        return text.takeIf { TelegramProxyLinkParser.findIn(it) != null }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                clipboardLink()?.let { value ->
                    if (value != lastSeenLink) {
                        lastSeenLink = value
                        suggestedLink = value
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    GlassSurface(
        hazeState = hazeState,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(28.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.83f),
        tint = Color.White.copy(alpha = 0.06f),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Telegram proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("MTProto · SOCKS5 · HTTP  |  Available before sign-in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh, enabled = !state.isLoading && !state.isChanging) {
                    Text("Refresh")
                }
            }
            if (state.isLoading || state.isChanging) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (state.isChanging) "Updating proxy…" else "Loading proxies…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.errorMessage?.let { msg ->
                Row {
                    Text(msg, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearError) { Text("Dismiss") }
                }
            }
            suggestedLink?.let {
                Surface(shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Proxy link detected in clipboard",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Review and add it to your list. Nothing is activated automatically.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onImport(it); suggestedLink = null }, enabled = !state.isChanging) {
                                Text("Add proxy")
                            }
                            TextButton(onClick = { suggestedLink = null }) { Text("Ignore") }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = linkInput,
                onValueChange = { linkInput = it },
                label = { Text("Paste a t.me/proxy, t.me/socks or tg:// link") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val value = clipboardLink()
                    if (value == null) manualError = "No valid Telegram proxy link on clipboard."
                    else { linkInput = value; manualError = null }
                }) {
                    androidx.compose.material3.Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Paste")
                }
                Button(onClick = { onImport(linkInput); linkInput = "" },
                    enabled = linkInput.isNotBlank() && !state.isChanging) {
                    Text("Add link")
                }
                TextButton(onClick = { showManual = !showManual }) {
                    androidx.compose.material3.Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Text("Manual")
                }
            }
            manualError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (showManual) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TelegramProxyKind.entries.forEach { kind ->
                        if (manualKind == kind) Button(onClick = { manualKind = kind }, Modifier.weight(1f)) {
                            Text(kind.name)
                        } else OutlinedButton(onClick = { manualKind = kind }, Modifier.weight(1f)) {
                            Text(kind.name)
                        }
                    }
                }
                OutlinedTextField(host, { host = it }, label = { Text("Server") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("Port (1–65535)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (manualKind == TelegramProxyKind.MTPROTO) {
                    OutlinedTextField(secret, { secret = it.trim() }, label = { Text("MTProto secret (hex)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password (optional)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Button(onClick = {
                    try {
                        val draft = TelegramProxyDraft(manualKind, host, port.toIntOrNull() ?: 0,
                            username, password, secret).validated()
                        manualError = null
                        onAdd(draft)
                        host = ""; port = ""; username = ""; password = ""; secret = ""
                        showManual = false
                    } catch (error: IllegalArgumentException) {
                        manualError = error.message ?: "Invalid proxy details."
                    }
                }, enabled = !state.isChanging, modifier = Modifier.fillMaxWidth()) { Text("Save proxy") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Direct connection", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row {
                    TextButton(onClick = { onPing(0) }, enabled = !state.directTesting) {
                        Text(if (state.directTesting) "Testing…" else "Ping")
                    }
                    TextButton(onClick = onDisable, enabled = !state.isChanging) {
                        Text(if (state.proxies.none { it.enabled }) "Active" else "Use direct")
                    }
                }
            }
            val directStatus = when {
                state.directTesting -> "Testing Telegram directly…"
                state.directLatencyMs != null -> "Direct: ${state.directLatencyMs} ms"
                state.directPingError != null -> "Direct: ${state.directPingError}"
                else -> null
            }
            directStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.proxies.isEmpty() && !state.isLoading) {
                Text("No proxies saved. Add a link or enter one manually.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            state.proxies.forEach { proxy ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (proxy.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f),
                    modifier = Modifier.fillMaxWidth().border(
                        1.dp, if (proxy.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f), RoundedCornerShape(20.dp)),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${proxy.kind.name}  ·  ${proxy.server}:${proxy.port}",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                            if (proxy.enabled) Text("ACTIVE", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            when {
                                proxy.testing -> "Testing Telegram connection…"
                                proxy.latencyMs != null -> "${proxy.latencyMs} ms"
                                proxy.pingError != null -> proxy.pingError
                                else -> "Not tested"
                            }, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onPing(proxy.id) }, enabled = !proxy.testing) { Text("Ping") }
                            Button(onClick = { onEnable(proxy.id) }, enabled = !state.isChanging && !proxy.enabled) {
                                Text(if (proxy.enabled) "Connected" else "Connect")
                            }
                            TextButton(onClick = { pendingRemoval = proxy.id }, enabled = !state.isChanging) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            Text("A successful ping confirms Telegram was reachable through that route at test time; it cannot guarantee future connectivity.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    pendingRemoval?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove proxy?") },
            text = { Text("Only this saved proxy is removed. Your Telegram login and music stay intact.") },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onRemove(id) }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
}
