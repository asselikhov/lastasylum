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
) {
    fun resolvedId(): String = id?.trim().orEmpty().ifBlank { _id?.trim().orEmpty() }

    fun toEntry(): OverlayReactionLogEntry? {
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
        )
    }
}

data class OverlayReactionReadCursorResponse(
    val lastSeenLogId: String? = null,
)

data class AdvanceOverlayReactionReadCursorRequest(
    val lastSeenLogId: String,
)
