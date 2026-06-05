package com.lastasylum.alliance.ui.chat.usecase

import com.lastasylum.alliance.data.isObjectIdNewer

/**
 * Unread helpers extracted from [com.lastasylum.alliance.ui.chat.ChatViewModel].
 */
internal object ChatUnreadUseCase {
    fun shouldTrackUnreadForMessage(
        messageId: String,
        lastReadMessageId: String?,
    ): Boolean {
        val lastRead = lastReadMessageId?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return isObjectIdNewer(messageId, lastRead)
    }
}
