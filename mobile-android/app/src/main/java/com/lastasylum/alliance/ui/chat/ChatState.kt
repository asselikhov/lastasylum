package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto

data class ChatState(
    val isLoading: Boolean = false,
    val isRoomsLoading: Boolean = true,
    val rooms: List<ChatRoomDto> = emptyList(),
    val selectedRoomId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val error: String? = null,
    val currentUserId: String = "",
    val currentUserRole: String = "",
    val isLoadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = true,
    val isSending: Boolean = false,
    val draftMessage: String = "",
    val replyToMessage: ChatMessage? = null,
    val activeActionMessageId: String? = null,
    val confirmDeleteMessageId: String? = null,
    val deletingMessageId: String? = null,
    val newestMessageKey: String? = null,
    /** Increments after a successful own send so the list scrolls to the latest message. */
    val scrollToLatestNonce: Long = 0L,
)
