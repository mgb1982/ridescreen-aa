// SPDX-License-Identifier: AGPL-3.0-or-later
// Part of RideScreen AA (fork of OpenCfMoto). Free software under the GNU AGPL v3 or later.
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashLayoutTest {
    private val areas = mapOf("sport" to DashLayout.SPORT, "racing" to DashLayout.RACING, "touring" to DashLayout.TOURING)

    @Test fun noCardsNoBoxes() {
        assertTrue(DashLayout.cards(DashLayout.SPORT, 0).isEmpty())
    }

    @Test fun oneBoxPerCard() {
        for ((_, a) in areas) for (n in 1..6) assertEquals(n, DashLayout.cards(a, n).size)
    }

    @Test fun cardsStayInsideTheReadablePartOfTheDash() {
        for ((name, a) in areas) for (n in 1..6) for (b in DashLayout.cards(a, n)) {
            val where = "$name n=$n $b"
            assertTrue(where, b.x >= a.left - .01f && b.right <= a.right + .01f)
            assertTrue(where, b.y >= a.top - .01f && b.bottom <= a.bottom + .01f)
            // Below the step the dash's raised speed readout covers the right-hand side.
            if (b.bottom > a.stepBottom) assertTrue(where, b.right <= a.stepX)
        }
    }

    @Test fun cardsNeverOverlap() {
        for ((name, a) in areas) for (n in 2..6) {
            val boxes = DashLayout.cards(a, n)
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val p = boxes[i]; val q = boxes[j]
                val apart = p.right <= q.x + .01f || q.right <= p.x + .01f || p.bottom <= q.y + .01f || q.bottom <= p.y + .01f
                assertTrue("$name n=$n $p $q", apart)
            }
        }
    }

    @Test fun fiveCardsLookLikeTheReleasedStyles() {
        // 2.4: Sport 2+2+1 cards of 52 px; Racing and Touring 3+2 of 50 px.
        val sport = DashLayout.cards(DashLayout.SPORT, 5)
        assertEquals(listOf(2, 2, 1), rowCounts(sport))
        assertTrue(sport.all { it.h in 50f..56f })
        for (a in listOf(DashLayout.RACING, DashLayout.TOURING)) {
            val boxes = DashLayout.cards(a, 5)
            assertEquals(listOf(3, 2), rowCounts(boxes))
            assertTrue(boxes.all { it.h in 50f..56f })
        }
    }

    @Test fun fewerCardsAreNeverSmaller() {
        for ((name, a) in areas) {
            val sizes = (1..6).map { DashLayout.cards(a, it).first().h }
            for (i in 1 until sizes.size) assertTrue("$name $sizes", sizes[i] <= sizes[i - 1] + .01f)
        }
    }

    @Test fun hidingFieldsMakesTheRestGrow() {
        for ((name, a) in areas) {
            val five = DashLayout.cards(a, 5).first().h
            val two = DashLayout.cards(a, 2).first().h
            assertTrue("$name five=$five two=$two", two >= five * 1.4f)
        }
    }

    @Test fun cardsNeverPassTheMaximumHeight() {
        for ((_, a) in areas) assertTrue(DashLayout.cards(a, 1).single().h <= a.maxH)
    }

    @Test fun textFollowsTheCardHeight() {
        val small = DashLayout.cardText(DashLayout.Box(0f, 0f, 200f, 50f))
        assertEquals(24f, small.valueSize, .01f)
        assertEquals(13f, small.titleSize, .01f)
        assertEquals(46f, small.textX, .01f)
        val big = DashLayout.cardText(DashLayout.Box(0f, 0f, 300f, 90f))
        assertTrue(big.valueSize > small.valueSize * 1.7f)
        assertTrue(big.titleSize <= 24f)
    }

    @Test fun fitScaleOnlyShrinks() {
        assertEquals(1f, DashLayout.fitScale(100f, 80f), 0f)
        assertEquals(.5f, DashLayout.fitScale(50f, 100f), .001f)
        assertEquals(.4f, DashLayout.fitScale(10f, 100f), .001f)
    }

    @Test fun orderFollowsTheRiderAndAppendsTheRest() {
        val all = DashLayout.DEFAULT_CARDS.toSet()
        assertEquals(listOf("baro", "coolant", "volt", "load", "intake"),
            DashLayout.orderedCards(listOf("baro"), all))
        // Hidden, unknown and repeated ids are dropped.
        assertEquals(listOf("intake", "volt"),
            DashLayout.orderedCards(listOf("intake", "nope", "intake", "coolant"), setOf("volt", "intake")))
        assertEquals(DashLayout.DEFAULT_CARDS, DashLayout.orderedCards(emptyList(), all))
    }

    @Test fun moveSwapsNeighboursAndKeepsTheEnds() {
        val d = DashLayout.DEFAULT_CARDS
        assertEquals(listOf("volt", "coolant", "load", "intake", "baro"), DashLayout.move(d, "volt", -1))
        assertEquals(d, DashLayout.move(d, "coolant", -1))
        assertEquals(d, DashLayout.move(d, "baro", 1))
        assertEquals(d, DashLayout.move(d, "nope", 1))
    }

    @Test fun orderRoundTrips() {
        val o = listOf("load", "coolant")
        assertEquals(o, DashLayout.parseOrder(DashLayout.encodeOrder(o)))
        assertTrue(DashLayout.parseOrder(null).isEmpty())
        assertEquals(listOf("a", "b"), DashLayout.parseOrder(" a, ,b "))
    }

    @Test fun colourRoundTripsAndRejectsJunk() {
        val c = 0xFF3D8BFF.toInt()
        assertEquals("#3D8BFF", DashLayout.encodeColor(c))
        assertEquals(c, DashLayout.parseColor("#3D8BFF"))
        assertEquals(c, DashLayout.parseColor("3d8bff"))
        assertEquals(0, DashLayout.parseColor(""))
        assertEquals(0, DashLayout.parseColor("#12345"))
        assertEquals(0, DashLayout.parseColor("#GGGGGG"))
        assertEquals("", DashLayout.encodeColor(0))
    }

    @Test fun accentKeepsItsColourAtNightAndDarkensByDay() {
        val blue = 0xFF3D8BFF.toInt()
        val tile = 0xFF131519.toInt()
        val night = DashLayout.accentFor(blue, night = true, tile = tile)
        assertEquals(blue, night.accent)
        assertTrue(brightness(night.accent2) > brightness(night.accent))
        val day = DashLayout.accentFor(blue, night = false, tile = 0xFFFFFFFF.toInt())
        assertTrue(brightness(day.accent) < brightness(blue))
        for (c in listOf(night.accent, night.accent2, night.edge, day.accent)) assertEquals(0xFF, (c ushr 24) and 0xFF)
    }

    @Test fun presetsStartWithTheStyleColour() {
        assertEquals(0, DashLayout.ACCENT_PRESETS.first())
        assertFalse(DashLayout.ACCENT_PRESETS.drop(1).contains(0))
    }

    @Test fun splitsCoverEveryArrangement() {
        assertEquals(listOf(listOf(3)), DashLayout.splits(3, 1))
        assertEquals(setOf(listOf(1, 2), listOf(2, 1)), DashLayout.splits(3, 2).toSet())
        assertEquals(6, DashLayout.splits(5, 3).size)
    }

    private fun rowCounts(boxes: List<DashLayout.Box>): List<Int> =
        boxes.groupBy { it.y }.toSortedMap().values.map { it.size }

    private fun brightness(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
}
