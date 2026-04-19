package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageReplyPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListMutationsTest {

    private fun msg(
        id: String,
        text: String = "t",
        replyTo: ChatMessageReplyPreview? = null,
    ) = ChatMessage(
        _id = id,
        allianceId = "alliance",
        roomId = "room",
        senderId = "u1",
        senderUsername = "user",
        senderRole = "R1",
        text = text,
        replyTo = replyTo,
    )

    @Test
    fun upsertMessage_prependsNewWithId() {
        val known = linkedSetOf<String>()
        val current = listOf(msg("1", "a"))
        val incoming = msg("2", "b")
        val r = upsertMessage(current, incoming, known)
        assertEquals(listOf("2", "1"), r.messages.map { it._id })
        assertEquals("2", r.newestMessageKey)
        assertTrue(known.contains("2"))
    }

    @Test
    fun upsertMessage_replacesExistingById() {
        val known = linkedSetOf("1")
        val current = listOf(msg("1", "old"))
        val incoming = msg("1", "new")
        val r = upsertMessage(current, incoming, known)
        assertEquals(1, r.messages.size)
        assertEquals("new", r.messages[0].text)
        assertNull(r.newestMessageKey)
    }

    @Test
    fun mergeOlderPage_deduplicatesKnownIds() {
        val known = linkedSetOf("a", "b")
        val current = listOf(msg("a"), msg("b"))
        val older = listOf(msg("b"), msg("c"))
        val merged = mergeOlderPage(current, older, known)
        assertEquals(listOf("a", "b", "c"), merged.map { it._id })
        assertTrue(known.contains("c"))
    }

    @Test
    fun capNewestFirst_keepsHead() {
        val newestFirst = (100 downTo 1).map { i -> msg("id$i", text = "$i") }
        val capped = capNewestFirst(newestFirst, 50)
        assertEquals(50, capped.size)
        assertEquals("id100", capped.first()._id)
        assertEquals("id51", capped.last()._id)
    }

    @Test
    fun scrubMessagesAfterRemove_clearsReplyTo() {
        val known = linkedSetOf("1", "2", "3")
        val reply = ChatMessageReplyPreview(
            _id = "2",
            senderId = "x",
            senderUsername = "x",
            senderRole = "R1",
            text = "quoted",
        )
        val messages = listOf(msg("1"), msg("3", replyTo = reply))
        val out = scrubMessagesAfterRemove(messages, "2", known)
        assertEquals(listOf("1", "3"), out.map { it._id })
        assertNull(out[1].replyTo)
        assertFalse(known.contains("2"))
    }

    @Test
    fun syncSelections_clearsMissingReply() {
        val state = ChatState(
            currentUserId = "u",
            currentUserRole = "R1",
            messages = listOf(msg("1")),
            replyToMessage = msg("99", "ghost"),
        )
        val synced = syncSelections(state)
        assertNull(synced.replyToMessage)
    }

    @Test
    fun syncSelections_noOpWhenNoSelections() {
        val state = ChatState(
            messages = listOf(msg("1")),
        )
        val synced = syncSelections(state)
        assertTrue(synced === state)
    }
}
