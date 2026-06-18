package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage

/**
 * Resolve clientMessageId for own socket/HTTP echoes.
 * Never guess when multiple outgoing sends are in flight.
 */
internal fun resolveOutgoingClientMessageId(
    message: ChatMessage,
    activeOutgoingClientMessageIds: Set<String>,
): String? {
    message.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    if (activeOutgoingClientMessageIds.size == 1) {
        return activeOutgoingClientMessageIds.singleOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
    return null
}
