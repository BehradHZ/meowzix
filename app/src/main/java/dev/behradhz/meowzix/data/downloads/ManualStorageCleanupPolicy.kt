package dev.behradhz.meowzix.data.downloads

/** Explicit cleanup cannot evict a completed music file, even without a database record. */
internal object ManualStorageCleanupPolicy {
    fun canRemove(name: String, inUse: Boolean): Boolean = name.endsWith(".part") && !inUse
}
