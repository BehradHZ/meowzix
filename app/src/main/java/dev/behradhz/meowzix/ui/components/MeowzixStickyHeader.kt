package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import dev.behradhz.meowzix.domain.telegram.TelegramProxyBadge
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

data class MeowzixHeaderControls(
    val onSettings: (() -> Unit)? = null,
    val onProxy: (() -> Unit)? = null,
    val proxyBadge: TelegramProxyBadge = TelegramProxyBadge.DISABLED,
)

val LocalMeowzixHeaderControls = compositionLocalOf<MeowzixHeaderControls?> { null }

/**
 * One chrome language for scrolling destinations. Draw this after the scrolling content
 * inside a Box, so the content moves under the frosted header rather than moving the header.
 * The scrolling surface must register as a hazeSource for the supplied HazeState.
 */
@Composable
fun MeowzixStickyHeader(
    title: String,
    subtitle: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    tintAlpha: Float = 0.76f,
    leadingActions: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = tintAlpha),
    ) {
        // A real rectangular frosted strip, not a transparent floating title.
        // The translucent fill remains visible when device blur is unavailable.
        Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.30f)))
        val contentLayoutDirection = LocalLayoutDirection.current
        val rowContent: @Composable RowScope.() -> Unit = {
            // A leading action is pinned to the physical left for this header only.
            leadingActions?.invoke(this)
            // Keep the title on the same vertical axis as the 44dp action icons.
            // The secondary text occupies the lower line without lifting the title.
            Box(
                Modifier.weight(1f)
                    .heightIn(min = if (subtitle.isBlank()) 60.dp else 68.dp),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection) {
                    Text(
                        title,
                        modifier = Modifier.align(Alignment.CenterStart),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            modifier = Modifier.align(Alignment.BottomStart),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            actions()
            // Trailing controls share the same spacing and inset as contextual actions.
            LocalMeowzixHeaderControls.current?.let { controls ->
                controls.onSettings?.let { onSettings ->
                    MeowzixHeaderAction("Open settings", Icons.Rounded.Settings, onSettings)
                }
                controls.onProxy?.let { onProxy ->
                    MeowzixProxyHeaderAction(controls.proxyBadge, onProxy)
                }
            }
        }
        val rowModifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .heightIn(min = 60.dp)
        if (leadingActions == null) {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                content = rowContent,
            )
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    content = rowContent,
                )
            }
        }
    }
}

@Composable
fun MeowzixHeaderAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = modifier.size(44.dp)
            .background(colors.primary.copy(alpha = 0.11f), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.3f),
    )
}

/** Telegram-style shield: tick only when connected; a rotating ring while connecting. */
@Composable
private fun MeowzixProxyHeaderAction(badge: TelegramProxyBadge, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = when (badge) {
        TelegramProxyBadge.DISABLED -> "Proxy settings: disabled"
        TelegramProxyBadge.CONNECTING -> "Proxy settings: connecting"
        TelegramProxyBadge.CONNECTED -> "Proxy settings: connected"
    }
    Box(
        modifier = Modifier.size(44.dp)
            .background(colors.primary.copy(alpha = 0.11f), RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("telegram-proxy-status"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Shield, contentDescription = label,
            modifier = Modifier.size(26.dp), tint = colors.onSurface,
        )
        when (badge) {
            TelegramProxyBadge.CONNECTED -> Icon(
                Icons.Rounded.Check, contentDescription = null,
                modifier = Modifier.size(12.dp), tint = colors.surface,
            )
            TelegramProxyBadge.CONNECTING -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp, color = colors.surface,
            )
            TelegramProxyBadge.DISABLED -> Unit
        }
    }
}
