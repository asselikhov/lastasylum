package com.lastasylum.alliance.data.teams

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForumSocketIngressTest {

    @Before
    fun setUp() {
        ForumSocketIngress.clear()
    }

    @Test
    fun claimForTopicUi_firstWins() {
        assertTrue(ForumSocketIngress.claimForTopicUi("topic1", "msg1"))
        assertFalse(ForumSocketIngress.claimForTopicUi("topic1", "msg1"))
    }

    @Test
    fun claimForPersistence_independentFromTopicUi() {
        assertTrue(ForumSocketIngress.claimForTopicUi("topic1", "msg1"))
        assertTrue(ForumSocketIngress.claimForPersistence("topic1", "msg1"))
    }
}
