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
 * Load older forum pages until [messageId] is present (no scroll).
 * Returns true when the message exists in the loaded window.
 */
suspend fun ensureForumPinnedMessageLoaded(
    messageId: String,
    messageIdsOldestFirst: () -> List<String>,
    hasMoreOlder: () -> Boolean,
    isLoadingOlder: () -> Boolean,
    loadOlder: suspend () -> Boolean,
): Boolean {
    val id = messageId.trim()
    if (id.isEmpty()) return false
    if (messageIdsOldestFirst().any { it.trim() == id }) return true
    if (!hasMoreOlder()) return false
    var attempts = 0
    while (attempts < PIN_JUMP_MAX_LOAD_ATTEMPTS) {
        if (messageIdsOldestFirst().any { it.trim() == id }) return true
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
    return messageIdsOldestFirst().any { it.trim() == id }
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

    suspend fun tryScroll(): Boolean {
        val idx = timelineIndexForMessageId(id)
        if (idx < 0) return false
        scrollToTimelineIndex(idx)
        onHighlight(id)
        return true
    }

    suspend fun finishHighlight(): Boolean {
        if (!tryScroll()) return false
        if (highlightMs > 0L) delay(highlightMs)
        return true
    }

    if (finishHighlight()) return true
    if (!hasMoreOlder()) return false

    val loaded = ensureForumPinnedMessageLoaded(
        messageId = id,
        messageIdsOldestFirst = messageIdsOldestFirst,
        hasMoreOlder = hasMoreOlder,
        isLoadingOlder = isLoadingOlder,
        loadOlder = loadOlder,
    )
    if (!loaded) return false
    return finishHighlight()
}
