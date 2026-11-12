package dev.zanderp.opencfmoto

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {
    private val es = Locale("es", "ES")
    private val de = Locale.GERMANY
    private val us = Locale.US

    @Test fun decimalUsesLocaleSeparator() {
        assertEquals("14,1", FmtCore.decimal(14.1, 1, es))
        assertEquals("14,1", FmtCore.decimal(14.1, 1, de))
        assertEquals("14.1", FmtCore.decimal(14.1, 1, us))
    }

    @Test fun groupedUsesLocaleSeparator() {
        assertEquals("10.000", FmtCore.grouped(10000, de))
        assertEquals("10.000", FmtCore.grouped(10000, es))
        assertEquals("10,000", FmtCore.grouped(10000, us))
        assertEquals("950", FmtCore.grouped(950, de))
        assertEquals("10.000 rpm", FmtCore.rpm(10000, de))
    }

    @Test fun distanceMetric() {
        assertEquals("12,34 km", FmtCore.distance(12340.0, false, es))
        assertEquals("12,34 km", FmtCore.distance(12340.0, false, de))
        assertEquals("12.34 km", FmtCore.distance(12340.0, false, us))
        assertEquals("12,3 km", FmtCore.distance(12340.0, false, es, 1))
    }

    @Test fun distanceImperial() {
        assertEquals("7,70 mi", FmtCore.distance(12392.0, true, es))
        assertEquals("7.70 mi", FmtCore.distance(12392.0, true, us))
        assertEquals("1.00 mi", FmtCore.distance(1609.344, true, us))
    }

    @Test fun russianUnitLabels() {
        assertEquals("5,00 км", FmtCore.distance(5000.0, false, Locale("ru", "RU")))
    }

    @Test fun speedKmhAndMph() {
        assertEquals("100 km/h", FmtCore.speed(100.0, false))
        assertEquals("62 mph", FmtCore.speed(100.0, true))
        assertEquals("0 mph", FmtCore.speed(0.0, true))
        assertEquals(99, FmtCore.speedValue(99.9, false))
    }
}
