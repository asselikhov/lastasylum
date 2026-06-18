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

/** Periodic REST reconcile while forum topic screen is active. */
internal const val ACTIVE_FORUM_RECONCILE_INTERVAL_MS = 30_000L

/** Faster reconcile while forum topic is open in overlay HUD. */
internal const val OVERLAY_ACTIVE_FORUM_RECONCILE_INTERVAL_MS = 8_000L

internal fun isForumPendingId(id: String): Boolean =
    id.trim().startsWith("pending-")

internal fun buildOptimisticForumMessage(
    teamId: String,
    topicId: String,
    senderUserId: String,
    senderUsername: String,
    text: String,
    clientMessageId: String,
    replyToMessageId: String? = null,
    nowIso: String,
): TeamForumMessageDto =
    TeamForumMessageDto(
        id = "pending-$clientMessageId",
        topicId = topicId,
        teamId = teamId,
        senderUserId = senderUserId,
        senderUsername = senderUsername,
        text = text,
        replyToMessageId = replyToMessageId,
        clientMessageId = clientMessageId,
        createdAt = nowIso,
        updatedAt = nowIso,
    )

internal fun hasMatchingPendingForumOutgoing(
    messages: List<TeamForumMessageDto>,
    incoming: TeamForumMessageDto,
    currentUserId: String,
): Boolean {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || incoming.senderUserId.trim() != selfId) return false
    val incomingClientId = incoming.clientMessageId?.trim().orEmpty()
    if (incomingClientId.isNotEmpty()) {
        return messages.any {
            it.clientMessageId?.trim() == incomingClientId && isForumPendingId(it.id)
        }
    }
    return messages.any {
        isForumPendingId(it.id) &&
            it.senderUserId.trim() == selfId &&
            it.text.trim() == incoming.text.trim()
    }
}

internal fun shouldBlockOwnForumOutgoingRealtime(
    messages: List<TeamForumMessageDto>,
    incoming: TeamForumMessageDto,
    currentUserId: String,
    inFlightClientMessageIds: Set<String>,
): Boolean {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || incoming.senderUserId.trim() != selfId) return false
    if (hasMatchingPendingForumOutgoing(messages, incoming, currentUserId)) return true
    val incomingClientId = incoming.clientMessageId?.trim().orEmpty()
    if (incomingClientId.isNotEmpty() && incomingClientId in inFlightClientMessageIds) return true
    return messages.any { it.id == incoming.id && !isForumPendingId(it.id) }
}

/** Replace optimistic pending row with confirmed server row; returns true when replaced. */
internal fun replaceMatchingPendingForumOutgoing(
    messages: MutableList<TeamForumMessageDto>,
    confirmed: TeamForumMessageDto,
    currentUserId: String,
): Boolean {
    val selfId = currentUserId.trim()
    if (selfId.isEmpty() || confirmed.senderUserId.trim() != selfId) return false
    val clientId = confirmed.clientMessageId?.trim().orEmpty()
    if (clientId.isNotEmpty()) {
        val idx = messages.indexOfFirst {
            it.clientMessageId?.trim() == clientId && isForumPendingId(it.id)
        }
        if (idx >= 0) {
            messages[idx] = messages[idx].mergePreservingForumMedia(confirmed)
            return true
        }
    }
    val idx = messages.indexOfFirst {
        isForumPendingId(it.id) &&
            it.senderUserId.trim() == selfId &&
            it.text.trim() == confirmed.text.trim()
    }
    if (idx >= 0) {
        messages[idx] = messages[idx].mergePreservingForumMedia(confirmed)
        return true
    }
    return false
}

internal fun removePendingForumOutgoing(
    messages: MutableList<TeamForumMessageDto>,
    clientMessageId: String,
): Boolean {
    val clientId = clientMessageId.trim()
    if (clientId.isEmpty()) return false
    return messages.removeAll {
        it.clientMessageId?.trim() == clientId && isForumPendingId(it.id)
    }
}

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
