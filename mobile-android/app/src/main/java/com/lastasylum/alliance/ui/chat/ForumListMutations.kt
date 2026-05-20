package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto

/** Oldest-first in-memory cap (forum thread UI). */
internal const val FORUM_MAX_MESSAGES_IN_MEMORY = 800

internal fun capForumMessagesOldestFirst(
    messages: MutableList<TeamForumMessageDto>,
    max: Int = FORUM_MAX_MESSAGES_IN_MEMORY,
) {
    val overflow = messages.size - max
    if (overflow <= 0) return
    repeat(overflow) { messages.removeAt(0) }
}
