package com.lastasylum.alliance.data.teams

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamForumSocketTopicFilterTest {
    @Test
    fun acceptsTopicEvent_falseWhenInboxOnlyNoSubscribedTopic() {
        assertFalse(TeamForumSocketTopicFilter.acceptsTopicEvent(null, "topic-1"))
        assertFalse(TeamForumSocketTopicFilter.acceptsTopicEvent("", "topic-1"))
    }

    @Test
    fun acceptsTopicEvent_trueAfterJoinTopic() {
        assertTrue(TeamForumSocketTopicFilter.acceptsTopicEvent("topic-1", "topic-1"))
        assertFalse(TeamForumSocketTopicFilter.acceptsTopicEvent("topic-1", "topic-2"))
    }

    @Test
    fun inboxThenJoinTopic_simulatesLiveDeliveryPath() {
        var subscribedTopicId: String? = null
        assertFalse(TeamForumSocketTopicFilter.acceptsTopicEvent(subscribedTopicId, "topic-raid"))
        subscribedTopicId = "topic-raid"
        assertTrue(TeamForumSocketTopicFilter.acceptsTopicEvent(subscribedTopicId, "topic-raid"))
    }
}
