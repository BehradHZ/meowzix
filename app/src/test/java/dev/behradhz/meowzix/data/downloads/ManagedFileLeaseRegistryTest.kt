package dev.behradhz.meowzix.data.downloads

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedFileLeaseRegistryTest {
    @Test
    fun fileRemainsProtectedUntilLastLeaseCloses() {
        val registry = ManagedFileLeaseRegistry()
        val path = File("build/tmp/shared-track.mp3").absolutePath

        val playback = registry.acquire(path, "playback")
        val analysis = registry.acquire(path, "analysis")
        assertTrue(registry.isProtected(path))

        playback.close()
        assertTrue(registry.isProtected(path))

        analysis.close()
        assertFalse(registry.isProtected(path))
    }

    @Test
    fun closingLeaseIsIdempotent() {
        val registry = ManagedFileLeaseRegistry()
        val path = File("build/tmp/idempotent-track.mp3").absolutePath
        val lease = registry.acquire(path, "download")

        lease.close()
        lease.close()

        assertFalse(registry.isProtected(path))
    }
}
