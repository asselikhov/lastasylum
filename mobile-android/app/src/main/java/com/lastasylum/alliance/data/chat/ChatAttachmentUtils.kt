package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.chat.chatMessageShowsEditedLabel
import org.json.JSONObject

fun isChatImageKind(kind: String): Boolean = kind.equals("image", ignoreCase = true)

fun ChatAttachment.isChatImage(): Boolean = isChatImageKind(kind) && url.isNotBlank()

fun List<ChatAttachment>.chatImageAttachments(): List<ChatAttachment> =
    filter { it.isChatImage() }

fun ChatMessage.chatImageAttachments(): List<ChatAttachment> =
    attachments.chatImageAttachments()

fun ChatMessage.hasVisibleText(): Boolean = text.trim().isNotBlank()

/**
 * Compact `message:reaction` socket payload (`messageId` + reactions, no sender row).
 * Must not create a new strip card — only merge into an existing buffered message.
 */
fun ChatMessage.isCompactReactionSocketUpdate(): Boolean {
    val id = _id?.trim().orEmpty()
    if (id.isEmpty()) return false
    if (senderId.isNotBlank() || senderUsername.isNotBlank()) return false
    if (hasVisibleText() || attachments.isNotEmpty()) return false
    return true
}

/** Socket/API updates without attachments must not wipe images already shown from REST. */
fun ChatMessage.mergePreservingAttachments(existing: ChatMessage): ChatMessage {
    val merged = when {
        attachments.isNotEmpty() -> this
        existing.attachments.isEmpty() -> this
        else -> copy(attachments = existing.attachments)
    }
    val editedAt = if (chatMessageShowsEditedLabel(merged)) {
        merged.editedAt
    } else {
        null
    }
    return merged.copy(editedAt = editedAt)
}

/** In-place row from socket (reactions, edit, delete) — incoming fields win except attachments. */
fun ChatMessage.mergeIncomingChatUpdate(existing: ChatMessage): ChatMessage {
    if (isCompactReactionSocketUpdate()) {
        return existing.copy(
            reactions = reactions.resolveFromSocketUpdate(existing.reactions),
        )
    }
    val merged = mergePreservingAttachments(existing)
    return merged.copy(
        text = text,
        editedAt = merged.editedAt ?: existing.editedAt,
        reactions = merged.reactions.resolveFromSocketUpdate(existing.reactions),
    )
}

/**
 * Socket payloads should use `{ count, reactedByMe }`, but a bad parse yields `count = 0`
 * while the row already shows reactions from REST — do not wipe peers' reactions.
 */
internal fun List<ChatReaction>.resolveFromSocketUpdate(
    existing: List<ChatReaction>,
): List<ChatReaction> {
    if (isEmpty()) return emptyList()
    if (any { it.count > 0 }) return this
    if (existing.any { it.count > 0 }) return existing
    return this
}

internal fun JSONObject.parseChatAttachments(): List<ChatAttachment> {
    val arr = optJSONArray("attachments") ?: return emptyList()
    val out = ArrayList<ChatAttachment>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val kind = o.optString("kind").takeIf { it.isNotBlank() } ?: continue
        val url = o.resolveChatAttachmentUrlFromJson() ?: continue
        out.add(
            ChatAttachment(
                kind = kind,
                url = url,
                mimeType = o.optString("mimeType").takeIf { it.isNotBlank() },
                size = o.optLong("size").takeIf { it > 0L },
                filename = o.optString("filename").takeIf { it.isNotBlank() },
            ),
        )
    }
    return out
}

private fun JSONObject.resolveChatAttachmentUrlFromJson(): String? {
    optString("url").takeIf { it.isNotBlank() }?.let { return it }
    val rawId = optString("fileId").takeIf { it.isNotBlank() }
        ?: optJSONObject("fileId")?.optString("\$oid")?.takeIf { it.isNotBlank() }
    return rawId?.let { "/chat/attachments/$it" }
}
