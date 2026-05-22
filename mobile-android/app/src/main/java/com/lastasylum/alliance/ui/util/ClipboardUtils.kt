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

/** Текст для вставки в композер (без плейсхолдера «изображение»). */
fun chatMessageTextForComposer(message: ChatMessage): String? {
    if (!message.deletedAt.isNullOrBlank()) return null
    if (ZlobyakaStickerPack.parseStem(message.text) != null) return null
    return message.text.trim().takeIf { it.isNotEmpty() }
}

fun chatMessageHasPasteableText(message: ChatMessage): Boolean =
    chatMessageTextForComposer(message) != null

fun readClipboardPlainText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    if (!cm.hasPrimaryClip()) return null
    val clip = cm.primaryClip ?: return null
    for (i in 0 until clip.itemCount) {
        val text = clip.getItemAt(i).coerceToText(context)?.toString()?.trim()
        if (!text.isNullOrEmpty()) return text
    }
    return null
}

fun appendTextToDraft(current: String, addition: String): String {
    val add = addition.trim()
    if (add.isEmpty()) return current
    if (current.isBlank()) return add
    val sep = if (current.endsWith(' ') || current.endsWith('\n')) "" else " "
    return current + sep + add
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
