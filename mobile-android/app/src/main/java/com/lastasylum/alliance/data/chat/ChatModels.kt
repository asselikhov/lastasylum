package com.lastasylum.alliance.data.chat

import com.squareup.moshi.Json

data class ChatRoomDto(
    @Json(name = "_id") val id: String,
    val allianceId: String? = null,
    val title: String,
    val sortOrder: Int = 0,
    val archivedAt: String? = null,
)

data class ChatMessage(
    val _id: String? = null,
    val allianceId: String,
    val roomId: String = "",
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val text: String,
    val createdAt: String? = null,
)

data class SendMessageRequest(
    val text: String,
    val roomId: String,
    val allianceId: String? = AllianceDefaults.DEFAULT_ALLIANCE_ID,
)

data class CreateChatRoomRequest(
    val title: String,
    val sortOrder: Int? = null,
)

data class UpdateChatRoomRequest(
    val title: String? = null,
    val sortOrder: Int? = null,
    val archived: Boolean? = null,
)
