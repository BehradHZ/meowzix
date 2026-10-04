package dev.behradhz.meowzix.domain.lyrics

enum class LyricsFollowMode {
    FOLLOWING,
    USER_BROWSING,
    REATTACH_ELIGIBLE,
}

enum class LyricsFollowAction {
    NONE,
    SETTLE_AND_FOLLOW,
}

class LyricsFollowMachine(
    private val reattachZoneRatio: Float = 0.35f,
    private val exitZoneRatio: Float = 0.55f,
    private val dwellMs: Long = 250L,
) {
    var mode: LyricsFollowMode = LyricsFollowMode.FOLLOWING
        private set
    private var eligibleSinceMs: Long? = null

    fun onUserInteraction() {
        mode = LyricsFollowMode.USER_BROWSING
        eligibleSinceMs = null
    }

    fun forceFollow(): LyricsFollowAction {
        mode = LyricsFollowMode.FOLLOWING
        eligibleSinceMs = null
        return LyricsFollowAction.SETTLE_AND_FOLLOW
    }

    fun evaluate(
        activeLineCenterPx: Float,
        anchorPx: Float,
        activeLineHeightPx: Float,
        pointerActive: Boolean,
        listMoving: Boolean,
        flingActive: Boolean,
        nowMs: Long,
    ): LyricsFollowAction {
        if (mode == LyricsFollowMode.FOLLOWING) return LyricsFollowAction.NONE
        if (pointerActive || listMoving || flingActive || activeLineHeightPx <= 0f) {
            mode = LyricsFollowMode.USER_BROWSING
            eligibleSinceMs = null
            return LyricsFollowAction.NONE
        }
        val distance = kotlin.math.abs(activeLineCenterPx - anchorPx)
        val enter = activeLineHeightPx * reattachZoneRatio
        val exit = activeLineHeightPx * exitZoneRatio
        val inside = when (mode) {
            LyricsFollowMode.REATTACH_ELIGIBLE -> distance <= exit
            else -> distance <= enter
        }
        if (!inside) {
            mode = LyricsFollowMode.USER_BROWSING
            eligibleSinceMs = null
            return LyricsFollowAction.NONE
        }
        if (mode != LyricsFollowMode.REATTACH_ELIGIBLE) {
            mode = LyricsFollowMode.REATTACH_ELIGIBLE
            eligibleSinceMs = nowMs
            return LyricsFollowAction.NONE
        }
        val since = eligibleSinceMs ?: nowMs.also { eligibleSinceMs = it }
        if (nowMs - since < dwellMs) return LyricsFollowAction.NONE
        mode = LyricsFollowMode.FOLLOWING
        eligibleSinceMs = null
        return LyricsFollowAction.SETTLE_AND_FOLLOW
    }
}
