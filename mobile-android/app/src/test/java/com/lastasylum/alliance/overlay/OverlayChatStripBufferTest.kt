package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.chat.isCompactReactionSocketUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OverlayChatStripBufferTest {
    @Test
    fun seedFromHistory_thenVisibleForPreview() {
        val buffer = OverlayChatStripBuffer(
            messageTtlSeconds = 3600,
            bufferCap = 100,
            maxPreviewMessages = 3,
        )
        val created = Instant.now().toString()
        val msg = ChatMessage(
            _id = "abc123",
            allianceId = "ally",
            roomId = "room1",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "hello",
            createdAt = created,
        )
        buffer.seedFromHistory(listOf(msg))
        val visible = buffer.visibleForPreview()
        assertEquals(1, visible.size)
        assertEquals("hello", visible.first().text)
    }

    @Test
    fun clear_emptiesBuffer() {
        val buffer = OverlayChatStripBuffer()
        buffer.seedFromHistory(
            listOf(
                ChatMessage(
                    allianceId = "a",
                    roomId = "r",
                    senderId = "x",
                    senderUsername = "x",
                    senderRole = "R2",
                    text = "m",
                    createdAt = Instant.now().toString(),
                ),
            ),
        )
        buffer.clear()
        assertTrue(buffer.visibleForPreview().isEmpty())
    }

    @Test
    fun prune_drops_messages_older_than_ttl_by_effective_instant() {
        val ttl = OverlayChatStripBuffer.DEFAULT_MESSAGE_TTL_SECONDS
        val buffer = OverlayChatStripBuffer(
            messageTtlSeconds = ttl,
            bufferCap = 100,
            maxPreviewMessages = 10,
        )
        val oldCreated = Instant.now().minusSeconds(ttl + 30).toString()
        val newCreated = Instant.now().toString()
        buffer.seedFromHistory(
            listOf(
                ChatMessage(
                    _id = "old1",
                    allianceId = "a",
                    roomId = "r",
                    senderId = "u-old",
                    senderUsername = "Old",
                    senderRole = "R2",
                    text = "stale",
                    createdAt = oldCreated,
                ),
                ChatMessage(
                    _id = "new1",
                    allianceId = "a",
                    roomId = "r",
                    senderId = "u-new",
                    senderUsername = "New",
                    senderRole = "R3",
                    text = "fresh",
                    createdAt = newCreated,
                ),
            ),
        )
        buffer.prune()
        val visible = buffer.visibleForPreview()
        assertEquals(1, visible.size)
        assertEquals("fresh", visible.first().text)
    }

    @Test
    fun visibleForPreview_hidesChatUntilGameSessionStarted() {
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = 3600)
        val msg = ChatMessage(
            _id = "offline-1",
            allianceId = "a",
            roomId = "r",
            senderId = "ally-2",
            senderUsername = "Ally",
            senderRole = "R2",
            text = "while away",
            createdAt = Instant.now().toString(),
        )
        buffer.upsert(msg)
        buffer.mergeReceiveTimeline(msg, selfId = "me")
        assertTrue(buffer.visibleForPreview().isEmpty())
        buffer.resetVisibleSession()
        buffer.mergeReceiveTimeline(msg, selfId = "me")
        assertEquals(1, buffer.visibleForPreview().size)
    }

    @Test
    fun touchReceivedNow_refreshesTtlForExistingMessage() {
        val ttl = OverlayChatStripBuffer.DEFAULT_MESSAGE_TTL_SECONDS
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = ttl)
        val oldCreated = Instant.now().minusSeconds(ttl + 90).toString()
        val msg = ChatMessage(
            _id = "touch-1",
            allianceId = "a",
            roomId = "r",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "coords",
            createdAt = oldCreated,
        )
        buffer.resetVisibleSession()
        buffer.upsert(msg)
        buffer.receivedAtMap()[msg.stableKey()] =
            Instant.now().minusSeconds(ttl + 90)
        assertTrue(buffer.visibleForPreview().isEmpty())
        buffer.touchReceivedNow(msg)
        val visible = buffer.visibleForPreview()
        assertEquals(1, visible.size)
        assertEquals("coords", visible.first().text)
    }

    @Test
    fun containsMessageId_detectsBufferedMessage() {
        val buffer = OverlayChatStripBuffer()
        val msg = ChatMessage(
            _id = "dup-id",
            allianceId = "a",
            roomId = "r",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "hello",
            createdAt = Instant.now().toString(),
        )
        buffer.resetVisibleSession()
        buffer.upsert(msg)
        assertTrue(buffer.containsMessageId("dup-id"))
        org.junit.Assert.assertFalse(buffer.containsMessageId("other"))
    }

    @Test
    fun isCompactReactionSocketUpdate_detectsReactionOnlyPayload() {
        val compact = ChatMessage(
            _id = "m1",
            allianceId = "a",
            roomId = "r",
            senderId = "",
            senderUsername = "",
            senderRole = "",
            text = "",
            reactions = listOf(ChatReaction(emoji = "❤", count = 1, reactedByMe = false)),
        )
        assertTrue(compact.isCompactReactionSocketUpdate())
        val full = compact.copy(senderId = "u1", senderUsername = "Pilot", text = "hi")
        org.junit.Assert.assertFalse(full.isCompactReactionSocketUpdate())
    }

    @Test
    fun upsert_mergesReactionsWithoutWipingExistingCounts() {
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = 3600)
        val existing = ChatMessage(
            _id = "rx-1",
            allianceId = "a",
            roomId = "r",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "hi",
            createdAt = Instant.now().toString(),
            reactions = listOf(ChatReaction(emoji = "❤", count = 3, reactedByMe = false)),
        )
        buffer.resetVisibleSession()
        buffer.upsert(existing)
        buffer.touchReceivedNow(existing)
        val socket = existing.copy(
            reactions = listOf(ChatReaction(emoji = "❤", count = 0, reactedByMe = true)),
        )
        buffer.upsert(socket)
        val merged = buffer.visibleForPreview().first().reactions.first()
        assertEquals(3, merged.count)
    }

    @Test
    fun removeServerMessageAndOptimisticEchoes_dropsPendingTwin() {
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = 3600)
        buffer.resetVisibleSession()
        val optimistic = ChatMessage(
            _id = "overlay-pending-1",
            allianceId = "a",
            roomId = "raid",
            senderId = "me",
            senderUsername = "Me",
            senderRole = "R2",
            text = "Атака",
            createdAt = Instant.now().toString(),
        )
        val server = optimistic.copy(_id = "server-99")
        buffer.upsert(optimistic)
        buffer.touchReceivedNow(optimistic)
        buffer.upsert(server)
        buffer.touchReceivedNow(server)
        assertEquals(2, buffer.visibleForPreview().size)
        buffer.removeServerMessageAndOptimisticEchoes("server-99")
        assertTrue(buffer.visibleForPreview().isEmpty())
    }

    @Test
    fun removeOptimisticEchoesForServerMessage_beforeUpsert() {
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = 3600)
        buffer.resetVisibleSession()
        val optimistic = ChatMessage(
            _id = "overlay-pending-2",
            allianceId = "a",
            roomId = "raid",
            senderId = "me",
            senderUsername = "Me",
            senderRole = "R2",
            text = "Штурм",
            createdAt = Instant.now().toString(),
        )
        val server = optimistic.copy(_id = "server-100")
        buffer.upsert(optimistic)
        buffer.touchReceivedNow(optimistic)
        buffer.removeOptimisticEchoesForServerMessage(server)
        buffer.upsert(server)
        buffer.touchReceivedNow(server)
        assertEquals(1, buffer.visibleForPreview().size)
        assertEquals("server-100", buffer.visibleForPreview().first()._id)
    }

    @Test
    fun removeMessageWithKey_dropsBufferedRow() {
        val buffer = OverlayChatStripBuffer(messageTtlSeconds = 3600)
        val msg = ChatMessage(
            _id = "gone-id",
            allianceId = "a",
            roomId = "r",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "delete me",
            createdAt = Instant.now().toString(),
        )
        buffer.resetVisibleSession()
        buffer.upsert(msg)
        buffer.touchReceivedNow(msg)
        assertEquals(1, buffer.visibleForPreview().size)
        buffer.removeMessageWithKey("gone-id")
        assertTrue(buffer.visibleForPreview().isEmpty())
        org.junit.Assert.assertFalse(buffer.containsMessageId("gone-id"))
    }
}
