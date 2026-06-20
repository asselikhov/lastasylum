package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import java.util.concurrent.CopyOnWriteArraySet

/**
 * User dismissed overlay strip cards via ✕ — must stay hidden for the session even if
 * REST catch-up / panel close / socket re-ingest tries to re-add the same message.
 */
object OverlayStripDismissTracker {
    private val dismissedKeys = CopyOnWriteArraySet<String>()

    fun messageKey(msg: ChatMessage): String =
        msg._id?.trim()?.takeIf { it.isNotEmpty() } ?: msg.stableKey()

    fun markDismissed(msg: ChatMessage) {
        dismissedKeys.add(messageKey(msg))
        msg._id?.trim()?.takeIf { it.isNotEmpty() }?.let { dismissedKeys.add(it) }
        msg.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { dismissedKeys.add(it) }
    }

    fun isDismissed(msg: ChatMessage): Boolean {
        if (messageKey(msg) in dismissedKeys) return true
        msg._id?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it in dismissedKeys) return true }
        msg.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it in dismissedKeys) return true }
        return false
    }

    fun clear() {
        dismissedKeys.clear()
    }
}
