package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClockLabTest {
    @Before
    fun resetDefaults() {
        ClockLab.applyFrom(ClockQueryMode.EMPTY, ClockTimeSyncMode.ECHO, false, keepWifi = false)
    }

    @Test
    fun defaultsMatchLatest() {
        assertEquals(ClockLabPreset.LATEST, ClockLab.matchingPreset())
        assertEquals(
            "[CLOCK-LAB] query=empty timeSync=echo bt=off keepWifi=off channel=-",
            ClockLab.banner(null),
        )
    }

    @Test
    fun presetsFillKnobs() {
        ClockLab.applyPreset(ClockLabPreset.V2012)
        assertEquals(ClockQueryMode.CARBIT, ClockLab.query)
        assertEquals(ClockTimeSyncMode.ECHO, ClockLab.timeSync)
        assertEquals(false, ClockLab.bluetooth)
        assertEquals(ClockLabPreset.V2012, ClockLab.matchingPreset())

        ClockLab.applyPreset(ClockLabPreset.ZONTES)
        assertEquals(ClockQueryMode.ZONTES, ClockLab.query)
        assertEquals(ClockLabPreset.ZONTES, ClockLab.matchingPreset())

        ClockLab.applyPreset(ClockLabPreset.PHONE_SYNC)
        assertEquals(ClockTimeSyncMode.PHONE, ClockLab.timeSync)
        assertEquals(ClockLabPreset.PHONE_SYNC, ClockLab.matchingPreset())

        ClockLab.applyPreset(ClockLabPreset.BT_LISTEN)
        assertEquals(true, ClockLab.bluetooth)
        assertEquals(ClockLabPreset.BT_LISTEN, ClockLab.matchingPreset())
    }

    @Test
    fun presetLeavesKeepWifiAlone() {
        ClockLab.keepWifi = true
        ClockLab.applyPreset(ClockLabPreset.ZONTES)
        assertEquals(true, ClockLab.keepWifi)
        assertEquals(ClockLabPreset.ZONTES, ClockLab.matchingPreset())
    }

    @Test
    fun clockFixReachesThe125mOnlyOnThePhoneHotspot() {
        assertEquals(true, PxcHandshake.clockFixDash("21340", phoneHotspotDash = false))
        assertEquals(true, PxcHandshake.clockFixDash(" 21340 ", phoneHotspotDash = true))
        assertEquals(true, PxcHandshake.clockFixDash("21322", phoneHotspotDash = true))
        assertEquals(true, PxcHandshake.clockFixDash("21334", phoneHotspotDash = false))
        assertEquals(true, PxcHandshake.clockFixDash("21313", phoneHotspotDash = false))
        assertEquals(true, PxcHandshake.clockFixDash("34808", phoneHotspotDash = false))
        // Moto Morini & co. share the Carbit channels but host their own Wi‑Fi: no change for them.
        assertEquals(false, PxcHandshake.clockFixDash("21322", phoneHotspotDash = false))
        assertEquals(false, PxcHandshake.clockFixDash("21333", phoneHotspotDash = true))
        assertEquals(false, PxcHandshake.clockFixDash(null, phoneHotspotDash = true))
        assertEquals(false, PxcHandshake.clockFixDash("", phoneHotspotDash = false))
    }

    @Test
    fun mixedKnobsHaveNoPreset() {
        ClockLab.applyFrom(ClockQueryMode.ZONTES, ClockTimeSyncMode.PHONE, true)
        assertNull(ClockLab.matchingPreset())
        assertEquals(
            "[CLOCK-LAB] query=zontes timeSync=phone bt=on keepWifi=off channel=21340",
            ClockLab.banner("21340"),
        )
    }

    @Test
    fun smartThresholdDefaultsTo2_3ValueAndIsInTheLabOptions() {
        assertEquals(1_950L, ClockLab.DEFAULT_SMART_THRESHOLD_MS)
        assertEquals(listOf(1_950L, 2_100L, 2_200L, 2_450L), ClockLab.SMART_THRESHOLD_OPTIONS)
        assert(ClockLab.DEFAULT_SMART_THRESHOLD_MS in ClockLab.SMART_THRESHOLD_OPTIONS)
        // Same default as 2.3's constant: no behaviour change unless the rider picks another in the lab.
        assertEquals(PxcHandshake.SLOW_QUERY_TIME_MS, ClockLab.DEFAULT_SMART_THRESHOLD_MS)
    }

    @Test
    fun clockWrongLineCarriesDelayThresholdAndResyncs() {
        val before = Triple(ClockLab.lastQueryTimeAfterMs, ClockLab.smartThresholdMs, ClockLab.resyncsThisBoot)
        try {
            ClockLab.lastQueryTimeAfterMs = 2_050L
            ClockLab.smartThresholdMs = 2_200L
            ClockLab.resyncsThisBoot = 1
            assertEquals(
                "[RESYNC] rider fixed clock by hand (button) — last QUERY_TIME after 2050ms, threshold 2200ms, resyncs this boot 1",
                ClockLab.clockWrongLine("button"),
            )
        } finally {
            ClockLab.lastQueryTimeAfterMs = before.first
            ClockLab.smartThresholdMs = before.second
            ClockLab.resyncsThisBoot = before.third
        }
    }
}
