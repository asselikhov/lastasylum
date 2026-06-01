package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.util.parseIsoInstant

object ChatMessageVisibilityPolicy {
    /** True when the message should appear in this user's room timeline. */
    fun isMessageVisible(
        message: ChatMessage,
        hiddenBeforeMessageId: String?,
    ): Boolean = isMessageVisible(
        messageId = message._id,
        createdAt = message.createdAt,
        hiddenBeforeMessageId = hiddenBeforeMessageId,
    )

    fun isMessageVisible(
        messageId: String?,
        createdAt: String?,
        hiddenBeforeMessageId: String?,
    ): Boolean {
        val hidden = hiddenBeforeMessageId?.trim().orEmpty()
        if (hidden.isEmpty()) return true
        val id = messageId?.trim().orEmpty()
        if (id.isNotEmpty() && id.equals(hidden, ignoreCase = true)) return false
        return isMessageAfterCursor(
            messageId = id,
            createdAt = createdAt,
            cursor = hidden,
        )
    }

    internal fun isMessageAfterCursor(
        messageId: String,
        createdAt: String?,
        cursor: String,
    ): Boolean {
        if (messageId.isNotEmpty() && messageId.equals(cursor, ignoreCase = true)) return false
        val messageInstant = parseIsoInstant(createdAt?.trim())
        val cursorInstant = objectIdInstantOrNull(cursor)
        if (messageInstant != null && cursorInstant != null) {
            return messageInstant.isAfter(cursorInstant)
        }
        if (messageId.length == 24 && cursor.length == 24) {
            return messageId.compareTo(cursor, ignoreCase = true) > 0
        }
        if (messageId.isNotEmpty()) {
            return messageId > cursor
        }
        return true
    }

    private fun objectIdInstantOrNull(id: String): java.time.Instant? {
        if (id.length != 24) return null
        return runCatching {
            val ts = id.substring(0, 8).toLong(16)
            java.time.Instant.ofEpochSecond(ts)
        }.getOrNull()
    }
}
