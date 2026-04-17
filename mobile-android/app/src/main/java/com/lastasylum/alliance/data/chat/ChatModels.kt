package com.lastasylum.alliance.data.chat

data class ChatMessage(
    val _id: String? = null,
    val allianceId: String,
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val text: String,
    val createdAt: String? = null,
)

data class SendMessageRequest(
    val text: String,
    val allianceId: String = "OBZHORY",
)
