package dev.zanderp.opencfmoto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DashStyleRowsTest {
    @Test fun marksOnlyTheCurrentRow() {
        val rows = DashStyleRows.rows(listOf("sport", "racing", "clean"), "racing")
        assertEquals(listOf(false, true, false), rows.map { it.selected })
        assertEquals(listOf("sport", "racing", "clean"), rows.map { it.item })
    }

    @Test fun unknownCurrentMarksNothing() {
        assertEquals(0, DashStyleRows.rows(listOf("a", "b"), "z").count { it.selected })
    }

    @Test fun labelHasCheckOnlyWhenSelected() {
        assertEquals("✓  Sport", DashStyleRows.label("Sport", true))
        assertEquals("Sport", DashStyleRows.label("Sport", false))
    }

    @Test fun previewKeyChangesWithEachPart() {
        val base = DashStyleRows.previewKey("sport", false, 0, 800, 416)
        assertEquals(base, DashStyleRows.previewKey("sport", false, 0, 800, 416))
        assertNotEquals(base, DashStyleRows.previewKey("racing", false, 0, 800, 416))
        assertNotEquals(base, DashStyleRows.previewKey("sport", true, 0, 800, 416))
        assertNotEquals(base, DashStyleRows.previewKey("sport", false, 0xFFFF8A1E.toInt(), 800, 416))
    }
}
