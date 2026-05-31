package com.lastasylum.alliance.overlay

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import kotlin.math.abs

object OverlayReactionPreviewAnimationPolicy {
    const val MAX_CONCURRENT_ANIMATED_PREVIEWS = 3

    /**
     * Picks up to [MAX_CONCURRENT_ANIMATED_PREVIEWS] entry ids whose list items are visible
     * and closest to the viewport vertical center.
     */
    fun resolveAnimatedEntryIds(
        visibleItems: List<LazyListItemInfo>,
        itemIndexToEntryId: Map<Int, String>,
        layoutInfo: LazyListLayoutInfo,
    ): Set<String> {
        if (visibleItems.isEmpty() || itemIndexToEntryId.isEmpty()) return emptySet()
        val viewportCenter =
            (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
        return visibleItems
            .mapNotNull { info ->
                val entryId = itemIndexToEntryId[info.index] ?: return@mapNotNull null
                val itemCenter = info.offset + info.size / 2
                entryId to abs(itemCenter - viewportCenter)
            }
            .sortedBy { it.second }
            .take(MAX_CONCURRENT_ANIMATED_PREVIEWS)
            .map { it.first }
            .toSet()
    }
}
