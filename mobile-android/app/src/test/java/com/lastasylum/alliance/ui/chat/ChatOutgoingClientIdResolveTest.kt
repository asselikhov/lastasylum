package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatOutgoingClientIdResolveTest {
    private fun message(clientMessageId: String? = null) = ChatMessage(
        _id = "507f1f77bcf86cd799439011",
        allianceId = "a1",
        roomId = "room-1",
        senderId = "u1",
        senderUsername = "me",
        senderRole = "R1",
        text = "hi",
        clientMessageId = clientMessageId,
    )

    @Test
    fun usesExplicitClientMessageId() {
        assertEquals(
            "cid-1",
            resolveOutgoingClientMessageId(message("cid-1"), setOf("cid-2", "cid-3")),
        )
    }

    @Test
    fun singleInflightFallbackOnlyWhenOnePending() {
        assertEquals(
            "only-one",
            resolveOutgoingClientMessageId(message(null), setOf("only-one")),
        )
    }

    @Test
    fun noGuessWhenMultipleInflight() {
        assertNull(
            resolveOutgoingClientMessageId(message(null), setOf("a", "b")),
        )
    }
}
