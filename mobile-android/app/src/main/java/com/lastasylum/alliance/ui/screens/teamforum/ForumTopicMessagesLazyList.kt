package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.chat.ForumListDeriveDefer
import com.lastasylum.alliance.ui.chat.ForumListImagePrefetchEffect
import com.lastasylum.alliance.ui.chat.ForumMessagesListDerived
import com.lastasylum.alliance.ui.chat.ForumTimelineEntry
import com.lastasylum.alliance.ui.chat.LocalChatHighlightMessageId
import com.lastasylum.alliance.ui.chat.buildForumMessagesListDerived
import com.lastasylum.alliance.ui.chat.duplicateForumLazyKeysInTimeline
import com.lastasylum.alliance.ui.chat.duplicateForumMessageIdsIn
import com.lastasylum.alliance.ui.chat.forumLazyIndexToTimelineIndex
import com.lastasylum.alliance.ui.chat.forumTimelineMessageItemKey
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces

/**
 * Rebuilds forum timeline off the main thread; defers full rebuild while [listState] scrolls.
 */
@Composable
internal fun rememberForumMessagesListDerived(
    messages: List<TeamForumMessageDto>,
    messagesGeneration: Int,
    listState: LazyListState,
): ForumMessagesListDerived {
    val defer = remember { ForumListDeriveDefer() }
    var derived by remember { mutableStateOf(ForumMessagesListDerived.Empty) }
    val generationRef = rememberUpdatedState(messagesGeneration)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                val flush = defer.setScrollInProgress(scrolling)
                if (flush != null) {
                    val (gen, snapshot) = flush
                    if (gen == generationRef.value) {
                        derived = withContext(Dispatchers.Default) {
                            buildForumMessagesListDerived(snapshot)
                        }
                    }
                }
            }
    }

    LaunchedEffect(messagesGeneration) {
        val snapshot = messages.toList()
        if (defer.deferFullDerive(messagesGeneration, snapshot)) return@LaunchedEffect
        val built = withContext(Dispatchers.Default) {
            buildForumMessagesListDerived(snapshot)
        }
        derived = built
    }

    return derived
}

@Composable
internal fun ForumTopicMessagesLazyList(
    messages: List<TeamForumMessageDto>,
    listDerived: ForumMessagesListDerived,
    listState: LazyListState,
    hasMoreOlder: Boolean,
    loadingOlder: Boolean,
    highlightMessageId: String?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    messageContent: @Composable (message: TeamForumMessageDto, messageIndex: Int) -> Unit,
) {
    val timeline = listDerived.timeline
    val highlightId = highlightMessageId?.trim()?.takeIf { it.isNotEmpty() }
    val duplicateMessageIds = remember(messages) { duplicateForumMessageIdsIn(messages) }
    val duplicateLazyKeys = remember(timeline) { duplicateForumLazyKeysInTimeline(timeline) }

    CompositionLocalProvider(
        LocalChatHighlightMessageId provides highlightId,
    ) {
        if (timeline.isNotEmpty()) {
            ForumListImagePrefetchEffect(
                listState = listState,
                timeline = timeline,
                messages = messages,
                hasMoreOlder = hasMoreOlder,
            )
        }
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            reverseLayout = true,
            flingBehavior = ScrollableDefaults.flingBehavior(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(
                count = timeline.size,
                key = { lazyIdx ->
                    val timelineIdx =
                        forumLazyIndexToTimelineIndex(lazyIdx, timeline.lastIndex) ?: lazyIdx
                    when (val e = timeline[timelineIdx]) {
                        is ForumTimelineEntry.DaySeparator -> "day:$timelineIdx:${e.label}"
                        is ForumTimelineEntry.Message -> forumTimelineMessageItemKey(
                            timelineIndex = timelineIdx,
                            messageIndex = e.messageIndex,
                            messageId = e.messageId,
                            duplicateIds = duplicateMessageIds,
                            duplicateLazyKeys = duplicateLazyKeys,
                        )
                    }
                },
                contentType = { lazyIdx ->
                    val timelineIdx =
                        forumLazyIndexToTimelineIndex(lazyIdx, timeline.lastIndex) ?: lazyIdx
                    when (timeline[timelineIdx]) {
                        is ForumTimelineEntry.DaySeparator -> "day"
                        is ForumTimelineEntry.Message -> "message"
                    }
                },
            ) { lazyIdx ->
                val timelineIdx =
                    forumLazyIndexToTimelineIndex(lazyIdx, timeline.lastIndex) ?: return@items
                when (val entry = timeline[timelineIdx]) {
                    is ForumTimelineEntry.DaySeparator -> {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val sch = MaterialTheme.colorScheme
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SquadRelaySurfaces.subtleColor(0.48f),
                                tonalElevation = 0.dp,
                                shadowElevation = 4.dp,
                                border = BorderStroke(1.dp, sch.outline.copy(alpha = 0.18f)),
                            ) {
                                Text(
                                    text = entry.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                    is ForumTimelineEntry.Message -> {
                        val msg = messages.getOrNull(entry.messageIndex) ?: return@items
                        messageContent(msg, entry.messageIndex)
                    }
                }
            }
            if (hasMoreOlder && loadingOlder) {
                item(key = "forum_load_older", contentType = "load_older") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
