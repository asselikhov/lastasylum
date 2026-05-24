package com.lastasylum.alliance.data.chat.stickers

import android.content.Context
import com.lastasylum.alliance.R

/**
 * Built-in sticker pack «Дракончик Чушуй» under [ASSET_FOLDER] as PNG files.
 * Wire format: whole message body is exactly `[[chushuy:<file_stem>]]` (no .png suffix).
 * Allowed stems must match the catalog on the server (`chushuy-stickers.const.ts`).
 */
object ChushuyStickerPack : ChatStickerPack {
    const val PACK_KEY: String = "chushuy"
    override val packKey: String = PACK_KEY
    override val titleRes: Int = R.string.chat_stickers_pack_chushuy
    private const val ASSET_FOLDER: String = "stickerpacks/$PACK_KEY"
    private const val PREFIX: String = "[[${PACK_KEY}:"
    private const val SUFFIX: String = "]]"

    @Volatile
    private var sortedStemsCache: List<String>? = null

    override fun encode(fileStem: String): String {
        val stem = fileStem.trim()
        require(stem.isNotEmpty()) { "empty sticker stem" }
        return "$PREFIX$stem$SUFFIX"
    }

    override fun parseStem(text: String): String? {
        val t = text.trim()
        if (!t.startsWith(PREFIX) || !t.endsWith(SUFFIX)) return null
        val stem = t.removePrefix(PREFIX).removeSuffix(SUFFIX).trim()
        if (stem.isEmpty()) return null
        if (stem.any { ch -> ch == '[' || ch == ']' || ch == '\n' || ch == '\r' }) return null
        return stem
    }

    override fun assetUriForStem(stem: String): String = "file:///android_asset/$ASSET_FOLDER/$stem.png"

    override fun listStems(context: Context): List<String> = listSortedStems(context)

    fun listSortedStems(context: Context): List<String> {
        sortedStemsCache?.let { return it }
        return synchronized(this) {
            sortedStemsCache?.let { return@synchronized it }
            val names = context.applicationContext.assets.list(ASSET_FOLDER) ?: emptyArray()
            val list = names
                .filter { it.endsWith(".png", ignoreCase = true) }
                .map { name -> name.dropLast(4) }
                .sortedWith(naturalOrder())
            sortedStemsCache = list
            list
        }
    }
}
