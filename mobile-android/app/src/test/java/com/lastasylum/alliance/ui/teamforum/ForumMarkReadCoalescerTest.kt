package com.lastasylum.alliance.ui.teamforum

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForumMarkReadCoalescerTest {
    @Test
    fun networkFailure_retainsPendingForRetry() = runTest {
        val coalescer = ForumMarkReadCoalescer(this)
        var cursor: String? = null
        var attempts = 0

        coalescer.schedule(
            topicId = "topic-1",
            messageId = "507f1f77bcf86cd799439011",
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, mid -> cursor = mid },
            onNetworkMarkRead = { _, _ ->
                attempts++
                false
            },
        )

        advanceTimeBy(FORUM_MARK_READ_DEBOUNCE_MS + 100)
        assertEquals(1, attempts)
        assertTrue(coalescer.hasPending("topic-1"))
    }

    @Test
    fun networkSuccess_clearsPending() = runTest {
        val coalescer = ForumMarkReadCoalescer(this)
        var cursor: String? = null

        coalescer.schedule(
            topicId = "topic-1",
            messageId = "507f1f77bcf86cd799439011",
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, mid -> cursor = mid },
            onNetworkMarkRead = { _, _ -> true },
        )

        advanceTimeBy(FORUM_MARK_READ_DEBOUNCE_MS + 100)
        assertTrue(!coalescer.hasPending("topic-1"))
    }
}
