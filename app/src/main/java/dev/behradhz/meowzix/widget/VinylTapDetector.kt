package dev.behradhz.meowzix.widget

/**
 * App widgets receive clicks, not MotionEvents. Two clicks from the same directional
 * hotspot within the threshold count as one double tap. A single directional tap
 * deliberately does nothing, so it cannot accidentally skip or favorite a song.
 */
internal class VinylTapDetector(private val maxIntervalMs: Long = 650L) {
    private var previousWidgetId: Int = -1
    private var previousZone: String? = null
    private var previousTimeMs: Long = Long.MIN_VALUE

    @Synchronized
    fun register(widgetId: Int, zone: String, elapsedRealtimeMs: Long): Boolean {
        val doubleTap = widgetId == previousWidgetId &&
            zone == previousZone &&
            elapsedRealtimeMs >= previousTimeMs &&
            elapsedRealtimeMs - previousTimeMs <= maxIntervalMs

        if (doubleTap) {
            previousWidgetId = -1
            previousZone = null
            previousTimeMs = Long.MIN_VALUE
        } else {
            previousWidgetId = widgetId
            previousZone = zone
            previousTimeMs = elapsedRealtimeMs
        }
        return doubleTap
    }
}
