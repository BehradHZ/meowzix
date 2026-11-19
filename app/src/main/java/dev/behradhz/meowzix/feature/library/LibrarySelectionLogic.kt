package dev.behradhz.meowzix.feature.library

import java.util.UUID

/** Toggle a single track or an entire collection without duplicating canonical IDs. */
internal fun <T> toggleSelectedEntries(
    selected: Map<UUID, T>,
    entries: Map<UUID, T>,
): Map<UUID, T> = when {
    entries.isEmpty() -> selected
    entries.keys.all(selected::containsKey) -> selected - entries.keys
    else -> selected + entries
}
