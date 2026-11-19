package dev.behradhz.meowzix.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.domain.playback.ProgressiveQueue
import dev.behradhz.meowzix.domain.playback.ProgressiveQueueSnapshot

/** Access the exact singleton powering QueueRoute, even from an AppWidgetProvider. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetQueueEntryPoint {
    fun progressiveQueue(): ProgressiveQueue
}

internal fun widgetLogicalQueue(context: Context): ProgressiveQueueSnapshot? =
    EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetQueueEntryPoint::class.java,
    ).progressiveQueue().snapshot()
