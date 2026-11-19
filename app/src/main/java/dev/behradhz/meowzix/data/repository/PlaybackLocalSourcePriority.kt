package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.TrackSourceEntity

/**
 * Both cached playback and offline-copy reuse prefer the app-owned copy, then
 * TDLib's local file, then MediaStore. Library-source and forwarding priorities
 * intentionally follow different policies.
 */
internal fun playbackLocalSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.APP_OFFLINE_COPY -> 0
    TrackSourceType.TDLIB_LOCAL -> 1
    TrackSourceType.LOCAL_MEDIASTORE -> 2
    else -> 3
}
