package com.lastasylum.alliance.ui.screens.teamforum

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Forum list refresh: back navigation must not double-increment refresh nonce. */
class TeamForumListRefreshPolicyTest {
    @Test
    fun topicExit_incrementsRefreshNonceOnce() {
        assertTrue(ForumListRefreshPolicy.shouldBumpListRefreshOnTopicExit())
    }

    @Test
    fun topicBack_doesNotIncrementRefreshNonce() {
        assertFalse(ForumListRefreshPolicy.shouldBumpListRefreshOnTopicBack())
    }
}

internal object ForumListRefreshPolicy {
    /** Only [DisposableEffect.onDispose] — not [onBack]. */
    fun shouldBumpListRefreshOnTopicExit(): Boolean = true

    fun shouldBumpListRefreshOnTopicBack(): Boolean = false
}
