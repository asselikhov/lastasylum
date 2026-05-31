package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionStageLayoutTest {

    private val dp: (Int) -> Int = { it * 2 }

    @Test
    fun heroAndMiniTileSizes_scaleWithScreenWidth() {
        assertEquals(256, OverlayReactionStageLayout.heroTileSizePx(640, dp))
        assertEquals(336, OverlayReactionStageLayout.heroTileSizePx(1080, dp))
        assertEquals(107, OverlayReactionStageLayout.miniTileSizePx(640, dp))
    }

    @Test
    fun heroTileSize_respectsMaxAndMinDp() {
        assertEquals(336, OverlayReactionStageLayout.heroTileSizePx(2000, dp))
        assertEquals(256, OverlayReactionStageLayout.heroTileSizePx(200, dp))
    }

    @Test
    fun maxStageHeight_is35PercentOfScreen() {
        assertEquals(350, OverlayReactionStageLayout.maxStageHeightPx(1000))
    }

    @Test
    fun shouldEvictOldestHistory_whenOverMax() {
        assertFalse(OverlayReactionStageLayout.shouldEvictOldestHistory(4))
        assertTrue(OverlayReactionStageLayout.shouldEvictOldestHistory(5))
    }

    @Test
    fun heroExpiry_extendedIsLonger() {
        assertTrue(
            OverlayReactionStageLayout.heroExpiryMs(extended = true) >
                OverlayReactionStageLayout.heroExpiryMs(extended = false),
        )
        assertEquals(4_500L, OverlayReactionStageLayout.heroExpiryMs(extended = false))
        assertEquals(6_000L, OverlayReactionStageLayout.heroExpiryMs(extended = true))
    }

    @Test
    fun miniExpiry_burstModeIsShorter() {
        assertTrue(
            OverlayReactionStageLayout.miniExpiryMs(burstMode = true) <
                OverlayReactionStageLayout.miniExpiryMs(burstMode = false),
        )
        assertEquals(3_000L, OverlayReactionStageLayout.miniExpiryMs(burstMode = false))
        assertEquals(2_000L, OverlayReactionStageLayout.miniExpiryMs(burstMode = true))
    }

    @Test
    fun clampStackWidth_respectsAnchorMax() {
        val anchor = OverlayReactionAnchorRect(
            bounds = android.graphics.Rect(0, 0, 200, 50),
            horizontalAlign = HorizontalAlign.CENTER,
            maxStackWidthPx = 180,
        )
        assertEquals(180, OverlayReactionAnchorLayout.clampStackWidthPx(240, anchor))
    }

    @Test
    fun computeStageWindowPlacement_screenCenterUsesFullWidth() {
        val anchor = OverlayReactionAnchorRect(
            bounds = android.graphics.Rect(0, 0, 100, 40),
            horizontalAlign = HorizontalAlign.SCREEN_CENTER,
        )
        val placement = OverlayReactionAnchorLayout.computeStageWindowPlacement(
            anchor = anchor,
            screenWidthPx = 1080,
            dp = dp,
            safeTopMinY = 100,
        )
        assertEquals(100, placement.y)
        assertTrue(placement.fullScreenWidth)
        assertEquals(android.view.Gravity.CENTER_HORIZONTAL, placement.stackContentGravity)
    }

    @Test
    fun computeStageWindowPlacement_respectsSafeTopMinY() {
        val anchor = OverlayReactionAnchorRect(
            bounds = android.graphics.Rect(0, 0, 100, 40),
            horizontalAlign = HorizontalAlign.SCREEN_CENTER,
        )
        val placement = OverlayReactionAnchorLayout.computeStageWindowPlacement(
            anchor = anchor,
            screenWidthPx = 1080,
            dp = dp,
            safeTopMinY = 100,
        )
        assertEquals(100, placement.y)
    }
}
