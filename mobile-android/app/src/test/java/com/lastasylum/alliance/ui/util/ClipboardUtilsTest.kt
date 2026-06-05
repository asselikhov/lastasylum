package com.lastasylum.alliance.ui.util

import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardUtilsTest {
    @Test
    fun appendTextToDraft_appendsWithSpace() {
        assertEquals("hello world", appendTextToDraft("hello", "world"))
    }

    @Test
    fun appendTextToDraft_emptyCurrent() {
        assertEquals("paste", appendTextToDraft("", "paste"))
    }

    @Test
    fun chatMessageHasMenuCopyAction_falseForImageOnly() {
        val message = ChatMessage(
            _id = "m1",
            allianceId = "pt:team",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "",
            attachments = listOf(ChatAttachment(kind = "image", url = "/chat/attachments/x")),
        )
        assertFalse(chatMessageHasMenuCopyAction(message))
    }

    @Test
    fun chatMessageHasMenuCopyAction_trueForCaptionedImage() {
        val message = ChatMessage(
            _id = "m1",
            allianceId = "pt:team",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "caption",
            attachments = listOf(ChatAttachment(kind = "image", url = "/chat/attachments/x")),
        )
        assertTrue(chatMessageHasMenuCopyAction(message))
    }

    @Test
    fun forumMessageHasMenuCopyAction_falseForImageOnly() {
        val message = TeamForumMessageDto(
            id = "m1",
            topicId = "t1",
            teamId = "team1",
            senderUserId = "u1",
            senderUsername = "alice",
            text = "",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
            imageRelativeUrls = listOf("/forum/files/x"),
        )
        assertFalse(forumMessageHasMenuCopyAction(message))
    }
}
