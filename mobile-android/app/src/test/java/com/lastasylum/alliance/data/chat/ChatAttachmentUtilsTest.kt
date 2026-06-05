package com.lastasylum.alliance.data.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatAttachmentUtilsTest {

    @Test
    fun parseChatAttachments_readsUrl() {
        val payload = JSONObject(
            """
            {
              "attachments": [
                {
                  "kind": "image",
                  "url": "/chat/attachments/abc123",
                  "mimeType": "image/jpeg",
                  "size": 1024
                }
              ]
            }
            """.trimIndent(),
        )
        val parsed = payload.parseChatAttachments()
        assertEquals(1, parsed.size)
        assertEquals("/chat/attachments/abc123", parsed[0].url)
    }

    @Test
    fun parseChatAttachments_buildsUrlFromFileId() {
        val payload = JSONObject(
            """
            {
              "attachments": [
                {
                  "kind": "image",
                  "fileId": "507f1f77bcf86cd799439011",
                  "mimeType": "image/png"
                }
              ]
            }
            """.trimIndent(),
        )
        val parsed = payload.parseChatAttachments()
        assertEquals("/chat/attachments/507f1f77bcf86cd799439011", parsed.single().url)
    }

    @Test
    fun mergePreservingAttachments_keepsExistingWhenIncomingEmpty() {
        val existing = ChatMessage(
            _id = "1",
            allianceId = "a",
            senderId = "u",
            senderUsername = "u",
            senderRole = "R1",
            text = "",
            attachments = listOf(ChatAttachment(kind = "image", url = "/chat/attachments/x")),
        )
        val incoming = existing.copy(attachments = emptyList(), text = "edited")
        val merged = incoming.mergePreservingAttachments(existing)
        assertEquals("/chat/attachments/x", merged.attachments.single().url)
        assertEquals("edited", merged.text)
    }

    @Test
    fun mergeIncomingChatUpdate_compactReaction_preservesTextAndSender() {
        val existing = ChatMessage(
            _id = "1",
            allianceId = "a",
            roomId = "room",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R1",
            text = "hello world",
        )
        val compact = ChatMessage(
            _id = "1",
            allianceId = "",
            roomId = "room",
            senderId = "",
            senderUsername = "",
            senderRole = "",
            text = "",
            reactions = listOf(
                ChatReaction(emoji = "👍", count = 2, reactedByMe = true),
            ),
        )
        assertTrue(compact.isCompactReactionSocketUpdate())
        val merged = compact.mergeIncomingChatUpdate(existing)
        assertEquals("hello world", merged.text)
        assertEquals("alice", merged.senderUsername)
        assertEquals("u1", merged.senderId)
        assertEquals(2, merged.reactions.single().count)
    }

    @Test
    fun mergeIncomingChatUpdate_edit_appliesNewText() {
        val existing = ChatMessage(
            _id = "1",
            allianceId = "a",
            roomId = "room",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R1",
            text = "before",
        )
        val edited = existing.copy(
            text = "after edit",
            editedAt = "2026-05-22T10:05:00.000Z",
        )
        val merged = edited.mergeIncomingChatUpdate(existing)
        assertEquals("after edit", merged.text)
    }
}
