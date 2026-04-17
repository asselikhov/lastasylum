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
)
