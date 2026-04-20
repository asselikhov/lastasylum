package com.lastasylum.alliance.data.chat

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json

@Immutable
data class ChatRoomDto(
    @Json(name = "_id") val id: String,
    val allianceId: String? = null,
    val title: String,
    val sortOrder: Int = 0,
    val archivedAt: String? = null,
)

@Immutable
data class ChatMessage(
    val _id: String? = null,
    val allianceId: String,
    val roomId: String = "",
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val senderTeamTag: String? = null,
    val senderTelegramUsername: String? = null,
    val text: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val replyToMessageId: String? = null,
    val replyTo: ChatMessageReplyPreview? = null,
    val deletedAt: String? = null,
    val deletedByUserId: String? = null,
)

@Immutable
data class ChatAttachment(
    val kind: String,
    val url: String,
    val mimeType: String? = null,
    val size: Long? = null,
)

data class SendMessageRequest(
    val text: String,
    val roomId: String,
    val allianceId: String? = AllianceDefaults.DEFAULT_ALLIANCE_ID,
    val replyToMessageId: String? = null,
    val attachments: List<String>? = null,
)

data class UploadChatAttachmentResponse(
    val fileId: String,
    val url: String,
    val mimeType: String,
    val size: Long,
)

@Immutable
data class ChatMessageReplyPreview(
    val _id: String,
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val senderTeamTag: String? = null,
    val text: String,
    val createdAt: String? = null,
    val deletedAt: String? = null,
)

data class ChatMessageDeletedEvent(
    val messageId: String,
    val roomId: String,
    val deletedAt: String? = null,
    val deletedByUserId: String? = null,
)

data class ChatMessageDeleteResult(
    val messageId: String,
    val roomId: String,
)

data class ChatTypingEvent(
    val roomId: String,
    val userId: String,
    val username: String,
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
