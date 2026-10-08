package dev.behradhz.meowzix.playback

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.behradhz.meowzix.domain.playback.LoudnessAlgorithm
import dev.behradhz.meowzix.domain.playback.LoudnessAnalysis
import dev.behradhz.meowzix.domain.playback.PlaybackGainCoordinator
import dev.behradhz.meowzix.domain.playback.ReplayGainParser

@OptIn(markerClass = [UnstableApi::class])
object ReplayGainMetadataParser {
    fun parse(metadata: Metadata): LoudnessAnalysis? {
        var albumGain: Float? = null
        for (index in 0 until metadata.length()) {
            val entry = metadata[index]
            val field = when (entry) {
                is VorbisComment -> entry.key to entry.value
                is TextInformationFrame -> {
                    if (entry.id != "TXXX" || entry.description.isNullOrBlank()) null
                    else entry.description!! to entry.values.firstOrNull().orEmpty()
                }
                is InternalFrame -> entry.description to entry.text
                else -> null
            } ?: continue
            val key = field.first.trim().uppercase()
            val gain = ReplayGainParser.parseDb(field.second) ?: continue
            val bounded = gain.coerceIn(
                PlaybackGainCoordinator.MIN_NORMALIZATION_DB,
                PlaybackGainCoordinator.MAX_NORMALIZATION_DB,
            )
            when (key) {
                "REPLAYGAIN_TRACK_GAIN", "REPLAY_GAIN_TRACK_GAIN" ->
                    return LoudnessAnalysis(
                        measuredDb = null,
                        suggestedGainDb = bounded,
                        algorithm = LoudnessAlgorithm.REPLAY_GAIN_TRACK,
                        algorithmVersion = "replaygain-track-v1",
                        confidence = 1f,
                    )
                "REPLAYGAIN_ALBUM_GAIN", "REPLAY_GAIN_ALBUM_GAIN" -> albumGain = bounded
            }
        }
        return albumGain?.let { gain ->
            LoudnessAnalysis(
                measuredDb = null,
                suggestedGainDb = gain,
                algorithm = LoudnessAlgorithm.REPLAY_GAIN_ALBUM,
                algorithmVersion = "replaygain-album-v1",
                confidence = 1f,
            )
        }
    }
}
