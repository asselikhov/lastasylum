package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayBadgeFormatTest {
    @Test
    fun label_capsAt99Plus() {
        assertEquals("1", OverlayBadgeFormat.label(1))
        assertEquals("99", OverlayBadgeFormat.label(99))
        assertEquals("99+", OverlayBadgeFormat.label(100))
        assertEquals("99+", OverlayBadgeFormat.label(999))
    }

    @Test
    fun capped_coercesNegativeToZero() {
        assertEquals(0, OverlayBadgeFormat.capped(-3))
        assertEquals(99, OverlayBadgeFormat.capped(150))
    }
}
