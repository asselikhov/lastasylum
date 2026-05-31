package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage

/** Incoming overlay strip cards are shown to recipients only, not to the sender. */
internal object OverlayStripRecipientPolicy {
    fun shouldShowIncomingStripCard(msg: ChatMessage, selfUserId: String?): Boolean {
        if (OverlayStripNoticeIds.isNotice(msg._id)) return true
        val self = selfUserId?.trim().orEmpty()
        if (self.isEmpty()) return true
        return msg.senderId.trim() != self
    }
}
