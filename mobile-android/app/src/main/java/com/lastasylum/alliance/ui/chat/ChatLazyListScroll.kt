package com.lastasylum.alliance.ui.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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

internal suspend fun LazyListState.scrollReverseChatToLatest(animate: Boolean) {
    scrollReverseChatRevealLatest(animate = animate, adjustViewport = !animate)
}

/**
 * Snap to newest row. [adjustViewport] = false for instant send (Telegram-style, no wait on layout).
 */
internal suspend fun LazyListState.scrollReverseChatRevealLatest(
    newestIndex: Int = 0,
    animate: Boolean = false,
    edgeInsetPx: Int = 12,
    adjustViewport: Boolean = true,
) {
    if (layoutInfo.totalItemsCount == 0) return
    if (animate) {
        runCatching { animateScrollToItem(newestIndex, 0) }
            .onFailure { scrollToItem(newestIndex, 0) }
    } else {
        runCatching { scrollToItem(newestIndex, 0) }
    }
    if (!adjustViewport) return
    snapshotFlow {
        layoutInfo.visibleItemsInfo.firstOrNull { it.index == newestIndex } to layoutInfo.viewportSize.height
    }
        .filter { it.first != null && it.second > 0 }
        .first()
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == newestIndex } ?: return
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1)
    val itemStart = item.offset
    val itemEnd = item.offset + item.size
    val bottomInset = edgeInsetPx.coerceAtLeast(0)
    val topInset = edgeInsetPx.coerceAtLeast(0)
    if (itemEnd > viewportEnd - bottomInset) {
        scrollBy((itemEnd - viewportEnd + bottomInset).toFloat())
    }
    if (item.size <= viewportHeight - topInset - bottomInset &&
        itemStart < viewportStart + topInset
    ) {
        scrollBy((itemStart - viewportStart - topInset).toFloat())
    }
}

/** Keep bubble top fixed when long text expands in a reverse-layout chat list. */
internal suspend fun LazyListState.scrollReverseChatCompensateExpand(heightDeltaPx: Int) {
    if (heightDeltaPx <= 0) return
    scrollBy(heightDeltaPx.toFloat())
}

private const val SCROLL_TO_ITEM_VISIBLE_TIMEOUT_MS = 3_000L

internal suspend fun LazyListState.scrollTimelineItemToViewportCenter(index: Int) {
    if (index < 0) return
    scrollToItem(index)
    withTimeoutOrNull(SCROLL_TO_ITEM_VISIBLE_TIMEOUT_MS) {
        snapshotFlow { layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
            .filter { it != null }
            .first()
    } ?: return
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
