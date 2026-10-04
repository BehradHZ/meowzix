package dev.behradhz.meowzix.data.downloads

import dev.behradhz.meowzix.domain.downloads.ManagedFileLease
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Protects app-managed files while playback/download/analysis has an active reader or writer. */
@Singleton
class ManagedFileLeaseRegistry @Inject constructor() {
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun acquire(path: String, owner: String): ManagedFileLease {
        val normalized = normalize(path)
        counts.computeIfAbsent(normalized) { AtomicInteger() }.incrementAndGet()
        return Lease(normalized, owner) { releasedPath ->
            counts.computeIfPresent(releasedPath) { _, count ->
                if (count.decrementAndGet() <= 0) null else count
            }
        }
    }

    fun isProtected(path: String): Boolean = counts[normalize(path)]?.get()?.let { it > 0 } == true

    fun protectedPaths(): Set<String> = counts
        .filterValues { it.get() > 0 }
        .keys
        .toSet()

    private fun normalize(path: String): String = runCatching { File(path).canonicalPath }
        .getOrElse { File(path).absolutePath }

    private class Lease(
        override val path: String,
        override val owner: String,
        private val release: (String) -> Unit,
    ) : ManagedFileLease {
        private val closed = AtomicBoolean(false)
        override fun close() {
            if (closed.compareAndSet(false, true)) release(path)
        }
    }
}
