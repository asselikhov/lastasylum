package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayInboxBadgeRefreshPipelineTest {
    @Test
    fun queue_mergesFlagsAcrossRequests() {
        val pipeline = OverlayInboxBadgeRefreshPipeline()
        pipeline.queue(
            includeHub = true,
            includeNews = false,
            includeForum = false,
            forceHubReconcile = true,
            forceNewsReconcile = false,
            forceForumReconcile = false,
            includeReactionLog = false,
        )
        pipeline.queue(
            includeHub = false,
            includeNews = true,
            includeForum = true,
            forceHubReconcile = false,
            forceNewsReconcile = true,
            forceForumReconcile = true,
            includeReactionLog = true,
        )
        val pending = pipeline.takePending()
        assertNotNull(pending)
        assertTrue(pending!!.includeHub)
        assertTrue(pending.includeNews)
        assertTrue(pending.includeForum)
        assertTrue(pending.forceHubReconcile)
        assertTrue(pending.forceNewsReconcile)
        assertTrue(pending.forceForumReconcile)
        assertTrue(pending.includeReactionLog)
        assertFalse(pipeline.hasPending())
        assertNull(pipeline.takePending())
    }
}
