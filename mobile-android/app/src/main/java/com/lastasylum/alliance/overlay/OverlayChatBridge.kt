package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatMessageDeletedEvent
import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import com.lastasylum.alliance.data.chat.ChatRoomUnreadEvent
import com.lastasylum.alliance.data.chat.ChatTypingEvent
import com.lastasylum.alliance.data.chat.OverlayReactionEvent

/**
 * Typed overlay chat/realtime listener bundle for [CombatOverlayService].
 */
internal data class OverlayChatBridgeListeners(
    val onMessage: (ChatMessage) -> Unit,
    val onDeleted: (ChatMessageDeletedEvent) -> Unit,
    val onRead: (ChatRoomReadEvent) -> Unit,
    val onTyping: (ChatTypingEvent) -> Unit,
    val onRoomUnread: (ChatRoomUnreadEvent) -> Unit,
    val onReaction: (OverlayReactionEvent) -> Unit,
    val onReactionLog: (com.lastasylum.alliance.data.chat.OverlayReactionLogEntryDto) -> Unit,
    val onReactionLogReaction: (com.lastasylum.alliance.data.chat.OverlayReactionLogEntryDto) -> Unit,
)
