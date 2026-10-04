package dev.behradhz.meowzix.feature.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import dev.behradhz.meowzix.domain.recommendation.RecommendationReason
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Presentation/queue actions consume canonical IDs; the engine never executes playback. */
data class RecommendationActionState(
    val trackId: UUID? = null,
    val title: String = "",
    val loading: Boolean = false,
    val why: Boolean = false,
    val reasons: List<RecommendationReason> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    val feedbackNotice: String? = null,
)

@HiltViewModel
class RecommendationActionsViewModel @Inject constructor(
    private val engine: RecommendationEngine,
    private val library: MusicLibraryRepository,
    private val queue: QueueRepository,
    private val feedback: RecommendationFeedbackRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecommendationActionState())
    val state = _state.asStateFlow()
    private var load: Job? = null

    fun why(trackId: UUID, reasons: List<RecommendationReason>? = null) = open(trackId, why = true, knownReasons = reasons)
    fun continueVibe(trackId: UUID) = open(trackId, why = false)

    fun moreLikeThis(trackId: UUID) {
        viewModelScope.launch {
            feedback.set(trackId, RecommendationFeedbackAction.MORE_LIKE_THIS)
            open(trackId, why = false, feedbackNotice = "More like this saved")
        }
    }

    fun suggestLess(trackId: UUID) = setFeedback(
        trackId,
        RecommendationFeedbackAction.SUGGEST_LESS,
        "We'll suggest this less",
    )

    fun snooze(trackId: UUID) = setFeedback(
        trackId,
        RecommendationFeedbackAction.SNOOZE,
        "Hidden from Smart suggestions for 24 hours",
    )

    fun undoFeedback(trackId: UUID) {
        viewModelScope.launch {
            feedback.undo(trackId)
            _state.value = RecommendationActionState(feedbackNotice = "Recommendation feedback removed")
        }
    }

    private fun setFeedback(trackId: UUID, action: RecommendationFeedbackAction, notice: String) {
        viewModelScope.launch {
            feedback.set(trackId, action)
            _state.value = RecommendationActionState(feedbackNotice = notice)
        }
    }

    private fun open(
        trackId: UUID,
        why: Boolean,
        knownReasons: List<RecommendationReason>? = null,
        feedbackNotice: String? = null,
    ) {
        load?.cancel()
        _state.value = RecommendationActionState(trackId = trackId, loading = true, why = why, feedbackNotice = feedbackNotice)
        load = viewModelScope.launch {
            try {
                val libraryTracks = withContext(Dispatchers.Default) { library.observeTracks().first().associateBy { it.id } }
                val title = libraryTracks[trackId]?.title ?: "This song"
                _state.value = if (why) RecommendationActionState(
                    trackId = trackId,
                    title = title,
                    why = true,
                    reasons = knownReasons ?: engine.whyThisSong(trackId),
                    feedbackNotice = feedbackNotice,
                ) else {
                    val ids = engine.continueTheVibe(trackId, 15).map { it.trackId }
                    RecommendationActionState(
                        trackId = trackId,
                        title = title,
                        tracks = ids.mapNotNull(libraryTracks::get),
                        feedbackNotice = feedbackNotice,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = RecommendationActionState(
                    trackId = trackId,
                    why = why,
                    error = "Unable to load suggestions. Try again.",
                    feedbackNotice = feedbackNotice,
                )
            }
        }
    }

    fun dismiss() { load?.cancel(); _state.value = RecommendationActionState() }
    fun clearNotice() { _state.value = _state.value.copy(feedbackNotice = null) }

    fun play(trackId: UUID) {
        val ids = _state.value.tracks.map { it.id }
        if (trackId in ids) queue.replaceAndPlay(ids, trackId, PlaybackMode.ORDERED)
        dismiss()
    }

    fun playMix() { _state.value.tracks.firstOrNull()?.id?.let(::play) }
}
