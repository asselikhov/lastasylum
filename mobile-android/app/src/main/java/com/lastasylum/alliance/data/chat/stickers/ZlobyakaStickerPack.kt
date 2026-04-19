package com.lastasylum.alliance.data.chat.stickers

import android.content.Context

/**
 * Built-in sticker pack shipped under [ASSET_FOLDER] as PNG files (512×512 source).
 * Wire format: whole message body is exactly `[[zlobyaka:<file_stem>]]` (no .png suffix).
 * Allowed stems must match the catalog on the server (`zlobyaka-stickers.const.ts`).
 */
object ZlobyakaStickerPack {
    const val PACK_KEY: String = "zlobyaka"
    private const val ASSET_FOLDER: String = "stickerpacks/$PACK_KEY"
    private const val PREFIX: String = "[[${PACK_KEY}:"
    private const val SUFFIX: String = "]]"

    fun encode(fileStem: String): String {
        val stem = fileStem.trim()
        require(stem.isNotEmpty()) { "empty sticker stem" }
        return "$PREFIX$stem$SUFFIX"
    }

    fun parseStem(text: String): String? {
        val t = text.trim()
        if (!t.startsWith(PREFIX) || !t.endsWith(SUFFIX)) return null
        val stem = t.removePrefix(PREFIX).removeSuffix(SUFFIX).trim()
        if (stem.isEmpty()) return null
        if (stem.any { ch -> ch == '[' || ch == ']' || ch == '\n' || ch == '\r' }) return null
        return stem
    }

    fun isPackMessage(text: String): Boolean = parseStem(text) != null

    fun assetUriForStem(stem: String): String = "file:///android_asset/$ASSET_FOLDER/$stem.png"

    fun listSortedStems(context: Context): List<String> {
        val names = context.assets.list(ASSET_FOLDER) ?: return emptyList()
        return names
            .filter { it.endsWith(".png", ignoreCase = true) }
            .map { name -> name.dropLast(4) }
            .sortedWith(naturalOrder())
    }
}
