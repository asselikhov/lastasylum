package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumGapReconcileTest {

    @Test
    fun gapThreshold_isThirtySeconds() {
        // Forum намеренно отвязан от chat-окна (10 c) и крупнее — см. ChatGapDetection.kt.
        assertEquals(30_000L, FORUM_GAP_RECONCILE_THRESHOLD_MS)
    }

    @Test
    fun replaceMatchingPendingForumOutgoing_confirmsOwnEcho() {
        val pending = buildOptimisticForumMessage(
            teamId = "team1",
            topicId = "topic1",
            senderUserId = "user1",
            senderUsername = "me",
            text = "hello",
            clientMessageId = "client-1",
            nowIso = "2026-01-01T00:00:00Z",
        )
        val messages = mutableListOf(pending)
        val confirmed = pending.copy(id = "507f1f77bcf86cd799439011")
        val replaced = replaceMatchingPendingForumOutgoing(messages, confirmed, "user1")
        assertTrue(replaced)
        assertEquals("507f1f77bcf86cd799439011", messages.single().id)
    }
}
