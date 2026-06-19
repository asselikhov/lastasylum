package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutoMarkReadPolicyTest {
    @Test
    fun shouldAutoMarkReadIncomingAtBottom_requiresBothFlags() {
        assertTrue(
            shouldAutoMarkReadIncomingAtBottom(
                isRoomActivelyViewed = true,
                messageListAtBottom = true,
            ),
        )
        assertFalse(
            shouldAutoMarkReadIncomingAtBottom(
                isRoomActivelyViewed = true,
                messageListAtBottom = false,
            ),
        )
        assertFalse(
            shouldAutoMarkReadIncomingAtBottom(
                isRoomActivelyViewed = false,
                messageListAtBottom = true,
            ),
        )
        assertFalse(
            shouldAutoMarkReadIncomingAtBottom(
                isRoomActivelyViewed = false,
                messageListAtBottom = false,
            ),
        )
    }

    @Test
    fun shouldAutoMarkReadIncomingAtBottom_scrolledUpWhileViewing_blocksBatchMark() {
        assertFalse(
            shouldAutoMarkReadIncomingAtBottom(
                isRoomActivelyViewed = true,
                messageListAtBottom = false,
            ),
        )
    }
}
