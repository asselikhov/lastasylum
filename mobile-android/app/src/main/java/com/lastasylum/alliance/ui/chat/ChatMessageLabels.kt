package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import java.time.Instant

/** Slack between [createdAt] and [editedAt] to ignore clock skew / same-second saves. */
private const val EDITED_LABEL_MIN_DELTA_MS = 2_000L

/**
 * Telegram-style: «изменено» only when the message was edited after send, not on every
 * new message or reaction-only [updatedAt] bumps.
 */
fun chatMessageShowsEditedLabel(message: ChatMessage): Boolean =
    chatMessageShowsEditedLabel(message.editedAt, message.createdAt)

fun chatMessageShowsEditedLabel(editedAt: String?, createdAt: String?): Boolean {
    val editedRaw = editedAt?.trim().orEmpty()
    if (editedRaw.isEmpty()) return false
    val createdRaw = createdAt?.trim().orEmpty()
    if (createdRaw.isEmpty()) return true
    val editedMs = parseChatInstantMs(editedRaw) ?: return true
    val createdMs = parseChatInstantMs(createdRaw) ?: return true
    return editedMs - createdMs >= EDITED_LABEL_MIN_DELTA_MS
}

internal fun parseChatInstantMs(iso: String): Long? =
    runCatching { Instant.parse(iso.trim()).toEpochMilli() }.getOrNull()
