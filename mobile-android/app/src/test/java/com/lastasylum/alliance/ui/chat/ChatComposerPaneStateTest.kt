package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatComposerPaneStateTest {
    @Test
    fun toComposerPane_includesEditingMessage() {
        val editing = ChatMessage(
            _id = "507f1f77bcf86cd799439014",
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "edit me",
        )
        val pane = ChatState(editingMessage = editing).toComposerPane()
        assertEquals(editing, pane.editingMessage)
    }

    @Test
    fun toComposerPane_clearsReplyWhenEditing() {
        val reply = ChatMessage(
            _id = "reply-1",
            allianceId = "pt:team1",
            senderId = "u2",
            senderUsername = "bob",
            senderRole = "R4",
            text = "reply",
        )
        val editing = ChatMessage(
            _id = "507f1f77bcf86cd799439014",
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "edit me",
        )
        val pane = ChatState(
            replyToMessage = reply,
            editingMessage = editing,
        ).toComposerPane()
        assertEquals(reply, pane.replyToMessage)
        assertEquals(editing, pane.editingMessage)
    }

    @Test
    fun toComposerPane_defaultEditingIsNull() {
        assertNull(ChatState().toComposerPane().editingMessage)
    }
}
