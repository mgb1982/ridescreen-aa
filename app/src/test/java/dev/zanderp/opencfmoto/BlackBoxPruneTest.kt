package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackBoxPruneTest {
    @Test fun movingBumpsGetTheirOwnName() {
        assertEquals("bb_100_m.json", BlackBoxPrune.fileName(100, "moving"))
        assertEquals("bb_100.json", BlackBoxPrune.fileName(100, "alarm"))
    }

    @Test fun bumpsNeverEvictRealEvents() {
        val real = (1..10).map { "bb_$it.json" }
        val bumps = (11..15).map { "bb_${it}_m.json" }
        val del = BlackBoxPrune.toDelete(real + bumps, 10)
        assertEquals(listOf("bb_12_m.json", "bb_11_m.json"), del)
    }

    @Test fun oldestRealEventGoesBeyondTheLimit() {
        val del = BlackBoxPrune.toDelete((1..11).map { "bb_$it.json" } + "notes.txt", 10)
        assertEquals(listOf("bb_1.json"), del)
        assertTrue(BlackBoxPrune.toDelete(listOf("bb_5_m.json"), 10).isEmpty())
    }
}
