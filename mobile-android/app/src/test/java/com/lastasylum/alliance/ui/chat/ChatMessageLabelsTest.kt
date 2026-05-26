package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageLabelsTest {

    @Test
    fun showsEdited_whenEditedClearlyAfterCreated() {
        val msg = ChatMessage(
            allianceId = "pt:1",
            senderId = "u1",
            senderUsername = "A",
            senderRole = "R2",
            text = "hi",
            createdAt = "2026-05-22T10:00:00.000Z",
            editedAt = "2026-05-22T10:05:00.000Z",
        )
        assertTrue(chatMessageShowsEditedLabel(msg))
    }

    @Test
    fun hidesEdited_whenEditedAtMissing() {
        val msg = ChatMessage(
            allianceId = "pt:1",
            senderId = "u1",
            senderUsername = "A",
            senderRole = "R2",
            text = "hi",
            createdAt = "2026-05-22T10:00:00.000Z",
            editedAt = null,
        )
        assertFalse(chatMessageShowsEditedLabel(msg))
    }

    @Test
    fun hidesEdited_whenCreatedAtMissing() {
        val msg = ChatMessage(
            allianceId = "pt:1",
            senderId = "u1",
            senderUsername = "A",
            senderRole = "R2",
            text = "hi",
            createdAt = "",
            editedAt = "2026-05-22T10:05:00.000Z",
        )
        assertFalse(chatMessageShowsEditedLabel(msg))
        assertNull(msg.normalizeEditedAtForDisplay().editedAt)
    }

    @Test
    fun hidesEdited_whenEditedAtSameSecondAsCreated() {
        val msg = ChatMessage(
            allianceId = "pt:1",
            senderId = "u1",
            senderUsername = "A",
            senderRole = "R2",
            text = "hi",
            createdAt = "2026-05-22T10:00:00.000Z",
            editedAt = "2026-05-22T10:00:00.500Z",
        )
        assertFalse(chatMessageShowsEditedLabel(msg))
    }
}
