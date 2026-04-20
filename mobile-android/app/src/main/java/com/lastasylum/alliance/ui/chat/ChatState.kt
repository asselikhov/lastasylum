package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto

enum class ChatVoicePhase {
    Idle,
    Listening,
    Sending,
}

data class ChatSendFailure(
    val messageText: String,
    val replyToMessageId: String?,
    val errorMessage: String,
)

data class ChatState(
    val isLoading: Boolean = false,
    val isRoomsLoading: Boolean = true,
    /** Full team name + tag set in profile — required to post in global «Общая». */
    val hasTeamProfileForGlobalChat: Boolean = false,
    val rooms: List<ChatRoomDto> = emptyList(),
    val selectedRoomId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val currentUserId: String = "",
    val currentUserRole: String = "",
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val isSending: Boolean = false,
    val replyToMessage: ChatMessage? = null,
    val activeActionMessageId: String? = null,
    val confirmDeleteMessageId: String? = null,
    /** Multi-select for bulk delete (long-press on a deletable message, then tap more). */
    val selectedMessageIds: Set<String> = emptySet(),
    val confirmBulkDelete: Boolean = false,
    val isDeletingSelection: Boolean = false,
    val deletingMessageId: String? = null,
    val newestMessageKey: String? = null,
    /** Increments after a successful own send so the list scrolls to the latest message. */
    val scrollToLatestNonce: Long = 0L,
    val sendFailure: ChatSendFailure? = null,
    val chatVoicePhase: ChatVoicePhase = ChatVoicePhase.Idle,
)
