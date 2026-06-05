package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionCoordinatorTest {
    @Test
    fun deliversReactionToTargetUser() {
        val event = OverlayReactionEvent(
            fromUserId = "u1",
            fromUsername = "a",
            reaction = "heart",
            targetUserId = "u2",
        )
        assertTrue(
            OverlayReactionCoordinator.shouldDeliverIncomingReaction(
                event = event,
                selfUserId = "u2",
                overlaySessionActive = true,
            ),
        )
    }

    @Test
    fun rejectsSelfSentReaction() {
        val event = OverlayReactionEvent(
            fromUserId = "u1",
            fromUsername = "a",
            reaction = "heart",
            targetUserId = "u1",
        )
        assertFalse(
            OverlayReactionCoordinator.shouldDeliverIncomingReaction(
                event = event,
                selfUserId = "u1",
                overlaySessionActive = true,
            ),
        )
    }
}
