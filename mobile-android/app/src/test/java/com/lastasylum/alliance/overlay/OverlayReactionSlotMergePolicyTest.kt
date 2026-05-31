package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionSlotMergePolicyTest {

    private val head = OverlayReactionMergeableHead(
        fromUserId = "user-a",
        broadcast = true,
        createdAtMs = 1_000L,
    )

    private val incoming = OverlayReactionBurstRequest(
        fromUserId = "user-a",
        fromDisplayName = "Alpha",
        reactionId = "heart",
        broadcast = true,
    )

    @Test
    fun canMerge_sameSenderWithinWindow() {
        assertTrue(
            OverlayReactionSlotMergePolicy.canMergeIntoHead(
                head,
                incoming,
                nowMs = 2_500L,
            ),
        )
    }

    @Test
    fun cannotMerge_afterWindow() {
        assertFalse(
            OverlayReactionSlotMergePolicy.canMergeIntoHead(
                head,
                incoming,
                nowMs = 3_100L,
            ),
        )
    }

    @Test
    fun cannotMerge_differentSender() {
        assertFalse(
            OverlayReactionSlotMergePolicy.canMergeIntoHead(
                head,
                incoming.copy(fromUserId = "user-b"),
                nowMs = 1_500L,
            ),
        )
    }

    @Test
    fun cannotMerge_differentBroadcastFlag() {
        assertFalse(
            OverlayReactionSlotMergePolicy.canMergeIntoHead(
                head,
                incoming.copy(broadcast = false),
                nowMs = 1_500L,
            ),
        )
    }
}
