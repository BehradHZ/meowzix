package dev.behradhz.meowzix.feature.recommendation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.history.TimeBucket
import dev.behradhz.meowzix.domain.recommendation.*
import dev.behradhz.meowzix.ui.components.TrackArtwork
import java.util.UUID

fun RecommendationReason.label(): String = when (this) {
    RecommendationReason.STRONG_CURRENT_TIME_AFFINITY -> "You tend to enjoy this song at this time of day"
    RecommendationReason.HIGH_GLOBAL_AFFINITY -> "You often finish this song"
    RecommendationReason.RECENT_MANUAL_SELECTION -> "You've chosen this song recently"
    RecommendationReason.RECENT_REPLAY -> "You've replayed this song recently"
    RecommendationReason.FAVORITE -> "It's one of your favorites"
    RecommendationReason.NOT_PLAYED_RECENTLY -> "You haven't played it recently"
    RecommendationReason.SIMILAR_ARTIST -> "From an artist you've been listening to"
    RecommendationReason.SIMILAR_ALBUM -> "From the same album as your listening anchor"
    RecommendationReason.SIMILAR_AUDIO_PROFILE -> "Its sound is similar to your listening anchor"
    RecommendationReason.UNDER_EXPLORED -> "Exploring a less-played track"
    RecommendationReason.HIGH_MODEL_CONFIDENCE -> "Fits your current listening pattern"
    RecommendationReason.DISCOVERY_PICK -> "A discovery pick to explore your library"
    RecommendationReason.AVOIDED_RECENT_REPETITION -> "Adds variety to your recent listening"
    RecommendationReason.EXPLICIT_MORE_LIKE -> "You asked for more recommendations like this"
    RecommendationReason.INSUFFICIENT_EVIDENCE -> "There isn't enough evidence for a more specific explanation yet"
}
fun RecommendationSection.title(): String = when (kind) {
    RecommendationSectionKind.FOR_YOU_NOW -> "For You Now"
    RecommendationSectionKind.TIME_MIX -> when (timeBucket) {
        TimeBucket.EARLY_MORNING -> "Early Morning Mix"
        TimeBucket.MORNING -> "Morning Mix"
        TimeBucket.AFTERNOON -> "Afternoon Mix"
        TimeBucket.EVENING -> "Evening Mix"
        TimeBucket.NIGHT -> "Night Mix"
        TimeBucket.LATE_NIGHT -> "Late Night Mix"
        null -> "Current Time Mix"
    }
    RecommendationSectionKind.REDISCOVER -> "Rediscover"
    RecommendationSectionKind.HIDDEN_GEMS -> "Hidden Gems"
    RecommendationSectionKind.BECAUSE_YOU_LISTEN_TO -> anchorTitle?.let { "Because You Listen To $it" } ?: "Because You Listen To…"
    RecommendationSectionKind.ON_REPEAT -> "On Repeat"
    RecommendationSectionKind.TRY_AGAIN -> "Try Again"
    RecommendationSectionKind.CONTINUE_THE_VIBE -> "Continue the Vibe"
}

@Composable
fun RecommendationActionButtons(trackId: UUID, modifier: Modifier = Modifier, viewModel: RecommendationActionsViewModel = hiltViewModel()) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { viewModel.why(trackId) }) { Text("Why this song?") }
        TextButton(onClick = { viewModel.continueVibe(trackId) }) { Text("Continue the vibe") }
    }
}

/** One host per screen keeps a track appearing in multiple recommendation sections from opening duplicate dialogs. */
@Composable
fun RecommendationActionDialogs(viewModel: RecommendationActionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecommendationActionContent(
        state = state,
        dismiss = viewModel::dismiss,
        play = viewModel::play,
        playMix = viewModel::playMix,
        moreLikeThis = viewModel::moreLikeThis,
        suggestLess = viewModel::suggestLess,
        snooze = viewModel::snooze,
        undoFeedback = viewModel::undoFeedback,
    )
}

@Composable
fun RecommendationActionContent(
    state: RecommendationActionState,
    dismiss: () -> Unit,
    play: (UUID) -> Unit,
    playMix: () -> Unit,
    moreLikeThis: (UUID) -> Unit,
    suggestLess: (UUID) -> Unit,
    snooze: (UUID) -> Unit,
    undoFeedback: (UUID) -> Unit,
) {
    if (state.trackId == null) {
        val feedbackTrackId = state.feedbackTrackId
        if (state.feedbackNotice != null) {
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Recommendation updated") },
                text = { Text(state.feedbackNotice) },
                confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
                dismissButton = {
                    if (feedbackTrackId != null) {
                        TextButton(onClick = { undoFeedback(feedbackTrackId) }) { Text("Undo") }
                    }
                },
            )
        }
        return
    }

    if (state.why) AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Why this song?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.title.isNotBlank()) Text(state.title, style = MaterialTheme.typography.titleSmall)
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text(state.error)
                    else -> state.reasons.ifEmpty { listOf(RecommendationReason.INSUFFICIENT_EVIDENCE) }.take(4).forEach { Text(it.label()) }
                }
                Text("Personalized on this device from your listening.", style = MaterialTheme.typography.bodySmall)
                FeedbackActions(
                    trackId = state.trackId,
                    moreLikeThis = moreLikeThis,
                    suggestLess = suggestLess,
                    snooze = snooze,
                )
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("Done") } },
    ) else ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Continue the Vibe", style = MaterialTheme.typography.headlineSmall)
            Text(state.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            state.feedbackNotice?.let { notice ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(notice, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    state.feedbackTrackId?.let { id ->
                        TextButton(onClick = { undoFeedback(id) }) { Text("Undo") }
                    }
                }
            }
            FeedbackActions(
                trackId = state.trackId,
                moreLikeThis = moreLikeThis,
                suggestLess = suggestLess,
                snooze = snooze,
            )
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(20.dp))
                state.error != null -> Text(state.error)
                state.tracks.isEmpty() -> Text("More music or listening history will help us find a matching mix.")
                else -> {
                    Button(onClick = playMix) { Text("Play mix") }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 450.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(state.tracks, key = { it.id }) { track ->
                            Row(Modifier.fillMaxWidth().clickable { play(track.id) }.padding(vertical = 8.dp)) {
                                TrackArtwork(track.artworkRef, track.title, 48.dp)
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist ?: "Unknown artist", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FeedbackActions(
    trackId: UUID,
    moreLikeThis: (UUID) -> Unit,
    suggestLess: (UUID) -> Unit,
    snooze: (UUID) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { moreLikeThis(trackId) }) { Text("More like this") }
        Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { suggestLess(trackId) }) { Text("Suggest less") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { snooze(trackId) }) { Text("Don't suggest for 24 hours") }
        }
    }
}
