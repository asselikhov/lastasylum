package com.lastasylum.alliance.ui.components.team

import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicCardTokensTest {
    @Test
    fun activityLevel_hotWhenServerUnreadPositive() {
        assertEquals(
            ForumTopicCardTokens.ActivityLevel.Hot,
            ForumTopicCardTokens.activityLevel(unreadCount = 2, messageCount = 0),
        )
    }

    @Test
    fun activityLevel_warmWhenNoUnreadButMessages() {
        assertEquals(
            ForumTopicCardTokens.ActivityLevel.Warm,
            ForumTopicCardTokens.activityLevel(unreadCount = 0, messageCount = 5),
        )
    }

    @Test
    fun activityLevel_calmWhenEmpty() {
        assertEquals(
            ForumTopicCardTokens.ActivityLevel.Calm,
            ForumTopicCardTokens.activityLevel(unreadCount = 0, messageCount = 0),
        )
    }

    @Test
    fun layoutConstants_textBlockFitsContentHeight() {
        assertEquals(
            ForumTopicCardTokens.cardContentHeight,
            ForumTopicCardTokens.textBlockHeightSum(),
        )
    }
}
