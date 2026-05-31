package com.lastasylum.alliance.data.chat

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
) {
    fun resolvedId(): String = id?.trim().orEmpty().ifBlank { _id?.trim().orEmpty() }

    fun toEntry(selfUserId: String = ""): OverlayReactionLogEntry? {
        val entryId = resolvedId()
        if (entryId.isBlank()) return null
        return OverlayReactionLogEntry(
            id = entryId,
            senderUserId = senderUserId.trim(),
            senderUsername = senderUsername.trim(),
            targetUserId = targetUserId?.trim()?.takeIf { it.isNotEmpty() },
            targetUsername = targetUsername?.trim()?.takeIf { it.isNotEmpty() },
            reaction = reaction.trim(),
            visibility = OverlayReactionLogVisibility.fromWire(visibility),
            createdAt = createdAt.trim(),
            reactions = reactions?.map { it.toChatReaction(selfUserId) } ?: emptyList(),
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
