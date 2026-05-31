package com.lastasylum.alliance.ui.chat

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import coil3.imageLoader
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val FORUM_PREFETCH_RADIUS = 3

@Composable
internal fun ForumListImagePrefetchEffect(
    listState: LazyListState,
    timeline: List<ForumTimelineEntry>,
    messages: List<TeamForumMessageDto>,
    hasMoreOlder: Boolean,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timelineRef = remember(timeline, messages, hasMoreOlder) {
        Triple(timeline, messages, hasMoreOlder)
    }
    LaunchedEffect(listState, timelineRef) {
        val (tl, msgs, more) = timelineRef
        if (tl.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.map { it.index }.minOrNull() to
                info.visibleItemsInfo.map { it.index }.maxOrNull()
        }
            .distinctUntilChanged()
            .map { (minIdx, maxIdx) ->
                if (minIdx == null || maxIdx == null) emptyList()
                else {
                    val maxLazy = forumLazyListMaxIndex(tl.size, more)
                    val from = (minIdx - FORUM_PREFETCH_RADIUS).coerceAtLeast(0)
                    val to = (maxIdx + FORUM_PREFETCH_RADIUS).coerceAtMost(maxLazy)
                    forumImageUrlsForLazyIndices(tl, msgs, from..to)
                }
            }
            .distinctUntilChanged()
            .collect { urls -> prefetchChatListThumbnails(context, urls) }
    }
}

internal fun forumLazyListMaxIndex(timelineSize: Int, hasMoreOlder: Boolean): Int =
    timelineSize - 1 + if (hasMoreOlder) 1 else 0

internal fun forumLazyIndexToTimelineIndex(lazyIdx: Int, timelineLastIndex: Int): Int? {
    if (lazyIdx !in 0..timelineLastIndex) return null
    return timelineLastIndex - lazyIdx
}

internal fun forumImageUrlsForLazyIndices(
    timeline: List<ForumTimelineEntry>,
    messages: List<TeamForumMessageDto>,
    lazyIndices: IntRange,
): List<String> {
    val urls = LinkedHashSet<String>()
    val last = timeline.lastIndex
    for (lazyIdx in lazyIndices) {
        val timelineIdx = forumLazyIndexToTimelineIndex(lazyIdx, last) ?: continue
        when (val entry = timeline[timelineIdx]) {
            is ForumTimelineEntry.Message -> {
                val msg = messages.getOrNull(entry.messageIndex) ?: continue
                msg.imageRelativeUrl?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    urls.add(resolvedChatAttachmentImageUrl(it))
                }
                msg.imageRelativeUrls.forEach { raw ->
                    val t = raw.trim()
                    if (t.isNotEmpty()) urls.add(resolvedChatAttachmentImageUrl(t))
                }
            }
            is ForumTimelineEntry.DaySeparator -> Unit
        }
    }
    return urls.toList()
}
