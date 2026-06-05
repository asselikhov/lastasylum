package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomPinChangedEvent
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun mergePinFromPrevious_preservesOptimisticPinWhileInFlight() {
        val previous = roomWithIdOnly.copy(pinnedMessage = preview)
        val serverStale = ChatRoomDto(
            id = "room1",
            allianceId = "pt:team",
            title = "Raid",
        )
        val merged = serverStale.mergePinFromPrevious(previous, pinOperationInFlight = true)
        assertEquals(preview.id, merged.pinnedMessageId)
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

    @Test
    fun topicMergePinFromPrevious_preservesOptimisticPinWhileInFlight() {
        val previous = TopicPinSnapshot(
            pinnedMessageId = preview.id,
            pinnedAt = "2026-01-01T00:00:00Z",
            pinnedByUserId = "user1",
            pinnedMessage = preview,
        )
        val serverStale = TopicPinSnapshot(
            pinnedMessageId = null,
            pinnedAt = null,
            pinnedByUserId = null,
            pinnedMessage = null,
        )
        val merged = serverStale.mergePinFromPrevious(previous, pinOperationInFlight = true)
        assertEquals(preview.id, merged.pinnedMessageId)
        assertEquals(preview, merged.pinnedMessage)
    }

    @Test
    fun mergePinFromEvent_usesPinnedMessagesFromServer() {
        val historyPreview = preview.copy(text = "from history")
        val room = roomWithIdOnly.copy(pinnedMessage = preview)
        val event = ChatRoomPinChangedEvent(
            roomId = "room1",
            pinnedMessageId = preview.id,
            pinnedMessage = null,
            pinnedMessages = listOf(historyPreview),
        )
        val merged = room.mergePinFromEvent(event)
        assertEquals(listOf(historyPreview), merged.pinnedMessages)
        assertEquals(historyPreview, merged.pinnedMessage)
    }

    @Test
    fun serverPinHistoryFromRoom_prefersPinnedMessagesList() {
        val room = roomWithIdOnly.copy(
            pinnedMessage = preview,
            pinnedMessages = listOf(preview, preview.copy(mongoId = "other")),
        )
        assertEquals(2, serverPinHistoryFromRoom(room).size)
    }

    @Test
    fun mergePinHistory_unionsServerAndLocalEntries() {
        val localOnly = preview.copy(mongoId = "local-only", text = "local")
        val merged = mergePinHistory(listOf(preview), listOf(preview, localOnly))
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == preview.id })
        assertTrue(merged.any { it.id == "local-only" })
    }

    @Test
    fun mergePinHistory_serverPreviewWinsOnConflict() {
        val server = listOf(preview.copy(text = "server"))
        val local = listOf(preview.copy(text = "local"))
        assertEquals("server", mergePinHistory(server, local).first().text)
    }

    @Test
    fun mergePinHistory_returnsLocalWhenServerEmpty() {
        assertEquals(listOf(preview), mergePinHistory(emptyList(), listOf(preview)))
    }

    @Test
    fun mergePinHistory_preservesLocalWhenServerOmitsDeletedPin() {
        val hiddenPin = preview.copy(mongoId = "hidden-pin", text = "cleared locally")
        val server = listOf(preview)
        val local = listOf(preview, hiddenPin)
        val merged = mergePinHistory(server, local)
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.id == "hidden-pin" })
    }

    @Test
    fun topicMergePinFromEvent_keepsExistingPreviewWhenEventPreviewNull() {
        val topic = TopicPinSnapshot(
            pinnedMessageId = preview.id,
            pinnedAt = "2026-01-01T00:00:00Z",
            pinnedByUserId = "user1",
            pinnedMessage = preview,
        )
        val event = TeamForumTopicPinChangedEvent(
            teamId = "team1",
            topicId = "topic1",
            pinnedMessageId = preview.id,
            pinnedMessage = null,
        )
        assertEquals(preview, topic.mergePinFromEvent(event).pinnedMessage)
    }
}
