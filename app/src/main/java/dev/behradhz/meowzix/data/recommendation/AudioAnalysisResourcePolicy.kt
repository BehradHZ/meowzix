package dev.behradhz.meowzix.data.recommendation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conservative gate for optional acoustic analysis, not a global CPU/RAM quota.
 * Processing also has single-flight serialization, bounded PCM buffers and a decode deadline.
 * When conditions are unfavorable we skip; a later playback can schedule another attempt.
 */
@Singleton
class AudioAnalysisResourcePolicy @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun mayAnalyze(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isPowerSaveMode == true) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            power != null && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        ) return false

        val memoryManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (memoryManager != null) {
            val memory = ActivityManager.MemoryInfo()
            memoryManager.getMemoryInfo(memory)
            if (memory.lowMemory || memory.availMem < MIN_AVAILABLE_MEMORY_BYTES) return false
        }
        return true
    }

    companion object {
        const val MIN_AVAILABLE_MEMORY_BYTES = 128L * 1024L * 1024L
    }
}
