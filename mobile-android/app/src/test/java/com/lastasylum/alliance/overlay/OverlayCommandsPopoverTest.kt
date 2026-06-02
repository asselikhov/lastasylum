package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayCommandsPopoverTest {

    @Test
    fun resolveReplyToLogIdForSend_returnsNullOutsideReplyMode() {
        val resolved = overlayResolveReplyToLogIdForSend(
            replyMode = false,
            replyToLogId = "6641c45bbbee8f2a06e9f111",
        )
        assertNull(resolved)
    }

    @Test
    fun resolveReplyToLogIdForSend_returnsTrimmedIdInReplyMode() {
        val resolved = overlayResolveReplyToLogIdForSend(
            replyMode = true,
            replyToLogId = " 6641c45bbbee8f2a06e9f111 ",
        )
        assertEquals("6641c45bbbee8f2a06e9f111", resolved)
    }

    @Test
    fun resolveReplyToLogIdForSend_returnsNullForBlankId() {
        val resolved = overlayResolveReplyToLogIdForSend(
            replyMode = true,
            replyToLogId = "   ",
        )
        assertNull(resolved)
    }

    @Test
    fun overlayReactionUsesReplyChannel_onlyInExplicitReplyMode() {
        assertFalse(
            overlayReactionUsesReplyChannel(
                replyMode = false,
                replyToLogId = "6641c45bbbee8f2a06e9f111",
            ),
        )
        assertTrue(
            overlayReactionUsesReplyChannel(
                replyMode = true,
                replyToLogId = "6641c45bbbee8f2a06e9f111",
            ),
        )
        assertFalse(
            overlayReactionUsesReplyChannel(
                replyMode = true,
                replyToLogId = "  ",
            ),
        )
    }
}
