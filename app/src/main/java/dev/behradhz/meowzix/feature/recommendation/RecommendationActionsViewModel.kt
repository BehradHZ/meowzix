package dev.behradhz.meowzix.feature.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.QueueRepository
import dev.behradhz.meowzix.domain.recommendation.PersonalizationMaintenance
import dev.behradhz.meowzix.domain.recommendation.RecommendationEngine
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackAction
import dev.behradhz.meowzix.domain.recommendation.RecommendationFeedbackRepository
import dev.behradhz.meowzix.domain.recommendation.RecommendationReason
import dev.behradhz.meowzix.domain.settings.SettingsRepository
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

data class RecommendationActionState(
    val trackId: UUID? = null,
    val title: String = "",
    val loading: Boolean = false,
    val why: Boolean = false,
    /** True only for the morphing player-menu panel; it must not open a second dialog. */
    val inlineWhy: Boolean = false,
    val reasons: List<RecommendationReason> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    val feedbackNotice: String? = null,
    val feedbackTrackId: UUID? = null,
    val showInsights: Boolean = false,
    val insightsLoading: Boolean = false,
    val insightsText: String? = null,
)

@HiltViewModel
class RecommendationActionsViewModel @Inject constructor(
    private val engine: RecommendationEngine,
    private val library: MusicLibraryRepository,
    private val queue: QueueRepository,
    private val feedback: RecommendationFeedbackRepository,
    private val maintenance: PersonalizationMaintenance,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RecommendationActionState())
    val state = _state.asStateFlow()
    private var load: Job? = null

    fun why(trackId: UUID, reasons: List<RecommendationReason>? = null, inlineMenu: Boolean = false) =
        open(trackId, true, reasons, inlineWhy = inlineMenu)
    fun continueVibe(trackId: UUID) = open(trackId, false)

    fun openInsights() {
        load?.cancel()
        val previous = _state.value
        _state.value = if (previous.inlineWhy) {
            previous.copy(showInsights = true, insightsLoading = true, insightsText = null, error = null)
        } else {
            RecommendationActionState(showInsights = true, insightsLoading = true)
        }
        load = viewModelScope.launch {
            _state.value = try {
                _state.value.copy(insightsLoading = false, insightsText = maintenance.debugReport())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value.copy(insightsLoading = false, error = "Unable to build local insights.")
            }
        }
    }

    fun closeInlineInsights() {
        if (!_state.value.inlineWhy) return
        load?.cancel()
        _state.value = _state.value.copy(showInsights = false, insightsLoading = false, insightsText = null, error = null)
    }

    fun moreLikeThis(trackId: UUID) {
        viewModelScope.launch {
            val fromInline = isInlineFor(trackId)
            if (!canSaveFeedback()) return@launch
            feedback.set(trackId, RecommendationFeedbackAction.MORE_LIKE_THIS)
            if (fromInline) {
                if (isInlineFor(trackId)) {
                    _state.value = _state.value.copy(feedbackNotice = "More like this saved", feedbackTrackId = trackId)
                }
            } else {
                open(trackId, false, feedbackNotice = "More like this saved", feedbackTrackId = trackId)
            }
        }
    }

    fun suggestLess(trackId: UUID) = setFeedback(trackId, RecommendationFeedbackAction.SUGGEST_LESS, "We'll suggest this less")
    fun snooze(trackId: UUID) = setFeedback(trackId, RecommendationFeedbackAction.SNOOZE, "Hidden from Smart suggestions for 24 hours")

    fun undoFeedback(trackId: UUID) {
        viewModelScope.launch {
            val fromInline = isInlineFor(trackId)
            feedback.undo(trackId)
            if (fromInline) {
                if (isInlineFor(trackId)) {
                    _state.value = _state.value.copy(
                        feedbackNotice = "Recommendation feedback removed", feedbackTrackId = null,
                    )
                }
            } else {
                _state.value = RecommendationActionState(feedbackNotice = "Recommendation feedback removed")
            }
        }
    }

    private suspend fun canSaveFeedback(): Boolean {
        if (settings.recommendationPreferenceSettings.first().smartRecommendationsEnabled) return true
        val notice = "Enable Smart recommendations in Settings to save recommendation feedback."
        _state.value = if (_state.value.inlineWhy) {
            _state.value.copy(feedbackNotice = notice, feedbackTrackId = null)
        } else {
            RecommendationActionState(feedbackNotice = notice)
        }
        return false
    }

    private fun setFeedback(trackId: UUID, action: RecommendationFeedbackAction, notice: String) {
        viewModelScope.launch {
            val fromInline = isInlineFor(trackId)
            if (!canSaveFeedback()) return@launch
            feedback.set(trackId, action)
            if (fromInline) {
                if (isInlineFor(trackId)) {
                    _state.value = _state.value.copy(feedbackNotice = notice, feedbackTrackId = trackId)
                }
            } else {
                _state.value = RecommendationActionState(feedbackNotice = notice, feedbackTrackId = trackId)
            }
        }
    }

    private fun isInlineFor(trackId: UUID) =
        _state.value.inlineWhy && _state.value.trackId == trackId

    private fun open(
        trackId: UUID,
        why: Boolean,
        knownReasons: List<RecommendationReason>? = null,
        feedbackNotice: String? = null,
        feedbackTrackId: UUID? = null,
        inlineWhy: Boolean = false,
    ) {
        load?.cancel()
        _state.value = RecommendationActionState(trackId = trackId, loading = true, why = why,
            inlineWhy = inlineWhy, feedbackNotice = feedbackNotice, feedbackTrackId = feedbackTrackId)
        load = viewModelScope.launch {
            try {
                val libraryTracks = withContext(Dispatchers.Default) { library.observeTracks().first().associateBy { it.id } }
                val title = libraryTracks[trackId]?.title ?: "This song"
                _state.value = if (why) RecommendationActionState(
                    trackId = trackId,
                    title = title,
                    why = true,
                    inlineWhy = inlineWhy,
                    reasons = knownReasons ?: engine.whyThisSong(trackId),
                    feedbackNotice = feedbackNotice,
                    feedbackTrackId = feedbackTrackId,
                ) else {
                    val ids = engine.continueTheVibe(trackId, 15).map { it.trackId }
                    RecommendationActionState(
                        trackId = trackId,
                        title = title,
                        tracks = ids.mapNotNull(libraryTracks::get),
                        feedbackNotice = feedbackNotice,
                        feedbackTrackId = feedbackTrackId,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = RecommendationActionState(trackId = trackId, why = why, inlineWhy = inlineWhy,
                    error = "Unable to load suggestions. Try again.", feedbackNotice = feedbackNotice, feedbackTrackId = feedbackTrackId)
            }
        }
    }

    fun dismiss() { load?.cancel(); _state.value = RecommendationActionState() }

    fun play(trackId: UUID) {
        val ids = _state.value.tracks.map { it.id }
        if (trackId in ids) queue.replaceAndPlay(ids, trackId, PlaybackMode.ORDERED)
        dismiss()
    }

    fun playMix() { _state.value.tracks.firstOrNull()?.id?.let(::play) }
}
