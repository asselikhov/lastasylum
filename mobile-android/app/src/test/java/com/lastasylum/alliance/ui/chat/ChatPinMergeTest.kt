package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPinMergeTest {
    private val preview = PinnedMessagePreviewDto(
        id = "507f1f77bcf86cd799439014",
        text = "pinned",
        senderUsername = "alice",
        createdAt = "2026-01-01T00:00:00Z",
    )

    private val roomWithIdOnly = ChatRoomDto(
        id = "room1",
        allianceId = "pt:team",
        title = "Raid",
        pinnedMessageId = preview.id,
    )

    @Test
    fun mergePinFromPrevious_restoresPreviewWhenServerOmitsIt() {
        val previous = roomWithIdOnly.copy(pinnedMessage = preview)
        val merged = roomWithIdOnly.mergePinFromPrevious(previous)
        assertEquals(preview, merged.pinnedMessage)
    }

    @Test
    fun mergePinFromEvent_keepsExistingPreviewWhenEventPreviewNull() {
        val room = roomWithIdOnly.copy(pinnedMessage = preview)
        val event = ChatRoomPinChangedEvent(
            roomId = "room1",
            pinnedMessageId = preview.id,
            pinnedMessage = null,
        )
        assertEquals(preview, room.mergePinFromEvent(event).pinnedMessage)
    }

    @Test
    fun ensureRoomPinPreview_attachesPreviewFromSource() {
        val ensured = ensureRoomPinPreview(roomWithIdOnly, preview, "user1")
        assertNotNull(ensured.pinnedMessage)
        assertEquals(preview.text, ensured.pinnedMessage?.text)
    }

    @Test
    fun ensureRoomPinPreview_noOpWhenUnpinned() {
        val room = ChatRoomDto(id = "room1", allianceId = "pt:team", title = "Raid")
        assertNull(ensureRoomPinPreview(room, preview, "user1").pinnedMessageId)
    }
}
