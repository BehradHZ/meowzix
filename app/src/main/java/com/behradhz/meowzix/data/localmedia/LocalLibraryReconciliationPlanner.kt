package com.behradhz.meowzix.data.localmedia

import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity

data class LocalLibraryReconciliationPlan(
    val discovered: List<ScannedLocalTrack>,
    val staleSources: List<LocalMediaSourceEntity>,
)

object LocalLibraryReconciliationPlanner {
    fun plan(
        scanned: List<ScannedLocalTrack>,
        existing: List<LocalMediaSourceEntity>,
    ): LocalLibraryReconciliationPlan {
        val scannedByUri = scanned.associateBy { it.contentUri }
        val stale = existing.filter { it.contentUri !in scannedByUri }

        return LocalLibraryReconciliationPlan(
            discovered = scannedByUri.values.toList(),
            staleSources = stale,
        )
    }
}
