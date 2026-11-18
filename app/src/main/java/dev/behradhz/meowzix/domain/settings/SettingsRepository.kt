package dev.behradhz.meowzix.domain.settings

import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.domain.playback.RepeatMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class NetworkPlaybackSettings(
    val offlineMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val prefetchEnabled: Boolean = true,
    val prefetchOnMetered: Boolean = false,
    val listeningHistoryEnabled: Boolean = true,
)

data class StoragePolicySettings(
    val temporaryCacheBudgetBytes: Long = DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES,
)

const val DEFAULT_TEMPORARY_CACHE_BUDGET_BYTES: Long = 512L * 1024L * 1024L

data class TelegramForwardSettings(
    val includeSourceAttribution: Boolean = true,
    val keepCaption: Boolean = true,
)

enum class LibrarySortMode { RECENTLY_ADDED, OLDEST_ADDED, TITLE_ASC, TITLE_DESC, ARTIST_ASC }
enum class LibraryGroupMode { NONE, ARTIST, ALBUM, YEAR }

/** Actions for the four fixed Selection Dock positions and optional More menu. */
enum class LibrarySelectionAction(val label: String) {
    DELETE("Delete"),
    PLAY_NEXT("Play next"),
    ADD_TO_PLAYLIST("Add to playlist"),
    SHARE("Forward"),
    ADD_TO_QUEUE("Add to queue"),
    DOWNLOAD("Download"),
    LIKE("Like"),
    UNLIKE("Unlike"),
    PLAY_SELECTED("Play selected"),
    SHUFFLE("Shuffle selected"),
}

val DEFAULT_SELECTION_DOCK = listOf(
    LibrarySelectionAction.DELETE,
    LibrarySelectionAction.PLAY_NEXT,
    LibrarySelectionAction.ADD_TO_PLAYLIST,
    LibrarySelectionAction.SHARE,
)

data class LibrarySelectionSettings(
    val dock: List<LibrarySelectionAction> = DEFAULT_SELECTION_DOCK,
    val more: List<LibrarySelectionAction> = LibrarySelectionAction.entries.filterNot { it in DEFAULT_SELECTION_DOCK },
) {
    /** Defensive normalization for old/corrupt preferences: the dock must always have four unique actions. */
    fun normalized(): LibrarySelectionSettings {
        val pinned = dock.distinct().take(4).toMutableList()
        LibrarySelectionAction.entries.forEach { if (pinned.size < 4 && it !in pinned) pinned.add(it) }
        return copy(dock = pinned, more = more.distinct().filterNot { it in pinned })
    }

    fun setDockSlot(slot: Int, action: LibrarySelectionAction): LibrarySelectionSettings {
        val current = normalized()
        require(slot in 0..3)
        val next = current.dock.toMutableList()
        val existing = next.indexOf(action)
        val displaced = next[slot]
        next[slot] = action
        if (existing >= 0 && existing != slot) next[existing] = displaced
        val nextMore = if (existing >= 0) current.more else
            (current.more.filterNot { it == action } + displaced).distinct()
        return LibrarySelectionSettings(next, nextMore).normalized()
    }

    fun setMoreEnabled(action: LibrarySelectionAction, enabled: Boolean): LibrarySelectionSettings {
        val current = normalized()
        if (action in current.dock) return current
        return current.copy(more = if (enabled) (current.more + action).distinct() else current.more - action)
    }
}

data class LibraryDisplaySettings(
    val sortMode: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
    val groupMode: LibraryGroupMode = LibraryGroupMode.NONE,
    val highlightNewTelegramTracks: Boolean = true,
    val selection: LibrarySelectionSettings = LibrarySelectionSettings(),
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

/** Accent is independent from light/dark. WALLPAPER reads the device's Material You palette. */
enum class ColorThemePreference {
    MEOWZIX, WALLPAPER, CORAL, AMBER, OCEAN, MINT, VIOLET, ROSE, CUSTOM
}

enum class LauncherIconColorSource { APP_THEME, WALLPAPER }
/** Separate system-widget palette; APP follows the in-app appearance settings. */
enum class WidgetThemeMode { APP, WALLPAPER, NOTHING, FULL_COLOR }
enum class PlayerAccentSource { GLOBAL, TRACK }
enum class PlayerSeekBarStyle { WAVEFORM, CLASSIC }

/** The opt-in Live Update complements, rather than replaces, Android's media player. */
enum class LiveSongCardStyle { MINIMAL, CONTROLS }
enum class LiveSongSideAction { PREVIOUS, NEXT, FAVORITE, SHUFFLE, REPEAT }

const val DEFAULT_CUSTOM_COLOR_SEED: Int = 0xFF6750A4.toInt()

data class AppearanceSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val colorTheme: ColorThemePreference = ColorThemePreference.MEOWZIX,
    val customColorSeed: Int = DEFAULT_CUSTOM_COLOR_SEED,
    val lyricsTextScalePercent: Int = 100,
    val reduceMotion: Boolean = false,
    val playerAccentSource: PlayerAccentSource = PlayerAccentSource.GLOBAL,
    val playerSeekBarStyle: PlayerSeekBarStyle = PlayerSeekBarStyle.WAVEFORM,
    val launcherIconSource: LauncherIconColorSource = LauncherIconColorSource.APP_THEME,
    val spermerModeEnabled: Boolean = false,
    val widgetTheme: WidgetThemeMode = WidgetThemeMode.APP,
)

