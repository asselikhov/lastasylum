package com.lastasylum.alliance.ui.chat

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import coil.imageLoader
import com.lastasylum.alliance.data.chat.chatImageAttachments
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val PREFETCH_RADIUS = 3

/** Collect image URLs from timeline entries for list thumbnail prefetch. */
internal fun imageUrlsForTimelineIndices(
    timeline: List<ChatTimelineEntry>,
    indices: Iterable<Int>,
): List<String> {
    val urls = LinkedHashSet<String>()
    for (idx in indices) {
        if (idx !in timeline.indices) continue
        when (val entry = timeline[idx]) {
            is ChatTimelineEntry.ChatAlbumItem -> urls.addAll(entry.resolvedImageUrls)
            is ChatTimelineEntry.ChatMessageItem -> {
                entry.message.chatImageAttachments().forEach { att ->
                    val raw = att.url.trim()
                    if (raw.isNotEmpty()) {
                        urls.add(resolvedChatAttachmentImageUrl(raw))
                    }
                }
            }
            is ChatTimelineEntry.DaySeparator -> Unit
        }
    }
    return urls.toList()
}

@Composable
internal fun ChatListImagePrefetchEffect(
    listState: LazyListState,
    timeline: List<ChatTimelineEntry>,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timelineRef = remember(timeline) { timeline }
    LaunchedEffect(listState, timelineRef) {
        if (timelineRef.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.map { it.index }.minOrNull() to
                info.visibleItemsInfo.map { it.index }.maxOrNull()
        }
            .distinctUntilChanged()
            .map { (minIdx, maxIdx) ->
                if (minIdx == null || maxIdx == null) emptyList()
                else {
                    val from = (minIdx - PREFETCH_RADIUS).coerceAtLeast(0)
                    val to = (maxIdx + PREFETCH_RADIUS).coerceAtMost(timelineRef.lastIndex)
                    imageUrlsForTimelineIndices(timelineRef, from..to)
                }
            }
            .distinctUntilChanged()
            .collect { urls ->
                prefetchChatListThumbnails(context, urls)
            }
    }
}

internal fun prefetchChatListThumbnails(context: Context, urls: List<String>) {
    if (urls.isEmpty()) return
    val loader = context.imageLoader
    val appContext = context.applicationContext
    for (url in urls) {
        val request = SquadRelayImageRequests.chatThumbnailInList(appContext, url)
        loader.enqueue(request)
    }
}
