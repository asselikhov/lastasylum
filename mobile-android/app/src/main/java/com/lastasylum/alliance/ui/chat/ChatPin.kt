package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import java.time.Instant

fun ChatMessage.toPinnedPreview(): PinnedMessagePreviewDto? {
    val id = _id?.trim().orEmpty()
    if (id.isEmpty()) return null
    val hasImage = attachments.any { it.kind == "image" && it.url.isNotBlank() }
    return PinnedMessagePreviewDto(
        id = id,
        text = text,
        senderUsername = senderUsername,
        senderTeamTag = senderTeamTag,
        senderServerNumber = senderServerNumber,
        createdAt = createdAt ?: Instant.now().toString(),
        editedAt = editedAt,
        hasImage = hasImage,
        isSticker = StickerPacks.stemForMessage(text) != null,
    )
}

fun TeamForumMessageDto.toPinnedPreview(): PinnedMessagePreviewDto =
    PinnedMessagePreviewDto(
        id = id,
        text = text,
        senderUsername = senderUsername,
        senderTeamTag = senderTeamTag,
        senderServerNumber = senderServerNumber,
        createdAt = createdAt,
        editedAt = editedAt,
        hasImage = imageRelativeUrl != null || imageRelativeUrls.isNotEmpty(),
        isSticker = StickerPacks.stemForMessage(text) != null,
    )

fun ChatRoomDto.withOptimisticPin(
    messageId: String,
    preview: PinnedMessagePreviewDto,
    pinnedByUserId: String,
): ChatRoomDto = copy(
    pinnedMessageId = messageId,
    pinnedAt = Instant.now().toString(),
    pinnedByUserId = pinnedByUserId,
    pinnedMessage = preview,
)

fun ChatRoomDto.withOptimisticUnpin(): ChatRoomDto = copy(
    pinnedMessageId = null,
    pinnedAt = null,
    pinnedByUserId = null,
    pinnedMessage = null,
)

fun resolveChatPinnedPreview(
    pinnedMessageId: String?,
    pinnedMessage: PinnedMessagePreviewDto?,
    messages: List<ChatMessage>,
): PinnedMessagePreviewDto? {
    pinnedMessage?.let { return it }
    val id = pinnedMessageId?.trim().orEmpty()
    if (id.isEmpty()) return null
    return messages.find { it._id == id }?.toPinnedPreview()
}

fun resolveForumPinnedPreview(
    pinnedMessageId: String?,
    pinnedMessage: PinnedMessagePreviewDto?,
    messages: List<TeamForumMessageDto>,
): PinnedMessagePreviewDto? {
    pinnedMessage?.let { return it }
    val id = pinnedMessageId?.trim().orEmpty()
    if (id.isEmpty()) return null
    return messages.find { it.id == id }?.toPinnedPreview()
}
