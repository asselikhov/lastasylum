package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayIncomingReactionDedupeTest {
    @Test
    fun suppressesDuplicateByLogEntryId() {
        OverlayIncomingReactionDedupe.clear()
        val event = OverlayReactionEvent(
            fromUserId = "a",
            fromUsername = "A",
            reaction = "heart",
            targetUserId = "b",
            logEntryId = "log-1",
        )
        val t0 = 1_000_000L
        assertFalse(OverlayIncomingReactionDedupe.shouldSuppress(event, t0))
        assertTrue(OverlayIncomingReactionDedupe.shouldSuppress(event, t0 + 500L))
        assertFalse(OverlayIncomingReactionDedupe.shouldSuppress(event, t0 + 3_500L))
    }

    @Test
    fun suppressesDuplicateFallbackKey() {
        OverlayIncomingReactionDedupe.clear()
        val event = OverlayReactionEvent(
            fromUserId = "a",
            fromUsername = "A",
            reaction = "heart",
            targetUserId = "b",
        )
        val t0 = 2_000_000L
        assertFalse(OverlayIncomingReactionDedupe.shouldSuppress(event, t0))
        assertTrue(OverlayIncomingReactionDedupe.shouldSuppress(event, t0 + 100L))
    }
}
