package dev.behradhz.meowzix.domain.playback

import kotlinx.coroutines.flow.StateFlow

data class AudioOutputRoute(
    val id: String,
    val name: String,
    val isSelected: Boolean,
    val canSelectTogether: Boolean,
    val canDeselect: Boolean,
)

data class AudioOutputState(
    val routes: List<AudioOutputRoute> = emptyList(),
    val supportsSimultaneousPlayback: Boolean = false,
    val errorMessage: String? = null,
)

interface AudioOutputController {
    val state: StateFlow<AudioOutputState>

    fun refresh()
    fun transferTo(routeId: String)
    fun setRouteEnabled(routeId: String, enabled: Boolean)
}
