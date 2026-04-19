package com.lastasylum.alliance.ui.chat

import android.content.Context
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack

object ChatStickerFormat {
    fun humanReadableBody(context: Context, text: String): String {
        return if (ZlobyakaStickerPack.parseStem(text) != null) {
            context.getString(R.string.chat_reply_preview_sticker)
        } else {
            text
        }
    }
}
