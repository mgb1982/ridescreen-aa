package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFieldsTest {
    private val none = VideoDataFlags(false, false, false, false, false, false, false)
    private val full = VideoDataFlags(true, true, true, true, true, true, true)

    @Test fun defaultIsEverythingOn() {
        assertEquals(VideoFields.ALL, VideoFields.decode(null))
        assertEquals(VideoFields.ALL, VideoFields.decode(""))
        assertEquals("", VideoFields.encode(VideoFields.ALL))
    }

    @Test fun encodeDecodeRoundTrip() {
        val s = VideoFields.ALL - VideoField.COOLANT - VideoField.G_FORCE
        assertEquals("COOLANT,G_FORCE", VideoFields.encode(s))
        assertEquals(s, VideoFields.decode(VideoFields.encode(s)))
        assertEquals(emptySet<VideoField>(), VideoFields.decode(VideoFields.encode(emptySet())))
    }

    @Test fun decodeIgnoresUnknownNames() {
        assertEquals(VideoFields.ALL - VideoField.RPM, VideoFields.decode("RPM,NOPE, "))
    }

    @Test fun withoutDataOnlySpeedAndMapRemain() {
        assertEquals(setOf(VideoField.SPEED, VideoField.MINIMAP), VideoFields.available(VideoStyle.CLASSIC, none))
        assertEquals(setOf(VideoField.SPEED, VideoField.MINIMAP, VideoField.G_FORCE), VideoFields.available(VideoStyle.GAUGES, none))
        assertFalse(VideoField.PEAK_BANNERS in VideoFields.available(VideoStyle.GAUGES, full))
        assertTrue(VideoField.PEAK_BANNERS in VideoFields.available(VideoStyle.HUD, none))
    }

    @Test fun voltageOnlyInTelemetry() {
        assertTrue(VideoField.VOLTAGE in VideoFields.available(VideoStyle.TELEMETRY, full))
        assertFalse(VideoField.VOLTAGE in VideoFields.available(VideoStyle.HUD, full))
    }

    @Test fun shortPresetKeepsOnlyAvailable() {
        assertEquals(setOf(VideoField.SPEED), VideoFields.shortPreset(VideoStyle.HUD, none))
        assertEquals(setOf(VideoField.SPEED, VideoField.LEAN, VideoField.HEART_RATE), VideoFields.shortPreset(VideoStyle.HUD, full))
        assertEquals(setOf(VideoField.SPEED, VideoField.LEAN), VideoFields.shortPreset(VideoStyle.CINEMA, none.copy(lean = true)))
    }

    @Test fun unavailableShownUnchecked() {
        val c = VideoFields.checked(VideoFields.ALL, VideoStyle.HUD, none)
        assertFalse(VideoField.HEART_RATE in c)
        assertTrue(VideoField.SPEED in c)
    }

    @Test fun mergeKeepsHiddenChoices() {
        // Trip without heart rate: the saved "heart rate on" survives; coolant (available) follows the picker.
        val d = full.copy(heartRate = false)
        val saved = VideoFields.ALL
        val out = VideoFields.merge(saved, setOf(VideoField.SPEED), VideoStyle.HUD, d)
        assertTrue(VideoField.HEART_RATE in out)
        assertTrue(VideoField.VOLTAGE in out)       // not drawn by HUD: untouched
        assertFalse(VideoField.COOLANT in out)
        assertTrue(VideoField.SPEED in out)
    }
}
