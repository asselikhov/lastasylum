package com.lastasylum.alliance.data.chat.sync

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto

data class ChatRoomMessageCache(
    val messages: List<ChatMessage>,
    val hasMoreOlder: Boolean,
)

data class LaunchDiskPrimePayload(
    val roomsRaw: List<ChatRoomDto>,
    val selectedRoomId: String,
    val roomCaches: Map<String, ChatRoomMessageCache>,
)
