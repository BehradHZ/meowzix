package dev.behradhz.meowzix.domain.playback

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Notifies live Media3 playback that the source was confirmed unavailable.
 * The database MISSING availability flag is authoritative; this signal only
 * prevents an already-open TDLib stream from waiting on a deleted message.
 */
@Singleton
class DeletedTelegramSourceEvents @Inject constructor() {
    private val _deleted = MutableSharedFlow<UUID>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val deleted: SharedFlow<UUID> = _deleted.asSharedFlow()

    fun notifyDeleted(trackId: UUID) {
        _deleted.tryEmit(trackId)
    }
}
