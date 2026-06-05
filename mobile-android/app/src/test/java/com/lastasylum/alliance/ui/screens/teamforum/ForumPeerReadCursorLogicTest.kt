package com.lastasylum.alliance.ui.screens.teamforum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForumPeerReadCursorLogicTest {
    @Test
    fun mergeTopicRead_ignoresSelf() {
        val map = mutableMapOf<String, String>()
        val publish = ForumPeerReadCursorLogic.mergeTopicReadEvent(
            otherReadUptoByTopic = map,
            topicId = "topicA",
            userId = "self",
            messageId = "000000000000000000000050",
            currentUserId = "self",
        )
        assertNull(publish)
        assertEquals(emptyMap<String, String>(), map)
    }

    @Test
    fun hydratePeerRead_advancesCursor() {
        val map = mutableMapOf<String, String>()
        val published = ForumPeerReadCursorLogic.hydratePeerRead(
            otherReadUptoByTopic = map,
            topicId = "topicA",
            peerUptoMessageId = "000000000000000000000060",
        )
        assertEquals("000000000000000000000060", published)
        assertEquals("000000000000000000000060", map["topicA"])
    }
}
