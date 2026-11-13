// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuadroLogicTest {
    @Test fun selectorFollowsDashboardWanted() {
        assertEquals(CuadroLogic.Mode.DASHBOARD, CuadroLogic.selectedMode(true))
        assertEquals(CuadroLogic.Mode.ANDROID_AUTO, CuadroLogic.selectedMode(false))
    }

    @Test fun modeButtonsNeedConnection() {
        assertTrue(CuadroLogic.modeButtonsEnabled(true))
        assertFalse(CuadroLogic.modeButtonsEnabled(false))
    }

    @Test fun targetFromHit() {
        assertEquals(CuadroLogic.Target.LEGACY, CuadroLogic.target(false, false))
        assertEquals(CuadroLogic.Target.LEGACY, CuadroLogic.target(false, true))
        assertEquals(CuadroLogic.Target.ANDROID_AUTO, CuadroLogic.target(true, true))
        assertEquals(CuadroLogic.Target.DASHBOARD, CuadroLogic.target(true, false))
        assertEquals(CuadroLogic.Target.NONE, CuadroLogic.target(true, false, insideDash = false))
        assertEquals(CuadroLogic.Target.ANDROID_AUTO, CuadroLogic.target(true, true, insideDash = false))
    }

    @Test fun tapVersusDrag() {
        val slop = 12f * 2f
        assertTrue(CuadroLogic.isTap(100f, 100f, 100f, 100f, slop))
        assertTrue(CuadroLogic.isTap(100f, 100f, 116f, 116f, slop))
        assertTrue(CuadroLogic.isTap(100f, 100f, 124f, 100f, slop))
        assertFalse(CuadroLogic.isTap(100f, 100f, 125f, 100f, slop))
        assertFalse(CuadroLogic.isTap(100f, 100f, 100f, 40f, slop))
    }
}
