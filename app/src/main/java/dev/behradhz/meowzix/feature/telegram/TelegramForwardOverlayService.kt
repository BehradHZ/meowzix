package dev.behradhz.meowzix.feature.telegram

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramSendEnqueueResult
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A real TYPE_APPLICATION_OVERLAY, not an Activity pretending to float.
 * Started by the short-lived NoDisplay notification gateway after permission is granted.
 * Kept for the duration of the picker; dismissal removes the entire window and stops this service.
 */
@AndroidEntryPoint
class TelegramForwardOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var forward: TelegramForwardRepository
    @Inject lateinit var settings: SettingsRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _ui = MutableStateFlow(OverlayForwardState())
    private val ui = _ui.asStateFlow()
    private var searchJob: Job? = null
    private var view: ComposeView? = null
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // No bypass if the permission was revoked after the user returned from Android Settings.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val incoming = intent?.getStringExtra(EXTRA_TRACK_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val live = playback.state.value.currentTrack
        val snapshot = when {
            incoming != null -> NowPlayingTrack(
                id = incoming,
                title = intent.getStringExtra(EXTRA_TITLE)?.takeIf(String::isNotBlank)
                    ?: live?.takeIf { it.id == incoming }?.title ?: "Current track",
                artist = intent.getStringExtra(EXTRA_ARTIST) ?: live?.takeIf { it.id == incoming }?.artist,
                artworkRef = live?.takeIf { it.id == incoming }?.artworkRef,
            )
            else -> live
        }
        if (snapshot == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null && !showWindow()) {
            stopSelf()
            return START_NOT_STICKY
        }
        searchJob?.cancel()
        _ui.value = OverlayForwardState(track = snapshot)
        search("")
        scope.launch {
            runCatching { settings.telegramForwardSettings.first() }
                .onSuccess { defaults -> _ui.update { if (it.track?.id == snapshot.id) it.copy(defaults = defaults) else it } }
        }
        return START_NOT_STICKY
    }

    private fun showWindow(): Boolean = runCatching {
        val manager = getSystemService(WindowManager::class.java)
        val compose = ComposeView(this)
        compose.setViewTreeLifecycleOwner(this)
        compose.setViewTreeViewModelStoreOwner(this)
        compose.setViewTreeSavedStateRegistryOwner(this)
        compose.setContent { OverlayContent() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "Meowzix Telegram sharing"
        }
        manager.addView(compose, params)
        windowManager = manager
        view = compose
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        overlayWindowVisible = true
        true
    }.getOrElse {
        // The OS can revoke overlay access independently; never leave an orphaned service.
        false
    }

    @Composable
    private fun OverlayContent() {
        val state by ui.collectAsState()
        MeowzixTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                // Transparent touch shield: dismiss outside the sheet without tinting the
                // user's previous app. Only the floating card casts a shadow.
                Box(Modifier.fillMaxSize().clickable { dismiss() })
                state.track?.let { selected ->
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val availableHeight = maxHeight
                        Box(
                            modifier = Modifier
                                .widthIn(max = 460.dp)
                                .fillMaxWidth()
                                .shadow(24.dp, RoundedCornerShape(28.dp), clip = false)
                                .testTag("telegram-forward-centered-overlay")
                                // Prevent an empty part of the card from hitting the dismiss scrim.
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {},
                        ) {
                            TelegramForwardSheet(
                                trackId = selected.id,
                                title = selected.title,
                                artist = selected.artist,
                                query = state.query,
                                chats = state.chats,
                                isSearching = state.isSearching,
                                isSending = state.isSending,
                                errorMessage = state.errorMessage,
                                defaults = state.defaults,
                                onQueryChange = ::search,
                                onForward = ::send,
                                onDismiss = ::dismiss,
                                floatingOverlay = true,
                                overlayMaxHeight = availableHeight * 0.82f,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        _ui.update { it.copy(query = query, isSearching = true, errorMessage = null) }
        searchJob = scope.launch {
            delay(180)
            try {
                val chats = forward.searchChats(query)
                _ui.update { if (it.query == query) it.copy(chats = chats, isSearching = false) else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _ui.update {
                    if (it.query == query) it.copy(
                        isSearching = false,
                        errorMessage = "Unable to load Telegram chats. Check the connection and retry.",
                    ) else it
                }
            }
        }
    }

    private fun send(chatId: Long, options: TelegramForwardOptions, rememberDefaults: Boolean) {
        val current = _ui.value
        val track = current.track ?: return
        if (current.isSending) return
        val title = current.chats.firstOrNull { it.chatId == chatId }?.title
        _ui.update { it.copy(isSending = true, errorMessage = null) }
        scope.launch {
            try {
                if (rememberDefaults) settings.setTelegramForwardDefaults(
                    options.includeSourceAttribution, options.keepCaption,
                )
                val result = forward.enqueueTrack(track.id, chatId, title, options)
                val message = when (result) {
                    is TelegramSendEnqueueResult.Queued -> null
                    is TelegramSendEnqueueResult.AlreadyQueued -> "Already queued for ${result.job.targetTitle ?: "this chat"}."
                    is TelegramSendEnqueueResult.AlreadyInChat -> "Already sent to ${result.targetTitle ?: "this chat"}."
                }
                _ui.update { if (it.track?.id == track.id) it.copy(isSending = false, errorMessage = message) else it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _ui.update { if (it.track?.id == track.id) it.copy(
                    isSending = false, errorMessage = "Unable to queue this Telegram send. Try again.",
                ) else it }
            }
        }
    }

    private fun dismiss() { stopSelf() }
    override fun onDestroy() {
        overlayWindowVisible = false
        searchJob?.cancel()
        scope.cancel()
        view?.let { compose ->
            compose.disposeComposition()
            runCatching { windowManager?.removeViewImmediate(compose) }
        }
        view = null
        windowManager = null
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Device-test evidence that the WindowManager window was attached, not just the gateway. */
        @Volatile
        internal var overlayWindowVisible = false
            private set

        private const val EXTRA_TRACK_ID = "forward_track_id"
        private const val EXTRA_TITLE = "forward_track_title"
        private const val EXTRA_ARTIST = "forward_track_artist"

        fun intent(context: Context, trackId: String?, title: String?, artist: String?): Intent =
            Intent(context, TelegramForwardOverlayService::class.java).apply {
                putExtra(EXTRA_TRACK_ID, trackId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
            }
    }
}

private data class OverlayForwardState(
    val track: NowPlayingTrack? = null,
    val query: String = "",
    val chats: List<TelegramChatSummary> = emptyList(),
    val isSearching: Boolean = false,
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val defaults: TelegramForwardSettings = TelegramForwardSettings(),
)
