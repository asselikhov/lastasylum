package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MessagesListDeriverPatchTest {

    private fun chatMsg(id: String, sender: String = "u1") = ChatMessage(
        _id = id,
        allianceId = "a",
        roomId = "r",
        senderId = sender,
        senderUsername = "user",
        senderRole = "R1",
        text = "hi $id",
        createdAt = "2024-06-15T12:00:00Z",
    )

    @Test
    fun patchMessage_updatesSingleRowWithoutTimelineResize() {
        val messages = listOf(
            chatMsg("507f1f77bcf86cd799439013"),
            chatMsg("507f1f77bcf86cd799439011"),
        )
        val derived = buildChatMessagesListDerived(messages)
        val withReaction = messages.mapIndexed { i, m ->
            if (i == 1) m.copy(reactions = listOf(ChatReaction("🔥", 1, true))) else m
        }
        val patched = buildChatMessagesListDerivedAfterPatchMessage(derived, withReaction, messageIndex = 1)
        assertEquals(derived.timeline.size, patched.timeline.size)
        val item = patched.timeline.filterIsInstance<ChatTimelineEntry.ChatMessageItem>()
            .first { it.messageIndex == 1 }
        assertEquals("🔥", item.message.reactions.first().emoji)
    }

    @Test
    fun forumDerivedAfterMessageContentPatch_keepsTimelineWhenSizeUnchanged() {
        val messages = listOf(
            forumMsg("a"),
            forumMsg("b"),
        )
        val derived = buildForumMessagesListDerived(messages)
        val patched = forumDerivedAfterMessageContentPatch(derived, messages)
        assertSame(derived, patched)
    }

    private fun forumMsg(id: String) = TeamForumMessageDto(
        id = id,
        topicId = "t1",
        teamId = "team",
        senderUserId = "u1",
        senderUsername = "user",
        text = "hi",
        createdAt = "2024-06-15T12:00:00Z",
        updatedAt = "2024-06-15T12:00:00Z",
        deletedAt = null,
        replyToMessageId = null,
        imageRelativeUrl = null,
        imageRelativeUrls = emptyList(),
        fileRelativeUrl = null,
    )
}
