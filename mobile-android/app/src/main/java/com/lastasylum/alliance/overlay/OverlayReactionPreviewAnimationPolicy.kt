package com.lastasylum.alliance.overlay

import androidx.compose.foundation.lazy.LazyListItemInfo

object OverlayReactionPreviewAnimationPolicy {
    const val MAX_CONCURRENT_ANIMATED_PREVIEWS = 5

    /**
     * Animates up to [MAX_CONCURRENT_ANIMATED_PREVIEWS] newest feed cards that are currently visible.
     */
    fun resolveAnimatedEntryIds(
        newestEntryIds: List<String>,
        visibleItems: List<LazyListItemInfo>,
        itemIndexToEntryId: Map<Int, String>,
        supportsAnimatedPreview: (String) -> Boolean = { true },
    ): Set<String> {
        if (newestEntryIds.isEmpty() || visibleItems.isEmpty() || itemIndexToEntryId.isEmpty()) {
            return emptySet()
        }
        val candidates = newestEntryIds
            .filter(supportsAnimatedPreview)
            .take(MAX_CONCURRENT_ANIMATED_PREVIEWS)
            .toSet()
        return visibleItems
            .mapNotNull { info -> itemIndexToEntryId[info.index] }
            .filter { it in candidates }
            .toSet()
    }
}
