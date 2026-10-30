package dev.behradhz.meowzix.playback

/**
 * Automatic pauses and explicit user intent are different things. A pause()
 * issued by PlaybackService must not be interpreted as the user pressing Pause.
 *
 * Serialized by PlaybackService on its playback/main thread. This class has no
 * Android dependencies, no timers and does not call Player directly.
 */
internal class AudioInterruptionResumePolicy {
    enum class Source { EXTERNAL_PLAYBACK, MICROPHONE }
    enum class Effect { NONE, PAUSE, REQUEST_RESUME }

    private val activeSources = mutableSetOf<Source>()
    private var resumeArmed = false
    private var resumeRequested = false
    private var explicitlyPaused = false

    /** A user/headset/notification Pause or Stop revokes automatic resume. */
    fun userPaused() {
        explicitlyPaused = true
        resumeArmed = false
        resumeRequested = false
    }

    /** User Play may occur while a previously recorded interruption is active. */
    fun userPlayed() {
        explicitlyPaused = false
        resumeArmed = activeSources.isNotEmpty()
        resumeRequested = false
    }

    /** Switching media cancels the interrupted track's pending resume. */
    fun newPlaybackSelection() {
        explicitlyPaused = false
        resumeArmed = false
        resumeRequested = false
    }

    /** Turning the preference off must not make subsequent automatic pauses fail. */
    fun disableAutoResume() {
        resumeArmed = false
        resumeRequested = false
    }

    /**
     * The first blocker that interrupts intended playback arms a single resume.
     * Additional overlapping blockers are tracked without replacing that intent.
     */
    fun interrupted(source: Source, wasPlayingOrIntended: Boolean): Effect {
        if (!activeSources.add(source)) return Effect.NONE
        val shouldPause = !explicitlyPaused &&
            (wasPlayingOrIntended || resumeArmed || resumeRequested)
        if (shouldPause) resumeArmed = true
        resumeRequested = false
        return if (shouldPause && wasPlayingOrIntended) Effect.PAUSE else Effect.NONE
    }

    /** Only the last completed blocker may issue a resume, and only once. */
    fun interruptionEnded(source: Source): Effect {
        if (!activeSources.remove(source)) return Effect.NONE
        return if (activeSources.isEmpty() && !explicitlyPaused &&
            resumeArmed && !resumeRequested
        ) {
            resumeRequested = true
            Effect.REQUEST_RESUME
        } else Effect.NONE
    }

    /** The player is actually audible, not merely playWhenReady=true. */
    fun playbackStarted() {
        resumeArmed = false
        resumeRequested = false
    }

    fun resumeRejected() {
        resumeArmed = false
        resumeRequested = false
    }

    fun reset() {
        activeSources.clear()
        resumeArmed = false
        resumeRequested = false
        explicitlyPaused = false
    }

    fun isInterruptedBy(source: Source): Boolean = source in activeSources
    fun isResumeRequested(): Boolean = resumeRequested
    fun isResumeArmed(): Boolean = resumeArmed
}
