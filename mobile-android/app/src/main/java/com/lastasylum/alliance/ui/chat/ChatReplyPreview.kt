package com.lastasylum.alliance.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessageReplyPreview
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack

@Composable
fun replyPreviewText(reply: ChatMessageReplyPreview): String = replyPreviewText(reply.text)

@Composable
fun replyPreviewText(text: String): String {
    return if (ZlobyakaStickerPack.parseStem(text) != null) {
        stringResource(R.string.chat_reply_preview_sticker)
    } else {
        text
    }
}

@Composable
fun chatMessageSemanticsPreview(text: String): String {
    return if (ZlobyakaStickerPack.parseStem(text) != null) {
        stringResource(R.string.chat_reply_preview_sticker)
    } else {
        text.take(120)
    }
}
