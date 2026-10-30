package dev.behradhz.meowzix.playback

/**
 * Only an interruption initiated by the microphone observer may auto-resume.
 * A later manual Play clears that pending interruption before recording ends.
 * Audio focus remains Android/Media3's responsibility when resume is requested.
 */
internal fun shouldResumeAfterMicrophoneStops(
    pausedByMicrophone: Boolean,
    playbackRequested: Boolean,
    otherMediaPlaying: Boolean,
    hasTrack: Boolean,
    playbackEnded: Boolean,
    sleepTimerExpired: Boolean,
): Boolean =
    pausedByMicrophone && !playbackRequested && !otherMediaPlaying &&
        hasTrack && !playbackEnded && !sleepTimerExpired
