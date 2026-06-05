package com.lastasylum.alliance.ui.chat

import kotlinx.coroutines.delay

/** Max older-page fetches while jumping to a pinned / quoted message. */
const val PIN_JUMP_MAX_LOAD_ATTEMPTS = 40

private suspend fun waitForLoadingOlderToFinish(
    isLoadingOlder: () -> Boolean,
    maxWaitMs: Long = 8_000L,
) {
    val deadline = System.currentTimeMillis() + maxWaitMs
    while (isLoadingOlder() && System.currentTimeMillis() < deadline) {
        delay(16)
    }
}

/**
 * Scroll to a pinned chat message, loading older pages when needed.
 * Returns true when the message was found and scroll was requested.
 */
suspend fun jumpToChatPinnedMessage(
    messageId: String,
    messageIdsNewestFirst: () -> List<String>,
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
    while (attempts < PIN_JUMP_MAX_LOAD_ATTEMPTS) {
        if (tryJump()) return true
        if (!hasMoreOlder()) break
        if (isLoadingOlder()) {
            waitForLoadingOlderToFinish(isLoadingOlder)
            attempts++
            continue
        }
        val loaded = loadOlder()
        waitForLoadingOlderToFinish(isLoadingOlder)
        if (!loaded && !messageIdsNewestFirst().any { it.trim() == id }) break
        attempts++
    }
    return tryJump()
}
