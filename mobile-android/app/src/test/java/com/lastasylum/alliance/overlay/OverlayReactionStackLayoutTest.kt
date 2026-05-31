package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionStackLayoutTest {

    @Test
    fun slotScaleForIndex_decreasesForOlderSlots() {
        assertEquals(1f, OverlayReactionStackLayout.slotScaleForIndex(0), 0.001f)
        assertEquals(0.88f, OverlayReactionStackLayout.slotScaleForIndex(1), 0.001f)
        assertEquals(0.58f, OverlayReactionStackLayout.slotScaleForIndex(99), 0.001f)
    }

    @Test
    fun slotAlpha_burstModeIsMoreAggressive() {
        assertTrue(
            OverlayReactionStackLayout.slotAlphaForIndex(2, burstMode = true) <
                OverlayReactionStackLayout.slotAlphaForIndex(2, burstMode = false),
        )
    }

    @Test
    fun slotTranslationY_increasesWithIndex() {
        assertEquals(0f, OverlayReactionStackLayout.slotTranslationYForIndex(0, 6))
        assertEquals(12f, OverlayReactionStackLayout.slotTranslationYForIndex(2, 6))
    }

    @Test
    fun slotTranslationX_endAlignShiftsLeft() {
        assertEquals(-6f, OverlayReactionStackLayout.slotTranslationXForIndex(2, 3, HorizontalAlign.END))
        assertEquals(0f, OverlayReactionStackLayout.slotTranslationXForIndex(2, 3, HorizontalAlign.CENTER))
    }

    @Test
    fun maxStageHeight_isHalfScreen() {
        assertEquals(500, OverlayReactionStackLayout.maxStageHeightPx(1000))
    }

    @Test
    fun headExpiry_extendedIsLonger() {
        assertTrue(
            OverlayReactionStackLayout.headExpiryMs(extended = true) >
                OverlayReactionStackLayout.headExpiryMs(extended = false),
        )
    }

    @Test
    fun visibleDuration_burstTailIsShorter() {
        val normal = OverlayReactionStackLayout.visibleDurationMsForIndex(3, 4, burstMode = false)
        val burst = OverlayReactionStackLayout.visibleDurationMsForIndex(3, 4, burstMode = true)
        assertEquals(OverlayReactionStackLayout.TAIL_VISIBLE_MS, normal)
        assertEquals(OverlayReactionStackLayout.BURST_TAIL_VISIBLE_MS, burst)
    }

    @Test
    fun shouldEvictOldestSlot_whenOverMaxVisible() {
        assertTrue(
            OverlayReactionStackLayout.shouldEvictOldestSlot(6, 100, 500),
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
    fun computeStageWindowPlacement_respectsSafeTopMinY() {
        val anchor = OverlayReactionAnchorRect(
            bounds = android.graphics.Rect(0, 0, 100, 40),
            horizontalAlign = HorizontalAlign.END,
        )
        val placement = OverlayReactionAnchorLayout.computeStageWindowPlacement(
            anchor = anchor,
            screenWidthPx = 1080,
            dp = { it * 2 },
            safeTopMinY = 100,
        )
        assertEquals(100, placement.y)
    }
}
