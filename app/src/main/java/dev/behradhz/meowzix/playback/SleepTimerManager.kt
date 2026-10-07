package dev.behradhz.meowzix.playback

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.PersistedSleepTimer
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerPolicy
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.playback.SleepTimerTermination
import dev.behradhz.meowzix.domain.playback.SleepTimerTerminationReason
import dev.behradhz.meowzix.playback.persistence.PlaybackStateStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PlaybackMonotonicClock {
    fun elapsedRealtimeMs(): Long
    fun bootIdentity(): String
}

@Singleton
class AndroidPlaybackMonotonicClock @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PlaybackMonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootIdentity(): String {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()
        if (bootCount != null) return "boot-count:$bootCount"
        val approximateBootEpochMinute =
            (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        return "boot-epoch-minute:$approximateBootEpochMinute"
    }
}

@Singleton
class SleepTimerManager @Inject constructor(
    private val stateStore: PlaybackStateStore,
    private val clock: PlaybackMonotonicClock,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()
    private val _terminations = MutableSharedFlow<SleepTimerTermination>(extraBufferCapacity = 4)
    val terminations: SharedFlow<SleepTimerTermination> = _terminations.asSharedFlow()
    private var restored = false

    suspend fun restore(): SleepTimerState = mutex.withLock { restoreLocked() }

    suspend fun setDuration(durationMs: Long, fadeDurationMs: Long = DEFAULT_FADE_MS): SleepTimerState =
        mutex.withLock {
            ensureRestoredLocked()
            val now = clock.elapsedRealtimeMs()
            val boot = clock.bootIdentity()
            val persisted = SleepTimerPolicy.duration(durationMs, now, boot, fadeDurationMs)
            stateStore.saveSleepTimer(persisted)
            SleepTimerPolicy.restore(persisted, now, boot).also { _state.value = it }
        }

    suspend fun stopAtEndOfTrack(
        currentMediaId: String?,
        fadeDurationMs: Long = DEFAULT_FADE_MS,
    ): SleepTimerState = mutex.withLock {
        ensureRestoredLocked()
        val boot = clock.bootIdentity()
        val persisted = SleepTimerPolicy.endOfTrack(currentMediaId, boot, fadeDurationMs)
        stateStore.saveSleepTimer(persisted)
        SleepTimerPolicy.restore(persisted, clock.elapsedRealtimeMs(), boot).also { _state.value = it }
    }

    suspend fun extend(additionalMs: Long): SleepTimerState = mutex.withLock {
        ensureRestoredLocked()
        val updated = SleepTimerPolicy.extend(_state.value, additionalMs, clock.elapsedRealtimeMs())
        _state.value = updated
        persistStateLocked(updated)
        updated
    }

    suspend fun cancel() = mutex.withLock {
        restored = true
        _state.value = SleepTimerState.Off
        stateStore.clearSleepTimer()
    }

    suspend fun neutralizeFadeForManualPlayback() = mutex.withLock {
        ensureRestoredLocked()
        val current = _state.value
        if (!current.active || current.fadeDurationMs == 0L) return@withLock
        val updated = current.copy(fadeDurationMs = 0L)
        _state.value = updated
        persistStateLocked(updated)
    }

    suspend fun rearmEndOfTrack(mediaId: String?) = mutex.withLock {
        ensureRestoredLocked()
        val current = _state.value
        if (current.mode != SleepTimerMode.END_OF_TRACK || current.armedMediaId == mediaId) return@withLock
        val updated = current.copy(armedMediaId = mediaId)
        _state.value = updated
        persistStateLocked(updated)
    }

    suspend fun refresh(): SleepTimerState = mutex.withLock {
        ensureRestoredLocked()
        SleepTimerPolicy.refresh(_state.value, clock.elapsedRealtimeMs()).also { _state.value = it }
    }

    suspend fun complete(
        reason: SleepTimerTerminationReason,
        mediaId: String?,
        positionMs: Long,
        durationMs: Long,
    ) = mutex.withLock {
        ensureRestoredLocked()
        if (!_state.value.active) return@withLock
        _state.value = SleepTimerState.Off
        stateStore.clearSleepTimer()
        _terminations.emit(
            SleepTimerTermination(reason, mediaId, positionMs.coerceAtLeast(0L), durationMs.coerceAtLeast(0L)),
        )
    }

    private suspend fun restoreLocked(): SleepTimerState {
        val persisted = stateStore.loadSleepTimer()
        val result = SleepTimerPolicy.restore(persisted, clock.elapsedRealtimeMs(), clock.bootIdentity())
        restored = true
        _state.value = result
        if (!result.active && persisted != null) stateStore.clearSleepTimer()
        return result
    }

    private suspend fun ensureRestoredLocked() {
        if (!restored) restoreLocked()
    }

    private suspend fun persistStateLocked(state: SleepTimerState) {
        if (!state.active) {
            stateStore.clearSleepTimer()
            return
        }
        stateStore.saveSleepTimer(
            PersistedSleepTimer(
                mode = state.mode,
                deadlineElapsedRealtimeMs = state.deadlineElapsedRealtimeMs,
                bootIdentity = state.bootIdentity ?: clock.bootIdentity(),
                armedMediaId = state.armedMediaId,
                fadeDurationMs = state.fadeDurationMs,
            ),
        )
    }

    companion object {
        const val DEFAULT_FADE_MS = 5_000L
    }
}
