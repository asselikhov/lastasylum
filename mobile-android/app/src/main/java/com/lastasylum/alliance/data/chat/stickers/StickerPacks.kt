package com.lastasylum.alliance.data.chat.stickers

import android.content.Context

/** Registry of built-in sticker packs (wire keys must match server catalog). */
object StickerPacks {
    private val all: List<ChatStickerPack> = listOf(
        ZlobyakaStickerPack,
        ChushuyStickerPack,
        SoidowCatStickerPack,
    )

    private val byKey: Map<String, ChatStickerPack> = all.associateBy { it.packKey }

    fun registeredPacks(): List<ChatStickerPack> = all

    fun forKey(packKey: String): ChatStickerPack? = byKey[packKey.trim()]

    fun parse(text: String): ParsedChatSticker? {
        val trimmed = text.trim()
        for (pack in all) {
            val stem = pack.parseStem(trimmed) ?: continue
            return ParsedChatSticker(pack.packKey, stem, pack)
        }
        return null
    }

    fun enabledPacks(keys: Set<String>): List<ChatStickerPack> =
        keys.mapNotNull { forKey(it) }

    fun listStems(context: Context, packKey: String): List<String> =
        forKey(packKey)?.listStems(context).orEmpty()

    fun assetUriForMessage(text: String): String? =
        parse(text)?.let { parsed -> parsed.pack.assetUriForStem(parsed.stem) }

    fun stemForMessage(text: String): String? = parse(text)?.stem
}
