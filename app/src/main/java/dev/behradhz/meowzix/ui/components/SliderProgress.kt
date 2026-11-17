package dev.behradhz.meowzix.ui.components

/** Keep the two custom seek bars' value-to-progress mapping identical. */
internal fun normalizedSliderProgress(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val coercedValue = value.coerceIn(range.start, range.endInclusive)
    val rangeLength = range.endInclusive - range.start
    return if (rangeLength <= 0f) {
        0f
    } else {
        ((coercedValue - range.start) / rangeLength).coerceIn(0f, 1f)
    }
}
