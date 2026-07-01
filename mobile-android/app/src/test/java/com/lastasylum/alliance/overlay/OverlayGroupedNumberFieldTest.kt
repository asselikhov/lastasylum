package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayGroupedNumberFieldTest {
    @Test
    fun formatDisplay_usesSpaceGrouping() {
        assertEquals("50\u00A0000\u00A0000", OverlayGroupedNumberField.formatDisplay(50_000_000L))
        assertEquals("", OverlayGroupedNumberField.formatDisplay(0L))
    }

    @Test
    fun parse_stripsGroupingCharacters() {
        assertEquals(50_000_000L, OverlayGroupedNumberField.parse("50 000 000"))
        assertEquals(1_234_567L, OverlayGroupedNumberField.parse("1\u00A0234\u00A0567"))
        assertNull(OverlayGroupedNumberField.parse(""))
        assertNull(OverlayGroupedNumberField.parse(null))
    }
}
