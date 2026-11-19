package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class WearUpdaterTest {
    @Test fun latestStableWithoutTag() {
        assertEquals("https://api.github.com/repos/a/b/releases/latest", WearUpdater.releaseUrl("a/b", null))
        assertEquals("https://api.github.com/repos/a/b/releases/latest", WearUpdater.releaseUrl("a/b", ""))
    }

    @Test fun thisBuildsPreReleaseByTag() {
        assertEquals("https://api.github.com/repos/a/b/releases/tags/test-v25-rc4-slim-556",
            WearUpdater.releaseUrl("a/b", "test-v25-rc4-slim-556"))
    }
}
