package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Shows pull progress, animates into a spinner, then retracts after a manual refresh. */
@Composable
internal fun BoxScope.FadeRefreshIndicator(
    state: PullToRefreshState,
    refreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = refreshing,
        modifier = modifier.testTag("music-refresh-indicator"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        color = MaterialTheme.colorScheme.primary,
    )
}
