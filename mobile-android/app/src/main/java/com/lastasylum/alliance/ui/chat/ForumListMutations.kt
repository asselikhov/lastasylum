package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto

/** Oldest-first in-memory cap (forum thread UI). */
internal const val FORUM_MAX_MESSAGES_IN_MEMORY = 800

/** API returns newest-first; UI thread is oldest-first (index 0 = oldest). */
internal fun sortForumMessagesOldestFirst(messages: MutableList<TeamForumMessageDto>) {
    if (messages.size < 2) return
    messages.sortBy { it.id }
}

internal fun capForumMessagesOldestFirst(
    messages: MutableList<TeamForumMessageDto>,
    max: Int = FORUM_MAX_MESSAGES_IN_MEMORY,
) {
    sortForumMessagesOldestFirst(messages)
    val overflow = messages.size - max
    if (overflow <= 0) return
    repeat(overflow) { messages.removeAt(0) }
}

/** When loading older pages, trim excess from the newest side so scrolled history stays visible. */
internal fun capForumMessagesTrimNewestOnly(
    messages: MutableList<TeamForumMessageDto>,
    max: Int = FORUM_MAX_MESSAGES_IN_MEMORY,
) {
    sortForumMessagesOldestFirst(messages)
    while (messages.size > max) {
        messages.removeAt(messages.lastIndex)
    }
}

/** Union-merge REST page with in-memory rows (socket-only ids preserved). */
internal fun mergeForumMessagesPage(
    existing: List<TeamForumMessageDto>,
    loaded: List<TeamForumMessageDto>,
): List<TeamForumMessageDto> {
    val byId = linkedMapOf<String, TeamForumMessageDto>()
    for (m in existing) byId[m.id] = m
    for (m in loaded) {
        val prev = byId[m.id]
        byId[m.id] = if (prev != null) prev.mergePreservingForumMedia(m) else m
    }
    return byId.values.sortedBy { it.id }
}

internal fun TeamForumMessageDto.hasForumImages(): Boolean =
    imageRelativeUrls.isNotEmpty() || !imageRelativeUrl.isNullOrBlank()

/** Socket/REST races must not drop attachments already shown in the thread. */
internal fun TeamForumMessageDto.mergePreservingForumMedia(incoming: TeamForumMessageDto): TeamForumMessageDto {
    if (incoming.hasForumImages()) return incoming
    if (!hasForumImages()) return incoming
    return incoming.copy(
        imageRelativeUrl = imageRelativeUrl,
        imageRelativeUrls = imageRelativeUrls,
        fileRelativeUrl = incoming.fileRelativeUrl ?: fileRelativeUrl,
        fileFilename = incoming.fileFilename ?: fileFilename,
    )
}
