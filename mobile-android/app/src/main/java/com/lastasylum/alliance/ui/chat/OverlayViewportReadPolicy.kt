package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.isObjectIdNewer

/**
 * Overlay chat mark-read watermark from visible message ids (viewport policy).
 */
internal fun computeOverlayViewportReadWatermark(
    messageIds: List<String>,
    messages: List<ChatMessage>,
    lastReadMessageId: String?,
    currentUserId: String,
    isValidMessageId: (String) -> Boolean,
): String? {
    val lastRead = lastReadMessageId?.trim().orEmpty()
    val self = currentUserId.trim()
    var watermark: String? = null
    for (raw in messageIds) {
        val id = raw.trim()
        if (!isValidMessageId(id)) continue
        if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
        val senderId = messages.find { it._id?.trim() == id }?.senderId?.trim().orEmpty()
        if (self.isNotBlank() && senderId == self) continue
        watermark = when (val prev = watermark) {
            null -> id
            else -> if (isObjectIdNewer(id, prev)) id else prev
        }
    }
    val markId = watermark ?: return null
    if (lastRead.isNotEmpty() && !isObjectIdNewer(markId, lastRead)) return null
    return markId
}

/** Whether overlay panel counts as actively viewing the selected room for mark-read. */
internal fun isOverlayRoomActivelyViewed(
    overlayChatTabActive: Boolean,
    appInForeground: Boolean,
    fullscreenPanelVisible: Boolean,
    overlayChatPanelOpenInGame: Boolean,
): Boolean {
    if (!overlayChatTabActive) return false
    return appInForeground || fullscreenPanelVisible || overlayChatPanelOpenInGame
}
