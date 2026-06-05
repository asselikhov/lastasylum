package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.Immutable
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto

@Immutable
data class ChatListPaneState(
    val selectedRoomId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val newestMessageKey: String? = null,
    val scrollToLatestNonce: Long = 0L,
    val scrollToMessageId: String? = null,
    val highlightMessageId: String? = null,
    val selectedMessageIds: Set<String> = emptySet(),
    val deletingMessageId: String? = null,
)

@Immutable
data class ChatChromePaneState(
    val rooms: List<ChatRoomDto> = emptyList(),
    val isRoomsLoading: Boolean = true,
    val selectedRoomId: String? = null,
    val hasTeamProfileForGlobalChat: Boolean = false,
    val currentUserId: String = "",
    val isAppAdmin: Boolean = false,
    val playerTeamSquadRole: String? = null,
    val error: String? = null,
    val transientNotice: String? = null,
    val activeActionMessageId: String? = null,
    val confirmDeleteMessageId: String? = null,
    val confirmBulkDelete: Boolean = false,
    val isDeletingSelection: Boolean = false,
    val pinInFlight: Boolean = false,
    val pinBarPreview: PinnedMessagePreviewDto? = null,
    val pinHistoryCount: Int = 0,
    val pinnedMessages: List<PinnedMessagePreviewDto> = emptyList(),
)

@Immutable
data class ChatComposerPaneState(
    val replyToMessage: ChatMessage? = null,
    val editingMessage: ChatMessage? = null,
    val sendFailure: ChatSendFailure? = null,
    val isSending: Boolean = false,
    val enabledStickerPackKeys: Set<String> = emptySet(),
)

fun ChatState.toListPane(): ChatListPaneState =
    ChatListPaneState(
        selectedRoomId = selectedRoomId,
        messages = messages,
        isLoading = isLoading,
        isLoadingOlder = isLoadingOlder,
        hasMoreOlder = hasMoreOlder,
        newestMessageKey = newestMessageKey,
        scrollToLatestNonce = scrollToLatestNonce,
        scrollToMessageId = scrollToMessageId,
        highlightMessageId = highlightMessageId,
        selectedMessageIds = selectedMessageIds,
        deletingMessageId = deletingMessageId,
    )

fun ChatState.toChromePane(): ChatChromePaneState =
    ChatChromePaneState(
        rooms = rooms,
        isRoomsLoading = isRoomsLoading,
        selectedRoomId = selectedRoomId,
        hasTeamProfileForGlobalChat = hasTeamProfileForGlobalChat,
        currentUserId = currentUserId,
        isAppAdmin = isAppAdmin,
        playerTeamSquadRole = playerTeamSquadRole,
        error = error,
        transientNotice = transientNotice,
        activeActionMessageId = activeActionMessageId,
        confirmDeleteMessageId = confirmDeleteMessageId,
        confirmBulkDelete = confirmBulkDelete,
        isDeletingSelection = isDeletingSelection,
        pinInFlight = pinInFlight,
        pinBarPreview = pinBarPreview,
        pinHistoryCount = pinHistoryCount,
        pinnedMessages = pinnedMessages,
    )

fun ChatState.toComposerPane(): ChatComposerPaneState =
    ChatComposerPaneState(
        replyToMessage = replyToMessage,
        editingMessage = editingMessage,
        sendFailure = sendFailure,
        isSending = isSending,
        enabledStickerPackKeys = enabledStickerPackKeys,
    )
