package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.AllianceDefaults
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatForwardedFrom
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageReplyPreview
import com.lastasylum.alliance.data.teams.TeamForumMessageDto

/** Тема форума → [ChatMessage] для общего UI пузырей (как в комнатах). */
fun TeamForumMessageDto.toDisplayChatMessage(
    teamId: String,
    topicId: String,
): ChatMessage {
    val imageUrls = buildList {
        imageRelativeUrl?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        addAll(imageRelativeUrls.map { it.trim() }.filter { it.isNotBlank() })
    }.distinct()
    val attachments = buildList {
        imageUrls.forEach { url ->
            add(ChatAttachment(kind = "image", url = url))
        }
        fileRelativeUrl?.trim()?.takeIf { it.isNotBlank() }?.let { url ->
            add(
                ChatAttachment(
                    kind = "file",
                    url = url,
                    filename = fileFilename,
                    mimeType = "application/vnd.android.package-archive",
                ),
            )
        }
    }
    return ChatMessage(
        _id = id,
        allianceId = teamId.ifBlank { AllianceDefaults.DEFAULT_ALLIANCE_ID },
        roomId = topicId,
        senderId = senderUserId,
        senderUsername = senderUsername,
        senderRole = senderRole,
        senderTeamTag = senderTeamTag,
        senderServerNumber = senderServerNumber,
        senderTelegramUsername = senderTelegramUsername,
        text = text,
        editedAt = editedAt,
        forwardedFrom = forwardedFrom?.let { fwd ->
            ChatForwardedFrom(
                messageId = fwd.messageId,
                senderId = fwd.senderUserId,
                senderUsername = fwd.senderUsername,
                senderRole = fwd.senderRole,
                senderTeamTag = fwd.senderTeamTag,
                senderServerNumber = fwd.senderServerNumber,
            )
        },
        reactions = reactions,
        attachments = attachments,
        createdAt = createdAt,
        updatedAt = updatedAt,
        replyToMessageId = replyToMessageId,
        replyTo = replyTo?.let { rp ->
            ChatMessageReplyPreview(
                _id = rp.id,
                senderId = "",
                senderUsername = rp.senderUsername,
                senderRole = rp.senderRole,
                senderTeamTag = rp.senderTeamTag,
                senderServerNumber = rp.senderServerNumber,
                text = rp.text,
            )
        },
        deletedAt = deletedAt,
        deletedByUserId = deletedByUserId,
    )
}
