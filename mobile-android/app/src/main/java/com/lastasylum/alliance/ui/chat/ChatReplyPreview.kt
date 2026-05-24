package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessageReplyPreview
import com.lastasylum.alliance.data.chat.stickers.StickerPacks

@Composable
fun replyPreviewText(reply: ChatMessageReplyPreview): String = replyPreviewText(reply.text)

@Composable
fun replyPreviewText(text: String): String {
    val parsed = StickerPacks.parse(text) ?: return text
    val packTitle = stringResource(parsed.pack.titleRes)
    return stringResource(R.string.chat_reply_preview_sticker_named, packTitle)
}

@Composable
fun chatMessageSemanticsPreview(text: String): String {
    val parsed = StickerPacks.parse(text) ?: return text.take(120)
    val packTitle = stringResource(parsed.pack.titleRes)
    return stringResource(R.string.chat_reply_preview_sticker_named, packTitle)
}
