package com.lastasylum.alliance.data.chat.stickers

import android.content.Context
import androidx.annotation.StringRes

/** Built-in sticker pack shipped under assets/stickerpacks/{packKey}/. */
interface ChatStickerPack {
    val packKey: String
    @get:StringRes
    val titleRes: Int
    fun listStems(context: Context): List<String>
    fun encode(stem: String): String
    fun parseStem(text: String): String?
    fun assetUriForStem(stem: String): String
}

data class ParsedChatSticker(
    val packKey: String,
    val stem: String,
    val pack: ChatStickerPack,
)
