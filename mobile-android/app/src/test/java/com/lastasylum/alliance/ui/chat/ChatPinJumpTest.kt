package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPinJumpTest {
    @Test
    fun jumpToChatPinnedMessage_findsMessageAlreadyInTimeline() = runBlocking {
        var jumpedTo: String? = null
        val found = jumpToChatPinnedMessage(
            messageId = "msg-b",
            messageIdsNewestFirst = listOf("msg-c", "msg-b", "msg-a"),
            hasMoreOlder = { false },
            isLoadingOlder = { false },
            loadOlder = { false },
            timelineIndexForMessageId = { id ->
                if (id == "msg-b") 3 else -1
            },
            onJumpToMessage = { jumpedTo = it },
        )
        assertTrue(found)
        assertEquals("msg-b", jumpedTo)
    }

    @Test
    fun jumpToChatPinnedMessage_notFoundWhenNoOlderPages() = runBlocking {
        val found = jumpToChatPinnedMessage(
            messageId = "missing",
            messageIdsNewestFirst = listOf("msg-a"),
            hasMoreOlder = { false },
            isLoadingOlder = { false },
            loadOlder = { false },
            timelineIndexForMessageId = { -1 },
            onJumpToMessage = {},
        )
        assertFalse(found)
    }

    @Test
    fun isPinnedPreviewLikelyDeleted_whenMessageRemovedFromFeed() {
        val preview = PinnedMessagePreviewDto(
            id = "gone",
            text = "pin",
            senderUsername = "alice",
            createdAt = "2026-01-01T00:00:00Z",
        )
        assertTrue(isPinnedPreviewLikelyDeleted(preview, emptyList()))
    }

    private fun assertEquals(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
