package com.lastasylum.alliance.overlay

import org.junit.Assert.assertNotEquals
import org.junit.Test

class OverlayReactionTilePreviewPoolTest {

    @Test
    fun cacheKey_differsPerLogEntryForSameReaction() {
        val a = OverlayReactionTilePreviewPool.cacheKey("log-a", "heart", playAnimatedPreview = false)
        val b = OverlayReactionTilePreviewPool.cacheKey("log-b", "heart", playAnimatedPreview = false)
        assertNotEquals(a, b)
    }

    @Test
    fun cacheKey_differsForAnimatedFlag() {
        val paused = OverlayReactionTilePreviewPool.cacheKey("log-a", "heart", playAnimatedPreview = false)
        val animated = OverlayReactionTilePreviewPool.cacheKey("log-a", "heart", playAnimatedPreview = true)
        assertNotEquals(paused, animated)
    }
}
