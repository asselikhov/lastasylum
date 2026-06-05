package com.lastasylum.alliance.ui.screens.teamforum

import com.lastasylum.alliance.data.isObjectIdNewer

/** Merges peer topic read cursors for forum read receipts (✓✓). */
internal object ForumPeerReadCursorLogic {
    fun mergeTopicReadEvent(
        otherReadUptoByTopic: MutableMap<String, String>,
        topicId: String,
        userId: String,
        messageId: String,
        currentUserId: String,
    ): String? {
        if (userId.isBlank() || messageId.isBlank()) return null
        if (userId.trim() == currentUserId.trim()) return null
        val tid = topicId.trim()
        if (tid.isEmpty()) return null
        val cur = otherReadUptoByTopic[tid]
        if (!isObjectIdNewer(messageId, cur)) return null
        otherReadUptoByTopic[tid] = messageId
        return messageId
    }

    fun hydratePeerRead(
        otherReadUptoByTopic: MutableMap<String, String>,
        topicId: String,
        peerUptoMessageId: String?,
    ): String? {
        val id = peerUptoMessageId?.trim().orEmpty()
        val tid = topicId.trim()
        if (id.isEmpty() || tid.isEmpty()) return null
        val cur = otherReadUptoByTopic[tid]
        if (!isObjectIdNewer(id, cur)) return null
        otherReadUptoByTopic[tid] = id
        return id
    }
}