/** Remembers the user's last Sleep Timer mode independently from the running timer. */
enum class SleepTimerPresetMode { DURATION, END_OF_TRACK }

data class PlaybackPreferenceSettings(
    val defaultMode: PlaybackMode = PlaybackMode.ORDERED,
    val artistBalancedShuffle: Boolean = true,
    val artistShuffleAlpha: Float = 0.8f,
    val defaultRepeat: RepeatMode = RepeatMode.OFF,
    val resumeOnLaunch: Boolean = true,
    val resumeAfterExternalAudio: Boolean = false,
    val pauseOnMicrophoneRecording: Boolean = false,
    val playbackSpeed: Float = 1f,
    val liveSongCardEnabled: Boolean = false,
    val liveSongCardStyle: LiveSongCardStyle = LiveSongCardStyle.MINIMAL,
    val liveSongLeftAction: LiveSongSideAction = LiveSongSideAction.PREVIOUS,
    val liveSongRightAction: LiveSongSideAction = LiveSongSideAction.NEXT,
    val loudnessNormalizationEnabled: Boolean = false,
    val crossfadeDurationSeconds: Int = 0,
    val sleepTimerFadeEnabled: Boolean = true,
    val sleepTimerLastMode: SleepTimerPresetMode = SleepTimerPresetMode.DURATION,
    val sleepTimerLastMinutes: Int = 30,
)

data class RecommendationPreferenceSettings(
    val smartRecommendationsEnabled: Boolean = true,
    val explorationPercent: Int = 15,
    val audioAnalysisEnabled: Boolean = true,
    val diagnosticsVisible: Boolean = false,
)

interface SettingsRepository {
    val networkPlaybackSettings: Flow<NetworkPlaybackSettings>
    val storagePolicySettings: Flow<StoragePolicySettings>
    val telegramForwardSettings: Flow<TelegramForwardSettings>
    val libraryDisplaySettings: Flow<LibraryDisplaySettings>
    val appearanceSettings: Flow<AppearanceSettings>
    val playbackPreferenceSettings: Flow<PlaybackPreferenceSettings>
    val recentShuffleTrackIds: Flow<List<UUID>> get() = flowOf(emptyList())
    val recommendationPreferenceSettings: Flow<RecommendationPreferenceSettings>

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setPrefetchEnabled(enabled: Boolean)
    suspend fun setPrefetchOnMetered(enabled: Boolean)
    suspend fun setListeningHistoryEnabled(enabled: Boolean)
    suspend fun setTemporaryCacheBudgetBytes(bytes: Long)
    suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean)
    suspend fun setLibrarySortMode(mode: LibrarySortMode)
    suspend fun setLibraryGroupMode(mode: LibraryGroupMode)
    suspend fun setHighlightNewTelegramTracks(enabled: Boolean) {}
    suspend fun setLibrarySelectionSettings(value: LibrarySelectionSettings) {}
    suspend fun setWidgetTheme(mode: WidgetThemeMode)
    suspend fun setThemePreference(theme: ThemePreference)
    suspend fun setColorThemePreference(colorTheme: ColorThemePreference)
    /** Saves the opaque RGB seed and selects CUSTOM atomically. */
    suspend fun setCustomColorSeed(argb: Int)
    suspend fun setLyricsTextScalePercent(percent: Int)
    suspend fun setReduceMotion(enabled: Boolean)
    suspend fun setPlayerAccentSource(source: PlayerAccentSource)
    suspend fun setPlayerSeekBarStyle(style: PlayerSeekBarStyle)
    suspend fun setLauncherIconSource(source: LauncherIconColorSource) {}
    suspend fun setSpermerModeEnabled(enabled: Boolean) {}
    suspend fun setArtistBalancedShuffle(enabled: Boolean) {}
    suspend fun setArtistShuffleAlpha(alpha: Float) {}
    suspend fun recordShuffleExposure(trackId: UUID) {}
    suspend fun setDefaultPlaybackMode(mode: PlaybackMode)
    suspend fun setDefaultRepeatMode(mode: RepeatMode)
    suspend fun setResumeOnLaunch(enabled: Boolean)
    suspend fun setResumeAfterExternalAudio(enabled: Boolean)
    suspend fun setPauseOnMicrophoneRecording(enabled: Boolean) {}
    suspend fun setPlaybackSpeed(speed: Float) {}
    suspend fun setLiveSongCardEnabled(enabled: Boolean)
    suspend fun setLiveSongCardStyle(style: LiveSongCardStyle)
    suspend fun setLiveSongLeftAction(action: LiveSongSideAction)
    suspend fun setLiveSongRightAction(action: LiveSongSideAction)
    suspend fun setLoudnessNormalizationEnabled(enabled: Boolean)
    suspend fun setCrossfadeDurationSeconds(seconds: Int)
    suspend fun setSleepTimerFadeEnabled(enabled: Boolean)
    suspend fun saveSleepTimerPreset(mode: SleepTimerPresetMode, minutes: Int)
    suspend fun setSmartRecommendationsEnabled(enabled: Boolean)
    suspend fun setExplorationPercent(percent: Int)
    suspend fun setAudioAnalysisEnabled(enabled: Boolean)
    suspend fun setDiagnosticsVisible(enabled: Boolean)
}
