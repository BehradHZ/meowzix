package dev.behradhz.meowzix.domain.playback

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class QueueActionKind {
    PLAY_NEXT,
    ADD_TO_END,
}

data class QueueActionFeedback(
    val kind: QueueActionKind,
    val trackTitle: String,
)

@Singleton
class QueueActionFeedbackBus @Inject constructor() {
    private val _events = MutableSharedFlow<QueueActionFeedback>(extraBufferCapacity = 8)
    val events: SharedFlow<QueueActionFeedback> = _events.asSharedFlow()

    fun emit(kind: QueueActionKind, trackTitle: String) {
        _events.tryEmit(QueueActionFeedback(kind, trackTitle))
    }
}
