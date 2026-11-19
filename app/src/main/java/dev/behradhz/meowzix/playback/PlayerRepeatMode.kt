package dev.behradhz.meowzix.playback

import androidx.media3.common.Player
import dev.behradhz.meowzix.domain.playback.RepeatMode

/** Progressive queues control repeat-all themselves; Media3 must not loop their window. */
internal fun RepeatMode.toPlayerRepeatMode(progressive: Boolean): Int = when {
    this == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    this == RepeatMode.ALL && !progressive -> Player.REPEAT_MODE_ALL
    else -> Player.REPEAT_MODE_OFF
}
