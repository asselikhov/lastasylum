package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLazyIndexTest {
    @Test
    fun chatLazyIndexForMessageId_fallsBackWhenDerivedTimelineStale() {
        val messages = listOf(
            ChatMessage(
                _id = "new",
                allianceId = "pt:team",
                text = "new",
                senderId = "u1",
                senderUsername = "a",
                senderRole = "member",
            ),
            ChatMessage(
                _id = "old",
                allianceId = "pt:team",
                text = "old",
                senderId = "u1",
                senderUsername = "a",
                senderRole = "member",
            ),
        )
        val staleDerived = ChatMessagesListDerived.Empty
        val idx = chatLazyIndexForMessageId(messages, staleDerived, "old")
        assertEquals(1, idx)
    }
}
