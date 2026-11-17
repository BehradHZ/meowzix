package dev.behradhz.meowzix.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Manual refresh stays unobtrusive; cached songs remain visible throughout. */
@Composable
internal fun BoxScope.FadeRefreshIndicator(
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = refreshing,
        enter = fadeIn(animationSpec = tween(120)),
        exit = fadeOut(animationSpec = tween(150)),
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refreshing music",
                modifier = Modifier.padding(10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
