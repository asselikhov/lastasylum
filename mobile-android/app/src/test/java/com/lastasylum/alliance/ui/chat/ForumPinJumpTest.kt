package com.lastasylum.alliance.ui.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumPinJumpTest {
    @Test
    fun jumpToForumPinnedMessage_findsMessageAlreadyInTimeline() = runBlocking {
        var scrolledTo: Int? = null
        var highlighted: String? = null
        val found = jumpToForumPinnedMessage(
            messageId = "msg-b",
            messageIdsOldestFirst = { listOf("msg-a", "msg-b") },
            hasMoreOlder = { false },
            isLoadingOlder = { false },
            loadOlder = { false },
            timelineIndexForMessageId = { id ->
                if (id == "msg-b") 3 else -1
            },
            scrollToTimelineIndex = { idx -> scrolledTo = idx },
            onHighlight = { highlighted = it },
            highlightMs = 0L,
        )
        assertTrue(found)
        assertEquals(3, scrolledTo)
        assertEquals("msg-b", highlighted)
    }

    @Test
    fun jumpToForumPinnedMessage_notFoundWhenNoOlderPages() = runBlocking {
        val found = jumpToForumPinnedMessage(
            messageId = "missing",
            messageIdsOldestFirst = { listOf("msg-a") },
            hasMoreOlder = { false },
            isLoadingOlder = { false },
            loadOlder = { false },
            timelineIndexForMessageId = { -1 },
            scrollToTimelineIndex = {},
            onHighlight = {},
            highlightMs = 0L,
        )
        assertFalse(found)
    }

    private fun assertEquals(expected: Int, actual: Int?) {
        org.junit.Assert.assertEquals(expected, actual)
    }

    private fun assertEquals(expected: String, actual: String?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
