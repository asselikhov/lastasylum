package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.util.sanitizePublicDisplayName
data class OverlayReactionLogReplyTo(
    val logId: String,
    val reaction: String,
    val visibility: OverlayReactionLogVisibility,
    val senderUserId: String,
    val senderUsername: String,
    val targetUserId: String?,
    val targetUsername: String?,
)

data class OverlayReactionLogEntry(
    val id: String,
    val senderUserId: String,
    val senderUsername: String,
    val targetUserId: String?,
    val targetUsername: String?,
    val reaction: String,
    val visibility: OverlayReactionLogVisibility,
    val createdAt: String,
    val reactions: List<ChatReaction> = emptyList(),
    val replyToLogId: String? = null,
    val replyToLog: OverlayReactionLogReplyTo? = null,
)

enum class OverlayReactionLogVisibility {
    Personal,
    Broadcast,
    ;

    companion object {
        fun fromWire(raw: String?): OverlayReactionLogVisibility =
            when (raw?.trim()?.lowercase()) {
                "broadcast" -> Broadcast
                else -> Personal
            }

        fun toWire(value: OverlayReactionLogVisibility): String =
            when (value) {
                Personal -> "personal"
                Broadcast -> "broadcast"
            }
    }
}

data class OverlayReactionLogListResponse(
    val items: List<OverlayReactionLogEntryDto>,
    val nextCursor: String?,
)

data class OverlayReactionLogEntryDto(
    val id: String? = null,
    val _id: String? = null,
    val senderUserId: String,
    val senderUsername: String,
    val targetUserId: String? = null,
    val targetUsername: String? = null,
    val reaction: String,
    val visibility: String,
    val createdAt: String,
    val reactions: List<ChatReactionDto>? = null,
    val replyToLogId: String? = null,
    val replyToLog: OverlayReactionLogReplyToDto? = null,
) {
    fun resolvedId(): String = id?.trim().orEmpty().ifBlank { _id?.trim().orEmpty() }

    fun toEntry(selfUserId: String = ""): OverlayReactionLogEntry? {
        val entryId = resolvedId()
        if (entryId.isBlank()) return null
        return OverlayReactionLogEntry(
            id = entryId,
            senderUserId = senderUserId.trim(),
            senderUsername = sanitizePublicDisplayName(senderUsername),
            targetUserId = targetUserId?.trim()?.takeIf { it.isNotEmpty() },
            targetUsername = targetUsername?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sanitizePublicDisplayName(it)
            },
            reaction = reaction.trim(),
            visibility = OverlayReactionLogVisibility.fromWire(visibility),
            createdAt = createdAt.trim(),
            reactions = reactions?.map { it.toChatReaction(selfUserId) } ?: emptyList(),
            // Mongo/mongoose can send `replyToLogId: null`; Android JSON parsing may yield the literal "null".
            // Treat it as absent so personal reactions are not incorrectly marked as reply.
            replyToLogId = replyToLogId
                ?.trim()
                ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) },
            replyToLog = replyToLog?.toReplyTo(),
        )
    }
}

data class OverlayReactionLogReplyToDto(
    val id: String? = null,
    val _id: String? = null,
    val reaction: String,
    val visibility: String,
    val senderUserId: String,
    val senderUsername: String,
    val targetUserId: String? = null,
    val targetUsername: String? = null,
) {
    fun resolvedId(): String = id?.trim().orEmpty().ifBlank { _id?.trim().orEmpty() }

    fun toReplyTo(): OverlayReactionLogReplyTo? {
        val logId = resolvedId()
        if (logId.isBlank()) return null
        return OverlayReactionLogReplyTo(
            logId = logId,
            reaction = reaction.trim(),
            visibility = OverlayReactionLogVisibility.fromWire(visibility),
            senderUserId = senderUserId.trim(),
            senderUsername = sanitizePublicDisplayName(senderUsername),
            targetUserId = targetUserId?.trim()?.takeIf { it.isNotEmpty() },
            targetUsername = targetUsername?.trim()?.takeIf { it.isNotEmpty() }?.let {
                sanitizePublicDisplayName(it)
            },
        )
    }
}

data class ChatReactionDto(
    val emoji: String,
    val count: Int = 0,
    val reactedByMe: Boolean = false,
    val userIds: List<String>? = null,
) {
    fun toChatReaction(selfUserId: String): ChatReaction {
        val ids = userIds.orEmpty()
        return ChatReaction(
            emoji = emoji,
            count = if (count > 0) count else ids.size,
            reactedByMe = reactedByMe ||
                (selfUserId.isNotBlank() && ids.contains(selfUserId)),
        )
    }
}

data class ToggleOverlayReactionLogReactionRequest(
    val emoji: String,
)

data class OverlayReactionReadCursorResponse(
    val lastSeenLogId: String? = null,
)

data class AdvanceOverlayReactionReadCursorRequest(
    val lastSeenLogId: String,
)
