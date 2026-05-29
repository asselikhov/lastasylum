package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageReadStatusTest {
    @Test
    fun isChatMessageReadByPeer_trueWhenCursorAtOrPastMessage() {
        assertTrue(
            isChatMessageReadByPeer(
                messageId = "000000000000000000000050",
                otherReadUptoMessageId = "000000000000000000000060",
            ),
        )
        assertTrue(
            isChatMessageReadByPeer(
                messageId = "000000000000000000000050",
                otherReadUptoMessageId = "000000000000000000000050",
            ),
        )
    }

    @Test
    fun isChatMessageReadByPeer_falseWhenCursorBehindMessage() {
        assertFalse(
            isChatMessageReadByPeer(
                messageId = "000000000000000000000070",
                otherReadUptoMessageId = "000000000000000000000050",
            ),
        )
    }

    @Test
    fun isChatMessageReadByPeer_falseWhenCursorMissing() {
        assertFalse(
            isChatMessageReadByPeer(
                messageId = "000000000000000000000050",
                otherReadUptoMessageId = null,
            ),
        )
    }
}
