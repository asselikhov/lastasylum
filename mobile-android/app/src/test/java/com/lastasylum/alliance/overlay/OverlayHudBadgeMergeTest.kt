package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamInboxBadgeDeriver
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayHudBadgeMergeTest {
    @Test
    fun instantPaint_canDecreaseWhenLocalSaysZero() {
        val prevNews = 5
        val instantNews = 0
        val merged = TeamInboxBadgeDeriver.mergeForDisplay(
            effectiveUnread = instantNews,
            previouslyDisplayed = prevNews,
            rawServerUnread = 0,
            optimisticFloor = 0,
        )
        assertEquals(0, merged)
    }

    @Test
    fun instantPaint_hubMergeRespectsLocalSuppress() {
        val merged = TeamInboxBadgeDeriver.mergeForDisplay(
            effectiveUnread = 0,
            previouslyDisplayed = 7,
            rawServerUnread = 2,
            optimisticFloor = 0,
        )
        assertEquals(0, merged)
    }
}
