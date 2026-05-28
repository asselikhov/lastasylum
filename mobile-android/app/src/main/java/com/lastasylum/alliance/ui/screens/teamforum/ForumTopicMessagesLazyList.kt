package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.chat.ForumMessagesListDerived
import com.lastasylum.alliance.ui.chat.ForumTimelineEntry
import com.lastasylum.alliance.ui.chat.buildForumMessagesListDerived
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberForumMessagesListDerived(
    messages: List<TeamForumMessageDto>,
): ForumMessagesListDerived {
    var derived by remember(messages) { mutableStateOf(ForumMessagesListDerived.Empty) }
    LaunchedEffect(messages) {
        val built = withContext(Dispatchers.Default) {
            buildForumMessagesListDerived(messages)
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
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    messageContent: @Composable (message: TeamForumMessageDto, messageIndex: Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (hasMoreOlder) {
            item(key = "forum_load_older", contentType = "load_older") {
                OutlinedButton(
                    onClick = onLoadOlder,
                    enabled = !loadingOlder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        if (loadingOlder) {
                            stringResource(R.string.team_forum_loading_older)
                        } else {
                            stringResource(R.string.team_forum_load_older)
                        },
                    )
                }
            }
        }
        items(
            count = listDerived.timeline.size,
            key = { idx ->
                when (val e = listDerived.timeline[idx]) {
                    is ForumTimelineEntry.DaySeparator -> "day:${e.label}"
                    is ForumTimelineEntry.Message -> "msg:${e.messageId}"
                }
            },
            contentType = { idx ->
                when (listDerived.timeline[idx]) {
                    is ForumTimelineEntry.DaySeparator -> "day"
                    is ForumTimelineEntry.Message -> "message"
                }
            },
        ) { idx ->
            when (val entry = listDerived.timeline[idx]) {
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
    }
}
