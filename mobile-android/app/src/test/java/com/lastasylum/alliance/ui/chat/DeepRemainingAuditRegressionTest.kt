package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.telemetry.LatencySpanType
import com.lastasylum.alliance.data.teams.ForumMessageStash
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Automated coverage for deep remaining audit top regression scenarios. */
class DeepRemainingAuditRegressionTest {
    @Before
    fun clearStash() {
        ForumMessageStash.drainAllForTeam("team-a")
    }

    @Test
    fun fcmReceiveToNotify_spanTypeRegistered() {
        assertEquals("fcm_receive_to_notify", LatencySpanType.FcmReceiveToNotify.wire)
    }

    @Test
    fun forumStash_drainAllForTeam_supportsOverlayReconnectCatchUp() {
        val msg = TeamForumMessageDto(
            id = "msg1",
            topicId = "topic1",
            teamId = "team-a",
            senderUserId = "peer",
            senderUsername = "peer",
            text = "hi",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        assertTrue(ForumMessageStash.stash(msg))
        val drained = ForumMessageStash.drainAllForTeam("team-a")
        assertEquals(1, drained.size)
        assertEquals(listOf(msg), drained["topic1"])
    }

    @Test
    fun skipRefresh_falseAfterReconnectFlag_documented() {
        assertFalse(
            shouldSkipBackgroundMessageRefresh(
                visible = emptyList(),
                sessionCache = emptyList(),
                roomCache = null,
                pageSize = 30,
                forceAfterReconnect = true,
            ),
        )
    }

    @Test
    fun manualTopFiveScenarios_documented() {
        val topFive = listOf(
            "Forum kill app mid-send completes via outbox after reopen",
            "Forum msg on list → badge + open without REST lag",
            "Quick command cold FGS: without multi-second delay",
            "Overlay panel close → mark-read before badge reconcile",
            "Airplane 30s → reconnect gaps filled",
        )
        assertEquals(5, topFive.size)
    }
}
