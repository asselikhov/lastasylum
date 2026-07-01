package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRelocPanelTargetTest {
    @Test
    fun hasRelocButtonAnchor_whenMetricsPresent() {
        val target = MapRelocPanelTarget(
            seq = 3,
            open = true,
            x = 100,
            y = 200,
            sid = 5,
            panelBottomPx = 900,
            relocBtnRightPx = 360,
            relocBtnTopPx = 1800,
            relocBtnWidthPx = 240,
            relocBtnHeightPx = 80,
            relocBtnColorRgb = 0x3D8A5C,
        )
        assertTrue(target.hasRelocButtonAnchor())
        assertEquals(360, target.relocBtnRightPx)
    }

    @Test
    fun withResolvedSid_fillsMissingServer() {
        val target = MapRelocPanelTarget(
            seq = 1,
            open = true,
            x = 10,
            y = 20,
            sid = 0,
            panelBottomPx = 900,
        )
        val resolved = target.withResolvedSid(42)
        assertEquals(42, resolved.sid)
        assertTrue(resolved.isValidWithSid(42))
    }

    @Test
    fun isValidWithSid_requiresCoordsAndServer() {
        val invalid = MapRelocPanelTarget(1, true, 0, 20, 0, null)
        assertFalse(invalid.isValidWithSid(5))
    }
}
