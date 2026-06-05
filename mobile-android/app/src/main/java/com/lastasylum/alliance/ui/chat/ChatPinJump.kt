package com.lastasylum.alliance.ui.chat

import kotlinx.coroutines.delay

/**
 * Scroll to a pinned chat message, loading older pages when needed.
 * Returns true when the message was found and scroll was requested.
 */
suspend fun jumpToChatPinnedMessage(
    messageId: String,
    messageIdsNewestFirst: List<String>,
    hasMoreOlder: () -> Boolean,
    isLoadingOlder: () -> Boolean,
    loadOlder: suspend () -> Boolean,
    timelineIndexForMessageId: (String) -> Int,
    onJumpToMessage: (String) -> Unit,
): Boolean {
    val id = messageId.trim()
    if (id.isEmpty()) return false

    fun tryJump(): Boolean {
        val idx = timelineIndexForMessageId(id)
        if (idx < 0) return false
        onJumpToMessage(id)
        return true
    }

    if (tryJump()) return true
    if (!hasMoreOlder()) return false

    var attempts = 0
    while (attempts < FORUM_PIN_JUMP_MAX_ATTEMPTS) {
        if (messageIdsNewestFirst.any { it.trim() == id }) {
            if (tryJump()) return true
        }
        if (!hasMoreOlder()) break
        if (isLoadingOlder()) {
            delay(40)
            attempts++
            continue
        }
        val loaded = loadOlder()
        if (!loaded) break
        attempts++
    }
    return tryJump()
}
