package com.lastasylum.alliance.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack

fun copyPlainTextToClipboard(context: Context, clipLabel: String, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
    Toast.makeText(context, context.getString(R.string.chat_copied_toast), Toast.LENGTH_SHORT).show()
}

fun copyChatMessageToClipboard(context: Context, message: ChatMessage) {
    if (!message.deletedAt.isNullOrBlank()) return
    val sticker = ZlobyakaStickerPack.parseStem(message.text)
    val text = when {
        sticker != null -> context.getString(R.string.chat_copy_sticker_line, sticker)
        message.text.isNotBlank() -> message.text
        message.attachments.any { it.kind == "image" && it.url.isNotBlank() } ->
            context.getString(R.string.chat_copy_image_placeholder)
        else -> return
    }
    copyPlainTextToClipboard(
        context,
        context.getString(R.string.chat_message_clipboard_label),
        text,
    )
}

fun chatMessageHasCopyableContent(message: ChatMessage): Boolean {
    if (!message.deletedAt.isNullOrBlank()) return false
    if (ZlobyakaStickerPack.parseStem(message.text) != null) return true
    if (message.text.isNotBlank()) return true
    if (message.attachments.any { it.kind == "image" && it.url.isNotBlank() }) return true
    return false
}

fun forumMessageHasCopyableContent(message: TeamForumMessageDto): Boolean {
    if (!message.deletedAt.isNullOrBlank()) return false
    if (ZlobyakaStickerPack.parseStem(message.text) != null) return true
    if (message.text.isNotBlank()) return true
    if (message.imageRelativeUrls.isNotEmpty()) return true
    if (!message.imageRelativeUrl.isNullOrBlank()) return true
    return false
}

fun copyForumMessageToClipboard(context: Context, message: TeamForumMessageDto) {
    if (!message.deletedAt.isNullOrBlank()) return
    val sticker = ZlobyakaStickerPack.parseStem(message.text)
    val text = when {
        sticker != null -> context.getString(R.string.chat_copy_sticker_line, sticker)
        message.text.isNotBlank() -> message.text
        message.imageRelativeUrls.isNotEmpty() ||
            !message.imageRelativeUrl.isNullOrBlank() -> context.getString(R.string.chat_copy_image_placeholder)
        else -> return
    }
    copyPlainTextToClipboard(
        context,
        context.getString(R.string.chat_message_clipboard_label),
        text,
    )
}
