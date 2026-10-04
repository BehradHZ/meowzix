package dev.behradhz.meowzix.domain.playback

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class SmartQueueAdaptationReason {
    EARLY_SKIP,
    SKIP_STREAK,
    EXPLICIT_FEEDBACK,
    TIME_BUCKET_CHANGED,
    MODEL_UPDATED,
    SMART_CYCLE_RESTARTED,
}

data class SmartQueueAdaptationSignal(
    val reason: SmartQueueAdaptationReason,
    val emittedAt: Instant = Instant.now(),
)

@Singleton
class SmartQueueAdaptationBus @Inject constructor() {
    private val _signals = MutableSharedFlow<SmartQueueAdaptationSignal>(extraBufferCapacity = 16)
    val signals: SharedFlow<SmartQueueAdaptationSignal> = _signals.asSharedFlow()

    fun emit(reason: SmartQueueAdaptationReason) {
        _signals.tryEmit(SmartQueueAdaptationSignal(reason))
    }
}
