// SPDX-License-Identifier: AGPL-3.0-or-later
package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AaColumnLayoutTest {
    private val sizes = listOf(1024 to 464, 1280 to 535, 800 to 416)

    @Test fun everythingStaysInsideTheColumnAndInOrder() {
        for ((w, h) in sizes) for (n in 0..6) {
            val col = AaWindowLayout.column(w, h)
            val r = AaColumnLayout.rows(col, n)
            assertEquals(minOf(n, 4), r.cards.size)
            assertTrue(r.bar.y >= r.speed.bottom)
            var prev = r.bar.bottom
            for (b in r.cards) {
                assertTrue("$w x $h n=$n", b.y >= prev && b.h >= 1f)
                assertTrue(b.x >= col.x && b.right <= col.x + col.w + .01f)
                prev = b.bottom
            }
            assertTrue("$w x $h n=$n", prev <= col.y + col.h + .01f)
        }
    }

    @Test fun fourCardsStayReadable() {
        for ((w, h) in sizes) {
            val r = AaColumnLayout.rows(AaWindowLayout.column(w, h), 4)
            assertTrue("card height ${r.cards[0].h} at $w x $h", r.cards[0].h >= h * .11f)
        }
    }
}
