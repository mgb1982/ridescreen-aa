package dev.zanderp.opencfmoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkeLivePolicyTest {
    @Test fun obdVoltageMeansNoBoxLinkForIt() {
        assertTrue(PkeLivePolicy.useObdVolt(13.9))
        assertFalse(PkeLivePolicy.useObdVolt(null))
    }
    @Test fun lockStateEvery30s() {
        assertFalse(PkeLivePolicy.lockDue(29_999))
        assertTrue(PkeLivePolicy.lockDue(30_000))
    }
}
