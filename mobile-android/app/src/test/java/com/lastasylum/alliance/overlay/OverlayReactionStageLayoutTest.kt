package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
    fun polishConstants_matchPlan() {
        assertEquals(80L, OverlayReactionStageLayout.STAGGER_MS)
        assertEquals(280L, OverlayReactionStageLayout.DEMOTE_ANIM_MS)
        assertEquals(
            OverlayReactionStageLayout.MINI_HERO_RATIO,
            OverlayReactionStageLayout.MINI_SCALE_RATIO,
            0.001f,
        )
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
    fun computeStageWindowPlacement_screenCenterStacksCentered() {
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
        assertEquals(android.view.Gravity.CENTER_HORIZONTAL, placement.stackContentGravity)
    }

    @Test
    fun hudBurstAnchor_usesMaxHudBottomAndScreenCenter() {
        val anchor = checkNotNull(
            OverlayReactionAnchorLayout.hudBurstAnchor(
                statusBounds = android.graphics.Rect(10, 20, 200, 80),
                topRightBounds = android.graphics.Rect(800, 24, 1060, 72),
                screenWidthPx = 1080,
            ),
        )
        assertEquals(80, anchor.bounds.bottom)
        assertEquals(HorizontalAlign.SCREEN_CENTER, anchor.horizontalAlign)
    }

    @Test
    fun adjustCenteredWindowX_screenCenterUsesScreenMiddle() {
        val anchor = OverlayReactionAnchorRect(
            bounds = android.graphics.Rect(900, 0, 1000, 50),
            horizontalAlign = HorizontalAlign.SCREEN_CENTER,
        )
        assertEquals(420, OverlayReactionAnchorLayout.adjustCenteredWindowX(anchor, 240, 1080))
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
