package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageVisibilityPolicyTest {
    @Test
    fun isMessageVisible_falseWhenIdMatchesHiddenCursor() {
        val id = "507f1f77bcf86cd799439011"
        assertFalse(
            ChatMessageVisibilityPolicy.isMessageVisible(
                messageId = id,
                createdAt = "2024-01-01T12:00:00Z",
                hiddenBeforeMessageId = id,
            ),
        )
    }

    @Test
    fun isMessageVisible_trueWhenNoHiddenCursor() {
        assertTrue(
            ChatMessageVisibilityPolicy.isMessageVisible(
                messageId = "507f1f77bcf86cd799439012",
                createdAt = "2024-01-01T12:00:00Z",
                hiddenBeforeMessageId = null,
            ),
        )
    }

    @Test
    fun isMessageVisible_trueForNewerObjectId() {
        assertTrue(
            ChatMessageVisibilityPolicy.isMessageVisible(
                messageId = "507f1f77bcf86cd799439012",
                createdAt = null,
                hiddenBeforeMessageId = "507f1f77bcf86cd799439011",
            ),
        )
    }
}
