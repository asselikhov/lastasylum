package com.lastasylum.alliance.overlay

import androidx.compose.foundation.lazy.LazyListItemInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionPreviewAnimationPolicyTest {

    @Test
    fun resolveAnimatedEntryIds_animatesNewestThreeWhenVisible() {
        val visible = listOf(
            FakeItemInfo(index = 1, key = "cluster-a"),
            FakeItemInfo(index = 2, key = "cluster-b"),
            FakeItemInfo(index = 3, key = "cluster-c"),
            FakeItemInfo(index = 4, key = "cluster-d"),
        )
        val indexMap = mapOf(
            1 to "a",
            2 to "b",
            3 to "c",
            4 to "d",
        )
        val result = OverlayReactionPreviewAnimationPolicy.resolveAnimatedEntryIds(
            newestEntryIds = listOf("a", "b", "c"),
            visibleItems = visible,
            itemIndexToEntryId = indexMap,
        )
        assertEquals(3, result.size)
        assertTrue(result.containsAll(setOf("a", "b", "c")))
        assertTrue("d" !in result)
    }

    @Test
    fun resolveAnimatedEntryIds_skipsNewestNotOnScreen() {
        val visible = listOf(
            FakeItemInfo(index = 2, key = "cluster-b"),
        )
        val indexMap = mapOf(2 to "b")
        val result = OverlayReactionPreviewAnimationPolicy.resolveAnimatedEntryIds(
            newestEntryIds = listOf("a", "b", "c"),
            visibleItems = visible,
            itemIndexToEntryId = indexMap,
        )
        assertEquals(setOf("b"), result)
    }

    private class FakeItemInfo(
        override val index: Int,
        override val key: Any,
    ) : LazyListItemInfo {
        override val offset: Int = 0
        override val size: Int = 80
        override val contentType: Any? = null
    }
}
