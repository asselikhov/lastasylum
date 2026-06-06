package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagesListDeriverTest {

    private fun msg(
        id: String,
        senderId: String = "u1",
        text: String = "hello",
        createdAt: String = "2026-05-24T12:00:00.000Z",
    ) = ChatMessage(
        _id = id,
        allianceId = "a",
        roomId = "r",
        senderId = senderId,
        senderUsername = "user",
        senderRole = "R1",
        text = text,
        createdAt = createdAt,
    )

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_matchesFullRebuild() {
        val previous = listOf(
            msg("2", senderId = "u1", text = "b"),
            msg("1", senderId = "u2", text = "a"),
        )
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3", senderId = "u1", text = "c")) + previous
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full.timeline.size, incremental.timeline.size)
        assertEquals(full.clusterFlags, incremental.clusterFlags)
        assertEquals(full.clusterTopSpacingDp, incremental.clusterTopSpacingDp)
    }

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_sameSenderCluster() {
        val previous = listOf(
            msg("2", senderId = "u1"),
            msg("1", senderId = "u1"),
        )
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3", senderId = "u1")) + previous
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full, incremental)
    }

    @Test
    fun buildChatTimeline_duplicateDayLabels_haveUniqueLazyKeys() {
        val messages = listOf(
            msg("4", createdAt = "2026-06-08T10:00:00.000Z"),
            msg("3", createdAt = "2026-06-07T10:00:00.000Z"),
            msg("2", createdAt = "2026-06-06T10:00:00.000Z"),
            msg("1", createdAt = "2026-06-07T09:00:00.000Z"),
        )
        val derived = buildChatMessagesListDerived(messages)
        val dayLabels = derived.timeline.filterIsInstance<ChatTimelineEntry.DaySeparator>()
        assertTrue(dayLabels.size >= 2)
        val keys = derived.timeline.mapIndexed { index, entry ->
            when (entry) {
                is ChatTimelineEntry.DaySeparator -> chatTimelineDaySeparatorKey(index, entry.label)
                else -> entry.toString()
            }
        }
        val dayKeys = keys.filter { it.startsWith("day:") }
        assertEquals(dayKeys.size, dayKeys.toSet().size)
    }

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_fallsBackWhenNotSimplePrepend() {
        val previous = listOf(msg("1"), msg("2"))
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3"), msg("1"), msg("2"))
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full, incremental)
        assertTrue(incremental.timeline.isNotEmpty())
    }

    @Test
    fun duplicateMessageIdsIn_detectsRepeatedServerIds() {
        val id = "6a24a23e4a984f6da85136db"
        val messages = listOf(msg(id), msg(id, text = "dup"))
        assertEquals(setOf(id), duplicateMessageIdsIn(messages))
    }

    @Test
    fun chatTimelineMessageItemKey_usesTimelineIndexWhenDuplicateIds() {
        val id = "6a24a23e4a984f6da85136db"
        val message = msg(id)
        val key0 = chatTimelineMessageItemKey(
            timelineIndex = 0,
            messageIndex = 0,
            message = message,
            messageListKey = { it._id!! },
            duplicateIds = setOf(id),
        )
        val key1 = chatTimelineMessageItemKey(
            timelineIndex = 1,
            messageIndex = 1,
            message = message,
            messageListKey = { it._id!! },
            duplicateIds = setOf(id),
        )
        assertEquals("t:0:$id", key0)
        assertEquals("t:1:$id", key1)
    }

    @Test
    fun reconcileDerivedWithMessages_rebuildsWhenTimelineHasExtraRow() {
        val messages = listOf(msg("server-1", text = "hello"))
        val staleDerived = buildChatMessagesListDerived(
            listOf(msg("server-1", text = "hello"), msg("server-1", text = "hello")),
        )
        val reconciled = reconcileDerivedWithMessages(staleDerived, messages)
        assertEquals(1, reconciled.timeline.count { it is ChatTimelineEntry.ChatMessageItem })
        assertTrue(derivedMatchesMessages(reconciled, messages))
    }
}
