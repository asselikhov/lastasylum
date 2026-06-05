package com.lastasylum.alliance.data.teams

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamInboxBadgeDeriverTest {
    @Test
    fun mergeForDisplay_allowsZeroWhenLocalSuppressesStaleServer() {
        assertEquals(
            0,
            TeamInboxBadgeDeriver.mergeForDisplay(
                effectiveUnread = 0,
                previouslyDisplayed = 5,
                rawServerUnread = 3,
                optimisticFloor = 0,
            ),
        )
    }

    @Test
    fun mergeForDisplay_keepsOptimisticFloorWhenEffectiveZero() {
        assertEquals(
            2,
            TeamInboxBadgeDeriver.mergeForDisplay(
                effectiveUnread = 0,
                previouslyDisplayed = 0,
                rawServerUnread = 0,
                optimisticFloor = 2,
            ),
        )
    }

    @Test
    fun resolveForumUnread_prefersClientOverApi() {
        assertEquals(
            0,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 0, apiUnread = 3),
        )
        assertEquals(
            2,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 2, apiUnread = 5),
        )
        assertEquals(
            4,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = null, apiUnread = 4),
        )
    }

    @Test
    fun mergeForDisplay_usesEffectiveWhenPositive() {
        assertEquals(
            4,
            TeamInboxBadgeDeriver.mergeForDisplay(
                effectiveUnread = 4,
                previouslyDisplayed = 1,
                rawServerUnread = 4,
                optimisticFloor = 0,
            ),
        )
    }
}
