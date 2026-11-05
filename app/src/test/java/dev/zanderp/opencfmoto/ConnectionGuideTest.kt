package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionGuideTest {
    @Test
    fun joiningWifiHighlightsFirstStep() {
        val model = ConnectionGuide.model(Phase.JOINING_WIFI, "")
        assertEquals(ConnectionGuide.Mark.CURRENT, model.steps[0])
        assertEquals(ConnectionGuide.Mark.TODO, model.steps[1])
        assertEquals(R.string.hint_wifi, model.hintRes)
    }

    @Test
    fun linkingMarksWifiAndDashDone() {
        val model = ConnectionGuide.model(Phase.PXC_CONNECTING, "368G")
        assertEquals(
            listOf(
                ConnectionGuide.Mark.DONE,
                ConnectionGuide.Mark.DONE,
                ConnectionGuide.Mark.CURRENT,
                ConnectionGuide.Mark.TODO,
            ),
            model.steps,
        )
    }

    @Test
    fun streamingCompletesEveryStep() {
        val model = ConnectionGuide.model(Phase.STREAMING, "")
        assertEquals(List(4) { ConnectionGuide.Mark.DONE }, model.steps)
        assertEquals(R.string.hint_stream, model.hintRes)
    }

    @Test
    fun ignitionStyleFailurePointsAtLinking() {
        val model = ConnectionGuide.model(Phase.ERROR, "Yunmo link dropped")
        assertEquals(ConnectionGuide.Mark.FAILED, model.steps[2])
        assertEquals(R.string.hint_error_ignition, model.hintRes)
    }

    @Test
    fun dashNotAskingFailsTheDashboardStep() {
        val model = ConnectionGuide.model(Phase.ERROR, "The dash isn't asking for the picture")
        assertEquals(ConnectionGuide.Mark.DONE, model.steps[0])
        assertEquals(ConnectionGuide.Mark.FAILED, model.steps[1])
        assertEquals(R.string.hint_error_dash, model.hintRes)
    }

    @Test
    fun missingWifiFailsTheFirstStep() {
        val model = ConnectionGuide.model(Phase.ERROR, "Phone Wi‑Fi is off — turn it on to connect")
        assertEquals(ConnectionGuide.Mark.FAILED, model.steps[0])
        assertEquals(R.string.hint_error_wifi, model.hintRes)
    }
}
