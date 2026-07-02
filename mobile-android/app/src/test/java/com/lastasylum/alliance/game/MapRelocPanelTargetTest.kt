package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun inGameRouteBtn_flagOnDataClass() {
        val off = MapRelocPanelTarget(1, true, 10, 20, 5, null)
        assertFalse(off.inGameRouteBtn)
        val on = MapRelocPanelTarget(1, true, 10, 20, 5, null, inGameRouteBtn = true)
        assertTrue(on.inGameRouteBtn)
    }

    @Test
    fun fromJson_parsesInGameRouteBtn() {
        val json = "{\"seq\":1,\"open\":true,\"x\":414,\"y\":683,\"sid\":109,\"inGameRouteBtn\":true}"
        val target = MapRelocPanelTarget.fromJson(json)
        assertNotNull("fromJson returned null", target)
        assertTrue(target!!.inGameRouteBtn)
        assertEquals(414, target.x)
    }

    @Test
    fun fromRouteClickJson_setsInGameFlag() {
        val json = "{\"x\":100,\"y\":200,\"sid\":5,\"ts\":12345}"
        val target = MapRelocPanelTarget.fromRouteClickJson(json)
        assertNotNull("fromRouteClickJson returned null", target)
        assertTrue(target!!.inGameRouteBtn)
        assertTrue(target.open)
        assertEquals(100, target.x)
        assertEquals(200, target.y)
    }
}
