package com.lastasylum.alliance.ui.chat

import android.content.Context
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.StickerPacks

object ChatStickerFormat {
    fun humanReadableBody(context: Context, text: String): String {
        val parsed = StickerPacks.parse(text) ?: return text
        val packTitle = context.getString(parsed.pack.titleRes)
        return context.getString(R.string.chat_reply_preview_sticker_named, packTitle)
    }
}
