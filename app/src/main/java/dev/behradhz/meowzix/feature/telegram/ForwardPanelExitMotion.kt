package dev.behradhz.meowzix.feature.telegram

/** A brief downward fade that keeps the translucent window intact until exit ends. */
internal const val ForwardPanelExitDurationMs = 210

internal data class ForwardPanelExitFrame(
    val alpha: Float,
    val translationDp: Float,
    val scale: Float,
)

internal fun forwardPanelExitFrame(progress: Float): ForwardPanelExitFrame {
    val fraction = progress.coerceIn(0f, 1f)
    return ForwardPanelExitFrame(
        alpha = 1f - fraction,
        translationDp = 38f * fraction,
        scale = 1f - 0.018f * fraction,
    )
}
