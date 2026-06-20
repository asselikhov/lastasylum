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
    fun resolveForumUnread_trustsClientZeroOverStaleApi() {
        assertEquals(
            0,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 0, apiUnread = 3),
        )
        assertEquals(
            5,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 2, apiUnread = 5),
        )
        assertEquals(
            4,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = null, apiUnread = 4),
        )
        assertEquals(
            2,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 2, apiUnread = 0),
        )
    }

    @Test
    fun resolveNewsUnread_matchesForumMergePolicy() {
        assertEquals(
            0,
            TeamInboxBadgeDeriver.resolveNewsUnread(clientUnread = 0, apiUnread = 3),
        )
        assertEquals(
            5,
            TeamInboxBadgeDeriver.resolveNewsUnread(clientUnread = 2, apiUnread = 5),
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

    @Test
    fun syncForumBadgeFromTopics_shouldNotDoubleApplyAggregateOptimisticFloor() {
        val topic = TeamForumTopicDto(
            id = "t1",
            teamId = "team1",
            title = "General",
            createdByUserId = "u1",
            messageCount = 5,
            unreadCount = 1,
            lastReadMessageId = null,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
        )
        val floors = mapOf("t1" to 1)
        val counts = TeamInboxBadgeDeriver.computeForumUnreadCounts(
            topics = listOf(topic),
            localReadByTopic = emptyMap(),
            optimisticFloorByTopic = floors,
        )
        val correctTabBadge = TeamInboxBadgeDeriver.mergeForDisplay(
            effectiveUnread = counts.effective,
            previouslyDisplayed = 0,
            rawServerUnread = counts.rawServer,
            optimisticFloor = 0,
        )
        val inflatedTabBadge = TeamInboxBadgeDeriver.mergeForDisplay(
            effectiveUnread = counts.effective,
            previouslyDisplayed = 0,
            rawServerUnread = counts.rawServer,
            optimisticFloor = floors.values.sum(),
        )
        assertEquals(counts.effective, correctTabBadge)
        assertEquals(correctTabBadge, inflatedTabBadge)
    }

    @Test
    fun overlaySeed_resolveForumUnread_prefersClientZeroOverStaleCache() {
        assertEquals(
            0,
            TeamInboxBadgeDeriver.resolveForumUnread(clientUnread = 0, apiUnread = 5),
        )
    }
}
