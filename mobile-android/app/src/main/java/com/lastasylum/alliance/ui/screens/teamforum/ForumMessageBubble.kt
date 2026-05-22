package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.chat.ForumMessageClusterFlags
import com.lastasylum.alliance.ui.chat.toChatClusterFlags
import com.lastasylum.alliance.ui.chat.toDisplayChatMessage
import com.lastasylum.alliance.ui.screens.ChatMessageBubble

/**
 * Сообщение темы форума — тот же UI, что в чат-комнатах ([ChatMessageBubble]).
 */
@Composable
internal fun ForumMessageBubble(
    message: TeamForumMessageDto,
    teamId: String,
    topicId: String,
    cluster: ForumMessageClusterFlags?,
    isMine: Boolean,
    canDelete: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    highlighted: Boolean,
    onJumpToMessage: (String) -> Unit,
    onToggleSelection: () -> Unit,
    onSwipeReply: () -> Unit,
    onOpenActions: () -> Unit,
    downloadingForumFileUrl: String? = null,
    onDownloadForumFile: (TeamForumMessageDto) -> Unit = {},
) {
    val chatMessage = remember(message) { message.toDisplayChatMessage(teamId, topicId) }
    ChatMessageBubble(
        message = chatMessage,
        cluster = cluster?.toChatClusterFlags(),
        isMine = isMine,
        highlighted = highlighted,
        clusterTopSpacing = cluster?.topSpacing ?: 8.dp,
        canDelete = canDelete,
        deleting = false,
        inSelectionMode = inSelectionMode,
        isSelected = isSelected,
        overlayUi = false,
        otherReadUptoMessageId = null,
        onToggleReaction = null,
        onOpenActions = {
            if (inSelectionMode && canDelete) {
                onToggleSelection()
            } else {
                onOpenActions()
            }
        },
        onToggleSelection = { onToggleSelection() },
        onSwipeReply = { onSwipeReply() },
        onJumpToQuotedMessage = onJumpToMessage,
        onFileDownload = { onDownloadForumFile(message) },
        downloadingFileUrl = downloadingForumFileUrl,
    )
}
