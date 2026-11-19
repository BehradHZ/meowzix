package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Shared centered, tinted-glass dialog chrome for Sleep Timer and Playback Speed. */
@Composable
internal fun MeowzixGlassDialog(
    onDismiss: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val accentGlass = colors.primary.copy(alpha = 0.16f).compositeOver(colors.surface)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    var entered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val requestDismiss = { visible = false }

    LaunchedEffect(Unit) {
        entered = true
        visible = true
    }
    LaunchedEffect(entered, visible) {
        if (entered && !visible) {
            delay(180)
            latestOnDismiss()
        }
    }

    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.90f)
                    .heightIn(max = 700.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.surface.copy(alpha = 0.94f),
                                accentGlass.copy(alpha = 0.97f),
                                colors.surface.copy(alpha = 0.995f),
                            ),
                        ),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .testTag(tag),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content(requestDismiss)
            }
        }
    }
}
