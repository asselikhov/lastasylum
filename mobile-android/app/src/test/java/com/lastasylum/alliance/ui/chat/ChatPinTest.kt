package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPinTest {
    @Test
    fun toPinnedPreview_returnsNullWithoutId() {
        val msg = ChatMessage(
            _id = null,
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "hi",
        )
        assertNull(msg.toPinnedPreview())
    }

    @Test
    fun toPinnedPreview_mapsFields() {
        val msg = ChatMessage(
            _id = "507f1f77bcf86cd799439014",
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "pinned text",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val preview = msg.toPinnedPreview()
        assertNotNull(preview)
        assertEquals("507f1f77bcf86cd799439014", preview!!.id)
        assertEquals("pinned text", preview.text)
        assertEquals("alice", preview.senderUsername)
    }

    @Test
    fun resolveChatPinnedPreview_prefersServerPreview() {
        val serverPreview = PinnedMessagePreviewDto(
            id = "507f1f77bcf86cd799439014",
            text = "from server",
            senderUsername = "alice",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val resolved = resolveChatPinnedPreview(
            pinnedMessageId = "507f1f77bcf86cd799439014",
            pinnedMessage = serverPreview,
            messages = emptyList(),
        )
        assertEquals(serverPreview, resolved)
    }

    @Test
    fun resolveChatPinnedPreview_fallsBackToMessageList() {
        val msg = ChatMessage(
            _id = "507f1f77bcf86cd799439014",
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "local fallback",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val resolved = resolveChatPinnedPreview(
            pinnedMessageId = "507f1f77bcf86cd799439014",
            pinnedMessage = null,
            messages = listOf(msg),
        )
        assertNotNull(resolved)
        assertEquals("local fallback", resolved!!.text)
    }

    @Test
    fun resolveForumPinnedPreview_fallsBackToMessageList() {
        val msg = TeamForumMessageDto(
            id = "507f1f77bcf86cd799439014",
            topicId = "topic1",
            teamId = "team1",
            senderUserId = "u1",
            senderUsername = "alice",
            text = "forum pin",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
        val resolved = resolveForumPinnedPreview(
            pinnedMessageId = msg.id,
            pinnedMessage = null,
            messages = listOf(msg),
        )
        assertNotNull(resolved)
        assertEquals("forum pin", resolved!!.text)
    }
}
