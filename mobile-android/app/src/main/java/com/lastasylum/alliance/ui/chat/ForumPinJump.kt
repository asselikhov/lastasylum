package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import kotlinx.coroutines.delay

/** @deprecated Use [PIN_JUMP_MAX_LOAD_ATTEMPTS]. */
const val FORUM_PIN_JUMP_MAX_ATTEMPTS = PIN_JUMP_MAX_LOAD_ATTEMPTS

/** Resolve lazy-list index even when deferred timeline derive is still empty/stale. */
fun forumLazyIndexForMessageId(
    messages: List<TeamForumMessageDto>,
    derived: ForumMessagesListDerived,
    messageId: String,
): Int {
    val id = messageId.trim()
    if (id.isEmpty()) return -1
    derived.fullLazyIndexForMessageId(id)?.let { return it }
    if (messages.isEmpty()) return -1
    return buildForumMessagesListDerived(messages).fullLazyIndexForMessageId(id) ?: -1
}

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
 * Scroll to a pinned forum message, loading older pages when needed.
 * Returns true when the message was found and scroll initiated.
 */
suspend fun jumpToForumPinnedMessage(
    messageId: String,
    messageIdsOldestFirst: () -> List<String>,
    hasMoreOlder: () -> Boolean,
    isLoadingOlder: () -> Boolean,
    loadOlder: suspend () -> Boolean,
    timelineIndexForMessageId: (String) -> Int,
    scrollToTimelineIndex: suspend (Int) -> Unit,
    onHighlight: (String) -> Unit,
    highlightMs: Long = 900L,
): Boolean {
    val id = messageId.trim()
    if (id.isEmpty()) return false

    suspend fun tryJump(): Boolean {
        val idx = timelineIndexForMessageId(id)
        if (idx < 0) return false
        scrollToTimelineIndex(idx)
        onHighlight(id)
        delay(highlightMs)
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
        if (!loaded && !messageIdsOldestFirst().any { it.trim() == id }) break
        attempts++
    }
    return tryJump()
}
