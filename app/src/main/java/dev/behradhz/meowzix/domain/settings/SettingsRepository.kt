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

/** Telegram person playlists have two independent sections and independent saved states. */
enum class TelegramPlaylistSection { PROFILE_MUSIC, SHARED_IN_CHAT }

fun telegramPlaylistSectionKey(playlistId: UUID, section: TelegramPlaylistSection): String =
    "$playlistId:${section.name}"

fun isTelegramPlaylistSectionExpanded(
    collapsedSections: Set<String>,
    playlistId: UUID,
    section: TelegramPlaylistSection,
): Boolean = telegramPlaylistSectionKey(playlistId, section) !in collapsedSections

/** Actions for the four fixed Selection Dock positions and optional More menu. */
enum class LibrarySelectionAction(val label: String) {
    DELETE("Delete"),
    PLAY_NEXT("Play next"),
    ADD_TO_PLAYLIST("Add to playlist"),
    SHARE("Forward"),
    ADD_TO_QUEUE("Add to queue"),
    DOWNLOAD("Download"),
    LIKE("Like / Unlike"),
    PLAY_SELECTED("Play selected"),
    SHUFFLE("Shuffle selected"),
}

/** One favorite action whose label follows the selected tracks. */
fun LibrarySelectionAction.selectionLabel(allSelectedLiked: Boolean): String =
    if (this == LibrarySelectionAction.LIKE) {
        if (allSelectedLiked) "Unlike" else "Like"
    } else label

/** A mixed selection is always liked first; only an all-liked selection is unliked. */
fun selectionShouldLike(favoriteStates: Collection<Boolean>): Boolean =
    favoriteStates.any { !it }

/** Old preferences used the separate UNLIKE action. */
fun librarySelectionActionFromName(name: String): LibrarySelectionAction? =
    if (name == "UNLIKE") LibrarySelectionAction.LIKE
    else LibrarySelectionAction.entries.firstOrNull { it.name == name }

val DEFAULT_SELECTION_DOCK = listOf(
    LibrarySelectionAction.DELETE,
    LibrarySelectionAction.PLAY_NEXT,
    LibrarySelectionAction.ADD_TO_PLAYLIST,
    LibrarySelectionAction.SHARE,
)

data class LibrarySelectionSettings(
    val dock: List<LibrarySelectionAction> = DEFAULT_SELECTION_DOCK,
    /** Enabled More actions, stored in menu order. */
    val more: List<LibrarySelectionAction> =
        LibrarySelectionAction.entries.filterNot { it in DEFAULT_SELECTION_DOCK },
    /** Every unpinned action including disabled entries, preserving drag order. */
    val moreOrder: List<LibrarySelectionAction> =
        LibrarySelectionAction.entries.filterNot { it in DEFAULT_SELECTION_DOCK },
) {
    fun normalized(): LibrarySelectionSettings {
        val pinned = dock.distinct().take(4).toMutableList()
        LibrarySelectionAction.entries.forEach { action ->
            if (pinned.size < 4 && action !in pinned) pinned.add(action)
        }
        val order = (moreOrder + more + LibrarySelectionAction.entries)
            .distinct().filterNot { it in pinned }
        return copy(dock = pinned, more = order.filter { it in more }, moreOrder = order)
    }

    fun setDockSlot(slot: Int, action: LibrarySelectionAction): LibrarySelectionSettings {
        val current = normalized()
        require(slot in 0..3)
        val next = current.dock.toMutableList()
        val existing = next.indexOf(action)
        val displaced = next[slot]
        if (displaced == action) return current
        next[slot] = action
        if (existing >= 0) next[existing] = displaced
        val nextMore = if (existing >= 0) current.more else
            (current.more.filterNot { it == action } + displaced).distinct()
        val nextOrder = current.moreOrder.toMutableList()
        if (existing < 0) {
            val index = nextOrder.indexOf(action)
            if (index >= 0) nextOrder[index] = displaced else nextOrder.add(displaced)
        }
        return LibrarySelectionSettings(next, nextMore, nextOrder).normalized()
    }

    fun setMoreEnabled(action: LibrarySelectionAction, enabled: Boolean): LibrarySelectionSettings {
        val current = normalized()
        if (action in current.dock) return current
        return current.copy(more = if (enabled) current.more + action else current.more - action).normalized()
    }

    fun moveMoreAction(action: LibrarySelectionAction, targetIndex: Int): LibrarySelectionSettings {
        val current = normalized()
        val from = current.moreOrder.indexOf(action)
        if (from < 0 || targetIndex !in current.moreOrder.indices) return current
        val next = current.moreOrder.toMutableList()
        next.removeAt(from)
        next.add(targetIndex, action)
        return current.copy(moreOrder = next).normalized()
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
    val volumeButtonTrackNavigationEnabled: Boolean = false,
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
    val collapsedTelegramPlaylistSections: Flow<Set<String>> get() = flowOf(emptySet())
    /** Whether the user has chosen an initial music source on the first Home visit. */
    val initialSourceChoiceCompleted: Flow<Boolean> get() = flowOf(false)
    /** A dismissed Home Telegram reminder stays dismissed across app restarts. */
    val telegramConnectReminderDismissed: Flow<Boolean> get() = flowOf(false)
    val appearanceSettings: Flow<AppearanceSettings>
    val playbackPreferenceSettings: Flow<PlaybackPreferenceSettings>
    val recentShuffleTrackIds: Flow<List<UUID>> get() = flowOf(emptyList())
    val recommendationPreferenceSettings: Flow<RecommendationPreferenceSettings>

    suspend fun setOfflineMode(enabled: Boolean)
    suspend fun setWifiOnlyDownloads(enabled: Boolean)
    suspend fun setPrefetchEnabled(enabled: Boolean)
    suspend fun setPrefetchOnMetered(enabled: Boolean)
    suspend fun setListeningHistoryEnabled(enabled: Boolean)
    suspend fun setInitialSourceChoiceCompleted() {}
    suspend fun dismissTelegramConnectReminder() {}
    suspend fun setTemporaryCacheBudgetBytes(bytes: Long)
    suspend fun setTelegramForwardDefaults(includeSourceAttribution: Boolean, keepCaption: Boolean)
    suspend fun setLibrarySortMode(mode: LibrarySortMode)
    suspend fun setLibraryGroupMode(mode: LibraryGroupMode)
    suspend fun setHighlightNewTelegramTracks(enabled: Boolean) {}
    suspend fun setLibrarySelectionSettings(value: LibrarySelectionSettings) {}
    suspend fun setTelegramPlaylistSectionExpanded(
        playlistId: UUID,
        section: TelegramPlaylistSection,
        expanded: Boolean,
    ) {}
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
    suspend fun setVolumeButtonTrackNavigationEnabled(enabled: Boolean)
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
