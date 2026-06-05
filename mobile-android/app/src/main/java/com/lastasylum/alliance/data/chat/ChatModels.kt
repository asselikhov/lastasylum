package com.lastasylum.alliance.data.chat

import androidx.compose.runtime.Immutable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Immutable
data class PinnedMessagePreviewDto(
    val id: String,
    val text: String,
    val senderUsername: String,
    val senderTeamTag: String? = null,
    val senderServerNumber: Int? = null,
    val createdAt: String,
    val editedAt: String? = null,
    val hasImage: Boolean = false,
    val isSticker: Boolean = false,
    val imageThumbnailUrl: String? = null,
    val pinnedByUsername: String? = null,
)

@Immutable
@JsonClass(generateAdapter = true)
data class ChatRoomDto(
    @Json(name = "_id") val mongoId: String? = null,
    @Json(name = "id") val legacyId: String? = null,
    val allianceId: String? = null,
    val title: String,
    val sortOrder: Int = 0,
    val archivedAt: String? = null,
    val unreadCount: Int = 0,
    val lastReadMessageId: String? = null,
    val pinnedMessageId: String? = null,
    val pinnedAt: String? = null,
    val pinnedByUserId: String? = null,
    val pinnedMessage: PinnedMessagePreviewDto? = null,
    val pinnedMessages: List<PinnedMessagePreviewDto> = emptyList(),
) {
    val id: String
        get() = mongoId?.trim().orEmpty().ifEmpty { legacyId?.trim().orEmpty() }

    constructor(
        id: String,
        allianceId: String? = null,
        title: String,
        sortOrder: Int = 0,
        archivedAt: String? = null,
        unreadCount: Int = 0,
        lastReadMessageId: String? = null,
        pinnedMessageId: String? = null,
        pinnedAt: String? = null,
        pinnedByUserId: String? = null,
        pinnedMessage: PinnedMessagePreviewDto? = null,
        pinnedMessages: List<PinnedMessagePreviewDto> = emptyList(),
    ) : this(
        mongoId = id,
        legacyId = null,
        allianceId = allianceId,
        title = title,
        sortOrder = sortOrder,
        archivedAt = archivedAt,
        unreadCount = unreadCount,
        lastReadMessageId = lastReadMessageId,
        pinnedMessageId = pinnedMessageId,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        pinnedMessage = pinnedMessage,
        pinnedMessages = pinnedMessages,
    )
}

/** Realtime + REST pin state for a chat room. */
data class ChatRoomPinChangedEvent(
    val roomId: String,
    val pinnedMessageId: String? = null,
    val pinnedAt: String? = null,
    val pinnedByUserId: String? = null,
    val pinnedMessage: PinnedMessagePreviewDto? = null,
    val pinnedMessages: List<PinnedMessagePreviewDto> = emptyList(),
)

data class PinRoomMessageRequest(
    val messageId: String? = null,
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
    val senderServerNumber: Int? = null,
    val senderTelegramUsername: String? = null,
    val text: String,
    val editedAt: String? = null,
    val forwardedFrom: ChatForwardedFrom? = null,
    val reactions: List<ChatReaction> = emptyList(),
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val replyToMessageId: String? = null,
    val replyTo: ChatMessageReplyPreview? = null,
    val deletedAt: String? = null,
    val deletedByUserId: String? = null,
)

@Immutable
data class ChatForwardedFrom(
    val messageId: String,
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val senderTeamTag: String? = null,
    val senderServerNumber: Int? = null,
)

@Immutable
data class ChatReaction(
    val emoji: String,
    val count: Int,
    val reactedByMe: Boolean,
)

@Immutable
data class ChatAttachment(
    val kind: String,
    val url: String,
    val mimeType: String? = null,
    val size: Long? = null,
    val filename: String? = null,
)

data class SendMessageRequest(
    val text: String,
    val roomId: String,
    val allianceId: String? = AllianceDefaults.DEFAULT_ALLIANCE_ID,
    val replyToMessageId: String? = null,
    val attachments: List<String>? = null,
    val excavationAlert: Boolean? = null,
    val gameEventAlert: String? = null,
)

data class EditMessageRequest(
    val text: String,
)

data class ToggleReactionRequest(
    val emoji: String,
)

data class ForwardMessageRequest(
    val roomId: String,
)

data class MarkRoomReadRequest(
    val messageId: String,
)

data class MarkRoomReadResponse(
    val success: Boolean = true,
    val unreadCount: Int = 0,
)

/** Per-user room history clear (messages stay in DB for other members). */
data class ClearRoomHistoryResponse(
    val roomId: String,
    val hiddenBeforeMessageId: String? = null,
    val lastReadMessageId: String? = null,
    val unreadCount: Int = 0,
)

/** Server push after message:new / markRoomRead (personal user socket room). */
data class ChatRoomUnreadEvent(
    val roomId: String,
    val unreadCount: Int = 0,
    val lastReadMessageId: String? = null,
)

data class UploadChatAttachmentResponse(
    val fileId: String,
    val url: String,
    val kind: String? = null,
    val mimeType: String,
    val size: Long,
    val filename: String? = null,
)

@Immutable
data class ChatMessageReplyPreview(
    val _id: String,
    val senderId: String,
    val senderUsername: String,
    val senderRole: String,
    val senderTeamTag: String? = null,
    val senderServerNumber: Int? = null,
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
    val pinChanged: ChatRoomPinChangedEvent? = null,
)

data class ChatTypingEvent(
    val roomId: String,
    val userId: String,
    val username: String,
)

data class ChatRoomReadEvent(
    val roomId: String,
    val userId: String,
    val messageId: String,
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
