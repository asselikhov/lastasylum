package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionHistoryLayoutTest {

    @Test
    fun modeFor_riverWhenBurstAndFull() {
        assertEquals(
            HistoryLayoutMode.FAN,
            OverlayReactionHistoryLayout.modeFor(burstMode = false, historyCount = 4),
        )
        assertEquals(
            HistoryLayoutMode.RIVER,
            OverlayReactionHistoryLayout.modeFor(burstMode = true, historyCount = 4),
        )
    }

    @Test
    fun fanOffsets_centerIndexHasZeroX() {
        val miniPx = 100
        val offset = OverlayReactionHistoryLayout.fanOffsets(
            index = 1,
            count = 3,
            miniPx = miniPx,
            gapPx = 6,
            overlapPx = 12,
            arcYPx = 8,
        )
        assertEquals(0f, offset.translationX, 0.001f)
    }

    @Test
    fun riverScrollTargetX_clampsToZero() {
        assertEquals(0, OverlayReactionHistoryLayout.riverScrollTargetX(100, 200))
        assertEquals(50, OverlayReactionHistoryLayout.riverScrollTargetX(250, 200))
    }
}
