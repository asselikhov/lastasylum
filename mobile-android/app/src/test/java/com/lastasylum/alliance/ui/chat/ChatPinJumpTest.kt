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
    fun jumpToChatPinnedMessage_loadsOlderUntilFound() = runBlocking {
        var loadCalls = 0
        val found = jumpToChatPinnedMessage(
            messageId = "msg-b",
            messageIdsNewestFirst = listOf("msg-a"),
            hasMoreOlder = { loadCalls < 1 },
            isLoadingOlder = { false },
            loadOlder = {
                loadCalls++
                true
            },
            timelineIndexForMessageId = { id ->
                if (id == "msg-b" && loadCalls > 0) 2 else -1
            },
            onJumpToMessage = {},
        )
        assertTrue(found)
        org.junit.Assert.assertEquals(1, loadCalls)
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
    fun isPinnedPreviewLikelyDeleted_notWhenMessageOutsideLoadedWindow() {
        val preview = PinnedMessagePreviewDto(
            id = "gone",
            text = "pin",
            senderUsername = "alice",
            createdAt = "2026-01-01T00:00:00Z",
        )
        assertFalse(isPinnedPreviewLikelyDeleted(preview, emptyList(), preview, preview.id))
    }

    private fun assertEquals(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
