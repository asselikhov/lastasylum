package com.lastasylum.alliance.ui.screens.teamforum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.chat.ForumMessageClusterFlags
import com.lastasylum.alliance.ui.chat.MessageActionOpenRequest
import com.lastasylum.alliance.ui.chat.toChatClusterFlags
import com.lastasylum.alliance.ui.chat.toDisplayChatMessage
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
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
    showPinnedMarker: Boolean = false,
    onJumpToMessage: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onBeginSelection: (String) -> Unit,
    onSwipeReply: () -> Unit,
    onOpenActions: (MessageActionOpenRequest) -> Unit,
    onToggleReaction: ((String, String) -> Unit)? = null,
    otherReadUptoMessageId: String? = null,
    downloadingForumFileUrl: String? = null,
    onDownloadForumFile: (TeamForumMessageDto) -> Unit = {},
) {
    val overlayUi = LocalOverlayUiMode.current
    val chatMessage = remember(message) { message.toDisplayChatMessage(teamId, topicId) }
    ChatMessageBubble(
        message = chatMessage,
        cluster = cluster?.toChatClusterFlags(),
        isMine = isMine,
        highlighted = highlighted,
        showPinnedMarker = showPinnedMarker,
        clusterTopSpacing = cluster?.topSpacing ?: 8.dp,
        canDelete = canDelete,
        deleting = false,
        inSelectionMode = inSelectionMode,
        isSelected = isSelected,
        overlayUi = overlayUi,
        otherReadUptoMessageId = otherReadUptoMessageId,
        inMessageList = true,
        onToggleReaction = onToggleReaction,
        onOpenActions = onOpenActions,
        onBeginSelection = onBeginSelection,
        onToggleSelection = onToggleSelection,
        onSwipeReply = { onSwipeReply() },
        onJumpToQuotedMessage = onJumpToMessage,
        onFileDownload = { onDownloadForumFile(message) },
        downloadingFileUrl = downloadingForumFileUrl,
    )
}
