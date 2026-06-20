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

/**
 * Main-app delivery/mark-read gate for the selected room (overlay panel uses [isOverlayRoomActivelyViewed]).
 * Peer traffic applies live on the chat tab even while the game overlay probe is active.
 */
internal fun isMainAppRoomActivelyViewed(
    isChatTabActive: Boolean,
    isTargetGameForeground: Boolean,
    isPeerMessage: Boolean?,
): Boolean {
    if (isPeerMessage == true) return isChatTabActive
    if (isTargetGameForeground) return false
    return isChatTabActive
}

/**
 * Main-app LazyColumn viewport mark-read: user is on the chat tab viewing the room
 * (does not require the game to be backgrounded).
 */
internal fun isMainAppChatViewportEligible(
    isChatTabActive: Boolean,
    selectedRoomId: String,
    roomId: String,
    overlayChatPanelOpenInGame: Boolean,
    appInForeground: Boolean,
): Boolean {
    if (roomId.trim() != selectedRoomId.trim()) return false
    if (!isChatTabActive) return false
    if (overlayChatPanelOpenInGame) return false
    return appInForeground
}

/** Batch/incoming auto mark-read only when the user is at the bottom of the thread (Telegram). */
internal fun shouldAutoMarkReadIncomingAtBottom(
    isRoomActivelyViewed: Boolean,
    messageListAtBottom: Boolean,
): Boolean = isRoomActivelyViewed && messageListAtBottom

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
