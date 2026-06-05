package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import kotlinx.coroutines.delay

const val FORUM_PIN_JUMP_MAX_ATTEMPTS = 40

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

/**
 * Scroll to a pinned forum message, loading older pages when needed.
 * Returns true when the message was found and scroll initiated.
 */
suspend fun jumpToForumPinnedMessage(
    messageId: String,
    messageIdsOldestFirst: List<String>,
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

    fun messageLoaded(): Boolean =
        messageIdsOldestFirst.any { it.trim() == id }

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
    while (attempts < FORUM_PIN_JUMP_MAX_ATTEMPTS) {
        if (messageLoaded()) {
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
