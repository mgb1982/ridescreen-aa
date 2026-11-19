package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class WearUpdaterTest {
    @Test fun latestStableWithoutTag() {
        assertEquals("https://api.github.com/repos/a/b/releases/latest", WearReleaseUrl.of("a/b", null))
        assertEquals("https://api.github.com/repos/a/b/releases/latest", WearReleaseUrl.of("a/b", ""))
    }

    @Test fun thisBuildsPreReleaseByTag() {
        assertEquals("https://api.github.com/repos/a/b/releases/tags/test-v25-rc4-slim-556",
            WearReleaseUrl.of("a/b", "test-v25-rc4-slim-556"))
    }
}
