package dev.behradhz.meowzix

import android.content.Context

/** Opt-in is separate from the special Android overlay permission (which may be revoked). */
object LockScreenPlayerPreferences {
    private const val FILE = "meowzix_lock_screen_player"
    private const val ENABLED = "open_on_wake"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED, enabled).apply()
    }
}

/** A pure gate to prevent unexpected launches while unlocked, paused, or without consent. */
internal fun shouldOpenLockScreenPlayer(
    enabled: Boolean,
    overlayPermission: Boolean,
    keyguardLocked: Boolean,
    activelyPlaying: Boolean,
): Boolean = enabled && overlayPermission && keyguardLocked && activelyPlaying
