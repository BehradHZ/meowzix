package dev.behradhz.meowzix.playback

import android.content.Context
import android.media.MediaRoute2Info
import android.media.MediaRouter
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.playback.AudioOutputController
import dev.behradhz.meowzix.domain.playback.AudioOutputRoute
import dev.behradhz.meowzix.domain.playback.AudioOutputState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidAudioOutputController @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioOutputController {
    private val _state = MutableStateFlow(AudioOutputState())
    override val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    private var mediaRouter2: MediaRouter2? = null
    private var legacyMediaRouter: MediaRouter? = null
    private var routes2ById: Map<String, MediaRoute2Info> = emptyMap()
    private var legacyRoutesById: Map<String, MediaRouter.RouteInfo> = emptyMap()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            initializeMediaRouter2()
        } else {
            initializeLegacyMediaRouter()
        }
        refresh()
    }

    override fun refresh() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            refreshMediaRouter2()
        } else {
            refreshLegacyMediaRouter()
        }
    }

    override fun transferTo(routeId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val router = mediaRouter2 ?: return
            val route = routes2ById[routeId] ?: return
            runCatching { router.transferTo(route) }
                .onFailure { publishError(it.message ?: "Unable to switch audio output.") }
        } else {
            @Suppress("DEPRECATION")
            val router = legacyMediaRouter ?: return
            val route = legacyRoutesById[routeId] ?: return
            runCatching {
                @Suppress("DEPRECATION")
                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, route)
            }.onFailure { publishError(it.message ?: "Unable to switch audio output.") }
        }
        refresh()
    }

    override fun setRouteEnabled(routeId: String, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (enabled) transferTo(routeId)
            return
        }

        val router = mediaRouter2 ?: return
        val route = routes2ById[routeId] ?: return
        val controller = router.systemController
        runCatching {
            if (enabled) {
                when {
                    controller.selectedRoutes.any { it.id == routeId } -> Unit
                    controller.selectableRoutes.any { it.id == routeId } -> controller.selectRoute(route)
                    else -> router.transferTo(route)
                }
            } else if (controller.deselectableRoutes.any { it.id == routeId }) {
                controller.deselectRoute(route)
            }
        }.onFailure { publishError(it.message ?: "Unable to update audio outputs.") }
        refresh()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun initializeMediaRouter2() {
        val router = MediaRouter2.getInstance(context)
        mediaRouter2 = router
        val executor = context.mainExecutor
        router.registerRouteCallback(
            executor,
            object : MediaRouter2.RouteCallback() {
                override fun onRoutesAdded(router: MediaRouter2, routes: List<MediaRoute2Info>) = refreshMediaRouter2()
                override fun onRoutesChanged(router: MediaRouter2, routes: List<MediaRoute2Info>) = refreshMediaRouter2()
                override fun onRoutesRemoved(router: MediaRouter2, routes: List<MediaRoute2Info>) = refreshMediaRouter2()
            },
            RouteDiscoveryPreference.Builder(
                listOf(MediaRoute2Info.FEATURE_LIVE_AUDIO),
                false,
            ).build(),
        )
        router.registerControllerCallback(
            executor,
            object : MediaRouter2.ControllerCallback() {
                override fun onControllerUpdated(controller: MediaRouter2.RoutingController) = refreshMediaRouter2()
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun initializeLegacyMediaRouter() {
        val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
        legacyMediaRouter = router
        router.addCallback(
            MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
            object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshLegacyMediaRouter()
                override fun onRouteRemoved(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshLegacyMediaRouter()
                override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refreshLegacyMediaRouter()
                override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refreshLegacyMediaRouter()
                override fun onRouteUnselected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refreshLegacyMediaRouter()
            },
            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun refreshMediaRouter2() {
        val router = mediaRouter2 ?: return
        val controller = router.systemController
        val selected = controller.selectedRoutes
        val selectable = controller.selectableRoutes
        val deselectable = controller.deselectableRoutes
        val transferable = controller.transferableRoutes
        val selectedIds = selected.mapTo(mutableSetOf()) { it.id }
        val selectableIds = selectable.mapTo(mutableSetOf()) { it.id }
        val deselectableIds = deselectable.mapTo(mutableSetOf()) { it.id }

        val routes = buildList {
            addAll(selected)
            addAll(selectable)
            addAll(deselectable)
            addAll(transferable)
            addAll(router.routes.filter { MediaRoute2Info.FEATURE_LIVE_AUDIO in it.features })
        }.distinctBy { it.id }

        routes2ById = routes.associateBy { it.id }
        _state.value = AudioOutputState(
            routes = routes.map { route ->
                AudioOutputRoute(
                    id = route.id,
                    name = route.name.toString(),
                    isSelected = route.id in selectedIds,
                    canSelectTogether = route.id in selectableIds,
                    canDeselect = route.id in deselectableIds,
                )
            },
            supportsSimultaneousPlayback =
                selected.size > 1 || selectable.isNotEmpty() || deselectable.isNotEmpty(),
        )
    }

    @Suppress("DEPRECATION")
    private fun refreshLegacyMediaRouter() {
        val router = legacyMediaRouter ?: return
        val selected = router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
        val mapped = buildList {
            for (index in 0 until router.routeCount) {
                val route = router.getRouteAt(index)
                if (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_AUDIO == 0) continue
                val id = "legacy:$index:${route.name}"
                add(id to route)
            }
        }
        legacyRoutesById = mapped.toMap()
        _state.value = AudioOutputState(
            routes = mapped.map { (id, route) ->
                AudioOutputRoute(
                    id = id,
                    name = route.name.toString(),
                    isSelected = route == selected,
                    canSelectTogether = false,
                    canDeselect = false,
                )
            },
            supportsSimultaneousPlayback = false,
        )
    }

    private fun publishError(message: String) {
        _state.value = _state.value.copy(errorMessage = message)
    }
}
