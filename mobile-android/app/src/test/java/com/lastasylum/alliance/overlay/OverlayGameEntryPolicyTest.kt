package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGameEntryPolicyTest {
    @Test
    fun entryBoost_activeWhenWithinWindow() {
        val now = 10_000L
        assertTrue(OverlayGameEntryPolicy.isEntryBoostActive(now, 20_000L))
        assertFalse(OverlayGameEntryPolicy.isEntryBoostActive(now, 5_000L))
    }

    @Test
    fun forceStableShow_duringEntry_whenAnyPositiveSignal() {
        assertTrue(
            OverlayGameEntryPolicy.shouldForceStableShowDuringEntry(
                entryBoostActive = true,
                inGame = false,
                fullHeuristicShow = true,
                quickInTarget = false,
            ),
        )
        assertTrue(
            OverlayGameEntryPolicy.shouldForceStableShowDuringEntry(
                entryBoostActive = true,
                inGame = true,
                fullHeuristicShow = false,
                quickInTarget = false,
            ),
        )
        assertFalse(
            OverlayGameEntryPolicy.shouldForceStableShowDuringEntry(
                entryBoostActive = false,
                inGame = true,
                fullHeuristicShow = true,
                quickInTarget = true,
            ),
        )
    }

    @Test
    fun notInTargetDismiss_requiresStreak() {
        assertTrue(OverlayGameEntryPolicy.NOT_IN_TARGET_DISMISS_STREAK >= 2)
    }
}
