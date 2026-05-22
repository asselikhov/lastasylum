package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * Helpers for the main/overlay chat [androidx.compose.foundation.lazy.LazyColumn] with
 * [androidx.compose.foundation.lazy.LazyColumn.reverseLayout] = true and newest messages at index 0.
 */
internal fun LazyListState.isAtReverseChatBottom(
    newestIndex: Int = 0,
    scrollThresholdPx: Int = 96,
): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return true
    if (visible.none { it.index == newestIndex }) return false
    val minVisibleIndex = visible.minOf { it.index }
    if (minVisibleIndex > newestIndex + 1) return false
    if (firstVisibleItemIndex > newestIndex + 1) return false
    if (firstVisibleItemIndex > newestIndex) return false
    return firstVisibleItemIndex == newestIndex &&
        firstVisibleItemScrollOffset <= scrollThresholdPx
}

internal suspend fun LazyListState.scrollTimelineItemToViewportCenter(index: Int) {
    if (index < 0) return
    scrollToItem(index)
    snapshotFlow { layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
        .filter { it != null }
        .first()
    val item = layoutInfo.visibleItemsInfo.first { it.index == index }
    val viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) return
    val itemCenter = item.offset + item.size / 2f
    val viewportCenter = viewportHeight / 2f
    val delta = itemCenter - viewportCenter
    if (kotlin.math.abs(delta) > 1f) {
        scrollBy(delta)
    }
}
