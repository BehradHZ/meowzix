package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.data.localmedia.ScannedLocalTrack

data class ExistingLocalSourceSnapshot(
    val sourceId: String,
    val trackId: String,
    val contentUri: String,
)

data class MatchedLocalTrack(
    val scanned: ScannedLocalTrack,
    val existing: ExistingLocalSourceSnapshot,
)

data class LocalLibraryReconciliationPlan(
    val toCreate: List<ScannedLocalTrack>,
    val toUpdate: List<MatchedLocalTrack>,
    val missingSourceIds: List<String>,
) {
    val discovered: Int get() = toCreate.size + toUpdate.size
}

object LocalLibraryReconciler {
    fun plan(
        scanned: List<ScannedLocalTrack>,
        existing: List<ExistingLocalSourceSnapshot>,
    ): LocalLibraryReconciliationPlan {
        val existingByUri = existing.associateBy { it.contentUri }
        val seenUris = HashSet<String>(scanned.size)
        val toCreate = ArrayList<ScannedLocalTrack>()
        val toUpdate = ArrayList<MatchedLocalTrack>()

        for (item in scanned) {
            if (!seenUris.add(item.contentUri)) continue

            val current = existingByUri[item.contentUri]
            if (current == null) {
                toCreate += item
            } else {
                toUpdate += MatchedLocalTrack(item, current)
            }
        }

        val missingSourceIds = existing.asSequence()
            .filter { it.contentUri !in seenUris }
            .map { it.sourceId }
            .toList()

        return LocalLibraryReconciliationPlan(
            toCreate = toCreate,
            toUpdate = toUpdate,
            missingSourceIds = missingSourceIds,
        )
    }
}
