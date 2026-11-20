package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryDeviceTest {
    @Test fun manufacturerAndModel() {
        assertEquals("samsung SM-S918B", TelemetryDevice.label("samsung", "SM-S918B"))
        assertEquals("Xiaomi 23049PCD8G", TelemetryDevice.label("Xiaomi", "23049PCD8G"))
    }

    @Test fun manufacturerNotRepeated() {
        assertEquals("Google Pixel 8", TelemetryDevice.label("Google", "Google Pixel 8"))
        assertEquals("OnePlus", TelemetryDevice.label("OnePlus", ""))
        assertEquals("Pixel 8", TelemetryDevice.label(null, "Pixel 8"))
        assertEquals("", TelemetryDevice.label(null, null))
    }

    @Test fun odd_characters_and_length_are_cleaned() {
        assertEquals("ACME X1 script pro", TelemetryDevice.label("ACME", "X1  <script> pro"))
        assertEquals(48, TelemetryDevice.label("a".repeat(30), "b".repeat(40)).length)
    }

    @Test fun ramGroupsPhonesOfTheSameClass() {
        fun mb(n: Long) = n * 1024 * 1024
        assertEquals(4096, TelemetryDevice.ramMb(mb(3_900)))   // a 4 GB phone reports ~3.6-3.9 GB
        assertEquals(4096, TelemetryDevice.ramMb(mb(3_650)))
        assertEquals(3072, TelemetryDevice.ramMb(mb(2_800)))
        assertEquals(6144, TelemetryDevice.ramMb(mb(5_600)))
        assertEquals(8192, TelemetryDevice.ramMb(mb(7_700)))
        assertEquals(12288, TelemetryDevice.ramMb(mb(11_300)))
        assertEquals(39936, TelemetryDevice.ramMb(mb(39_000)))   // above the table: whole gigabytes, rounded up
        assertEquals(0, TelemetryDevice.ramMb(0))
        assertEquals(0, TelemetryDevice.ramMb(-5))
    }
}
