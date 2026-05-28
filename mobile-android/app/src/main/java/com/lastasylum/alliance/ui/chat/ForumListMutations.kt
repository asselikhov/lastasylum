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
