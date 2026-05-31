package com.lastasylum.alliance.overlay

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionPreviewAnimationPolicyTest {

    @Test
    fun resolveAnimatedEntryIds_capsAtThreeClosestToViewportCenter() {
        val layoutInfo = FakeLayoutInfo(viewportStart = 0, viewportEnd = 1000)
        val visible = listOf(
            FakeItemInfo(index = 1, offset = 100, size = 80),
            FakeItemInfo(index = 2, offset = 450, size = 80),
            FakeItemInfo(index = 3, offset = 480, size = 80),
            FakeItemInfo(index = 4, offset = 900, size = 80),
        )
        val indexMap = mapOf(
            1 to "a",
            2 to "b",
            3 to "c",
            4 to "d",
        )
        val result = OverlayReactionPreviewAnimationPolicy.resolveAnimatedEntryIds(
            visibleItems = visible,
            itemIndexToEntryId = indexMap,
            layoutInfo = layoutInfo,
        )
        assertEquals(3, result.size)
        assertTrue("b" in result)
        assertTrue("c" in result)
    }

    private class FakeLayoutInfo(
        private val viewportStart: Int,
        private val viewportEnd: Int,
    ) : LazyListLayoutInfo {
        override val visibleItemsInfo: List<LazyListItemInfo> = emptyList()
        override val viewportStartOffset: Int = viewportStart
        override val viewportEndOffset: Int = viewportEnd
        override val totalItemsCount: Int = 0
        override val beforeContentPadding: Int = 0
        override val afterContentPadding: Int = 0
        override val mainAxisItemSpacing: Int = 0
        override val reverseLayout: Boolean = false
        override val orientation: androidx.compose.foundation.gestures.Orientation =
            androidx.compose.foundation.gestures.Orientation.Vertical
    }

    private class FakeItemInfo(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo {
        override val key: Any = index
        override val contentType: Any? = null
    }
}
