package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.mergePreservingAttachments
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
    fun upsertMessage_preservesAttachmentsWhenIncomingEmpty() {
        val known = linkedSetOf("1")
        val withImage = msg("1", "a").copy(
            attachments = listOf(
                ChatAttachment(kind = "image", url = "/chat/attachments/abc"),
            ),
        )
        val incoming = msg("1", "a")
        val r = upsertMessage(listOf(withImage), incoming, known)
        assertEquals(1, r.messages.size)
        assertEquals("/chat/attachments/abc", r.messages[0].attachments.single().url)
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
    fun upsertMessagesBatch_appliesInOrderOnce() {
        val known = linkedSetOf<String>()
        val index = mutableMapOf<String, Int>()
        val current = listOf(msg("1", "a"))
        val batch = listOf(msg("2", "b"), msg("3", "c"))
        val r = upsertMessagesBatch(current, batch, known, index)
        assertEquals(listOf("3", "2", "1"), r.messages.map { it._id })
        assertEquals("3", r.newestMessageKey)
    }

    @Test
    fun chatMessagesListContentEqual_detectsChanges() {
        val a = listOf(msg("1", "a"), msg("2", "b"))
        val b = listOf(msg("1", "a"), msg("2", "b"))
        val c = listOf(msg("1", "a"), msg("2", "c"))
        assertTrue(chatMessagesListContentEqual(a, b))
        assertFalse(chatMessagesListContentEqual(a, c))
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
    fun stripRedundantPendingOutgoing_removesPendingWhenServerEchoExists() {
        val pending = msg("pending-1", "hello")
        val server = msg("server-1", "hello")
        val out = stripRedundantPendingOutgoing(listOf(server, pending), "u1")
        assertEquals(listOf("server-1"), out.map { it._id })
    }

    @Test
    fun dropMatchingPendingOutgoing_ignoresReplyToNullVsEmpty() {
        val pending = msg("pending-1", "hello", replyTo = null)
        val server = msg("server-1", "hello").copy(replyToMessageId = "")
        val out = dropMatchingPendingOutgoing(listOf(pending), listOf(server), "u1")
        assertTrue(out.isEmpty())
    }

    @Test
    fun replaceMatchingPendingOutgoing_swapsOptimisticRow() {
        val pending = msg("pending-1", "hello")
        val server = msg("server-1", "hello")
        val current = listOf(pending, msg("older", "x"))
        val replacement = replaceMatchingPendingOutgoing(current, server, "u1")
        requireNotNull(replacement)
        assertEquals("server-1", replacement.messages[0]._id)
        assertEquals("pending-1", replacement.pendingId)
        assertEquals(0, replacement.replacedIndex)
    }

    @Test
    fun dedupeMessagesByIdNewestFirst_keepsFirstOccurrence() {
        val server = msg("same", "hello")
        val dup = server.copy(text = "hello (server)")
        val pending = msg("pending-1", "hello")
        val list = listOf(server, dup, pending)
        val out = dedupeMessagesByIdNewestFirst(list)
        assertEquals(listOf("same", "pending-1"), out.map { it._id })
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

    @Test
    fun syncSelections_prunesStaleSelectionIds() {
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "R1",
            messages = listOf(msg("1")),
            selectedMessageIds = setOf("1", "ghost"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_dropsOthersMessagesForNonAdminSelection() {
        val other = msg("2").copy(senderId = "other")
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "R1",
            messages = listOf(msg("1"), other),
            selectedMessageIds = setOf("1", "2"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_keepsAdminSelectionForOthersMessages() {
        val globalRoom = ChatAllianceIds.GLOBAL
        val other = msg("2").copy(senderId = "other", allianceId = globalRoom)
        val state = ChatState(
            currentUserId = "u1",
            currentUserRole = "ADMIN",
            isAppAdmin = true,
            messages = listOf(msg("1").copy(allianceId = globalRoom), other),
            selectedMessageIds = setOf("1", "2"),
        )
        val synced = syncSelections(state)
        assertEquals(setOf("1", "2"), synced.selectedMessageIds)
    }

    @Test
    fun syncSelections_clearsOrphanBulkConfirm() {
        val state = ChatState(
            messages = listOf(msg("1")),
            selectedMessageIds = emptySet(),
            confirmBulkDelete = true,
        )
        val synced = syncSelections(state)
        assertFalse(synced.confirmBulkDelete)
    }
}
