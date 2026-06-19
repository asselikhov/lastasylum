package com.lastasylum.alliance.ui.screens.teamforum

import com.lastasylum.alliance.ui.chat.ForumTimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumVisibleMarkReadLogicTest {
    private val timeline = listOf(
        ForumTimelineEntry.DaySeparator("Today"),
        ForumTimelineEntry.Message(messageIndex = 0, messageId = "000000000000000000000010"),
        ForumTimelineEntry.Message(messageIndex = 1, messageId = "000000000000000000000020"),
        ForumTimelineEntry.Message(messageIndex = 2, messageId = "000000000000000000000030"),
    )

    @Test
    fun watermark_skipsSelfAndAlreadyRead() {
        val senders = mapOf(
            "000000000000000000000010" to "peer",
            "000000000000000000000020" to "self",
            "000000000000000000000030" to "peer",
        )
        val mark = ForumVisibleMarkReadLogic.readWatermarkFromVisibleLazyIndices(
            lazyIndices = listOf(0, 1),
            timeline = timeline,
            currentUserId = "self",
            lastReadCursor = "000000000000000000000010",
            senderUserIdForMessageId = { senders[it] },
        )
        assertEquals("000000000000000000000030", mark)
    }

    @Test
    fun watermark_nullWhenOnlySelfVisible() {
        val senders = mapOf(
            "000000000000000000000030" to "self",
        )
        val mark = ForumVisibleMarkReadLogic.readWatermarkFromVisibleLazyIndices(
            lazyIndices = listOf(0),
            timeline = timeline,
            currentUserId = "self",
            lastReadCursor = null,
            senderUserIdForMessageId = { senders[it] },
        )
        assertNull(mark)
    }
}
