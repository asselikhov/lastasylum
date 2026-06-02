package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ChatStickerPack
import com.lastasylum.alliance.data.chat.stickers.StickerPacks

/** Asset pack for overlay-only sticker reactions (not chat Zlobyaka). */
internal const val OVERLAY_REACTION_STICKER_PACK = "overlay"

/** Время показа входящей реакции на экране (мс). */
const val OVERLAY_REACTION_BURST_VISIBLE_MS = 5_000L

internal enum class OverlayReactionCategory {
    ANIMATIONS,
    MEMES,
    STICKERS,
    TEXT,
}

internal data class OverlayQuickReaction(
    val id: String,
    val category: OverlayReactionCategory,
    @DrawableRes val iconRes: Int = R.drawable.ic_overlay_cmd_reaction,
    @StringRes val labelRes: Int,
    val tintHex: String,
    @RawRes val lottieRawRes: Int? = null,
    val lottieTintHex: String? = null,
    @DrawableRes val gifDrawableRes: Int? = null,
    @DrawableRes val memeDrawableRes: Int? = null,
    val stickerAssetStem: String? = null,
    /** Папка assets/stickerpacks/{packKey}/ для [stickerAssetStem]. */
    val stickerPackKey: String? = null,
    /** Для [OVERLAY_TEXT_REACTION_PREFIX] — текст вспышки (не в каталоге плиток). */
    val textPayload: String? = null,
    val burstAccentHex: String = "#CCFF5252",
)

private val overlayMemeDrawableIds = intArrayOf(
    R.drawable.overlay_meme_01,
    R.drawable.overlay_meme_02,
    R.drawable.overlay_meme_03,
    R.drawable.overlay_meme_04,
    R.drawable.overlay_meme_05,
    R.drawable.overlay_meme_06,
    R.drawable.overlay_meme_07,
    R.drawable.overlay_meme_08,
    R.drawable.overlay_meme_09,
    R.drawable.overlay_meme_10,
    R.drawable.overlay_meme_11,
    R.drawable.overlay_meme_12,
    R.drawable.overlay_meme_13,
    R.drawable.overlay_meme_14,
    R.drawable.overlay_meme_15,
    R.drawable.overlay_meme_16,
    R.drawable.overlay_meme_17,
    R.drawable.overlay_meme_18,
    R.drawable.overlay_meme_19,
    R.drawable.overlay_meme_20,
    R.drawable.overlay_meme_21,
    R.drawable.overlay_meme_22,
    R.drawable.overlay_meme_23,
    R.drawable.overlay_meme_24,
    R.drawable.overlay_meme_25,
    R.drawable.overlay_meme_26,
    R.drawable.overlay_meme_27,
    R.drawable.overlay_meme_28,
    R.drawable.overlay_meme_29,
    R.drawable.overlay_meme_30,
    R.drawable.overlay_meme_31,
    R.drawable.overlay_meme_32,
    R.drawable.overlay_meme_33,
    R.drawable.overlay_meme_34,
    R.drawable.overlay_meme_35,
    R.drawable.overlay_meme_36,
    R.drawable.overlay_meme_37,
    R.drawable.overlay_meme_38,
    R.drawable.overlay_meme_39,
)

private val overlayStickerAssetStemsFallback = arrayOf(
    "overlay_sticker_01",
    "overlay_sticker_02",
    "overlay_sticker_03",
)

private val overlayGifDrawableIds = intArrayOf(
    R.drawable.overlay_reaction_gif_01,
    R.drawable.overlay_reaction_gif_02,
    R.drawable.overlay_reaction_gif_03,
    R.drawable.overlay_reaction_gif_04,
    R.drawable.overlay_reaction_gif_05,
    R.drawable.overlay_reaction_gif_06,
    R.drawable.overlay_reaction_gif_07,
    R.drawable.overlay_reaction_gif_08,
    R.drawable.overlay_reaction_gif_09,
    R.drawable.overlay_reaction_gif_10,
    R.drawable.overlay_reaction_gif_11,
    R.drawable.overlay_reaction_gif_12,
    R.drawable.overlay_reaction_gif_13,
    R.drawable.overlay_reaction_gif_14,
    R.drawable.overlay_reaction_gif_15,
    R.drawable.overlay_reaction_gif_16,
    R.drawable.overlay_reaction_gif_17,
    R.drawable.overlay_reaction_gif_18,
    R.drawable.overlay_reaction_gif_19,
    R.drawable.overlay_reaction_gif_20,
    R.drawable.overlay_reaction_gif_21,
    R.drawable.overlay_reaction_gif_22,
    R.drawable.overlay_reaction_gif_23,
    R.drawable.overlay_reaction_gif_24,
    R.drawable.overlay_reaction_gif_25,
    R.drawable.overlay_reaction_gif_26,
    R.drawable.overlay_reaction_gif_27,
    R.drawable.overlay_reaction_gif_28,
    R.drawable.overlay_reaction_gif_29,
    R.drawable.overlay_reaction_gif_30,
    R.drawable.overlay_reaction_gif_31,
    R.drawable.overlay_reaction_gif_32,
    R.drawable.overlay_reaction_gif_33,
    R.drawable.overlay_reaction_gif_34,
    R.drawable.overlay_reaction_gif_35,
    R.drawable.overlay_reaction_gif_36,
    R.drawable.overlay_reaction_gif_37,
    R.drawable.overlay_reaction_gif_38,
    R.drawable.overlay_reaction_gif_39,
    R.drawable.overlay_reaction_gif_40,
    R.drawable.overlay_reaction_gif_41,
    R.drawable.overlay_reaction_gif_42,
    R.drawable.overlay_reaction_gif_43,
    R.drawable.overlay_reaction_gif_44,
    R.drawable.overlay_reaction_gif_45,
)

private val overlayGifReactionById: Map<String, Int> = buildMap {
    overlayGifDrawableIds.forEachIndexed { index, drawableRes ->
        put("gif_%02d".format(index + 1), drawableRes)
    }
}

private fun overlayLottieAnimationReactions(): List<OverlayQuickReaction> = listOf(
    OverlayQuickReaction(
        id = "heart",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_heart_cd,
        tintHex = "#FFFF5252",
        lottieRawRes = R.raw.overlay_reaction_heart,
        lottieTintHex = "#FFFF5252",
        burstAccentHex = "#CCFF5252",
    ),
    OverlayQuickReaction(
        id = "doggie",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_doggie_cd,
        tintHex = "#FFF9C44F",
        lottieRawRes = R.raw.overlay_reaction_doggie,
        burstAccentHex = "#CCF9C44F",
    ),
    OverlayQuickReaction(
        id = "wumpus_angry",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_wumpus_angry_cd,
        tintHex = "#FF7C4DFF",
        lottieRawRes = R.raw.overlay_reaction_wumpus_angry,
        burstAccentHex = "#CC7C4DFF",
    ),
    OverlayQuickReaction(
        id = "crying_smoothymon",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_crying_smoothymon_cd,
        tintHex = "#FF4FC3F7",
        lottieRawRes = R.raw.overlay_reaction_crying_smoothymon,
        burstAccentHex = "#CC4FC3F7",
    ),
    OverlayQuickReaction(
        id = "plane_heart",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_plane_heart_cd,
        tintHex = "#FF00FF88",
        lottieRawRes = R.raw.overlay_reaction_plane_heart,
        lottieTintHex = "#FF00FF88",
        burstAccentHex = "#CC00FF88",
    ),
    OverlayQuickReaction(
        id = "cat_love",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_cat_love_cd,
        tintHex = "#FFFF8A80",
        lottieRawRes = R.raw.overlay_reaction_cat_love,
        burstAccentHex = "#CCFF8A80",
    ),
    OverlayQuickReaction(
        id = "cat_playing",
        category = OverlayReactionCategory.ANIMATIONS,
        labelRes = R.string.overlay_reaction_cat_playing_cd,
        tintHex = "#FFFFB74D",
        lottieRawRes = R.raw.overlay_reaction_cat_playing,
        burstAccentHex = "#CCFFB74D",
    ),
)

internal fun overlayAnimationReactions(): List<OverlayQuickReaction> =
    overlayLottieAnimationReactions() +
        overlayGifDrawableIds.mapIndexed { index, drawableRes ->
            val num = index + 1
            OverlayQuickReaction(
                id = "gif_%02d".format(num),
                category = OverlayReactionCategory.ANIMATIONS,
                labelRes = R.string.overlay_reaction_gif_cd,
                tintHex = "#FFE8F0FF",
                gifDrawableRes = drawableRes,
                burstAccentHex = "#CC90A4AE",
            )
        }

internal fun overlayMemeReactions(): List<OverlayQuickReaction> =
    overlayMemeDrawableIds.mapIndexed { index, drawableRes ->
        val num = index + 1
        OverlayQuickReaction(
            id = "meme_%02d".format(num),
            category = OverlayReactionCategory.MEMES,
            labelRes = R.string.overlay_reaction_meme_cd,
            tintHex = "#FFE8F0FF",
            memeDrawableRes = drawableRes,
            burstAccentHex = "#CC90A4AE",
        )
    }

internal data class OverlayStickerPackTab(
    val packKey: String,
    @StringRes val titleRes: Int,
)

private const val CHAT_STICKER_REACTION_ID_PREFIX = "sticker/"

internal fun encodeChatStickerReactionId(packKey: String, stem: String): String =
    "$CHAT_STICKER_REACTION_ID_PREFIX$packKey/$stem"

internal fun isStickerOrGifReactionId(reactionId: String): Boolean {
    if (decodeChatStickerReactionId(reactionId) != null) return true
    if (reactionId.startsWith("sticker_")) return true
    return reactionId.startsWith("gif_")
}

internal fun decodeChatStickerReactionId(reactionId: String): Pair<String, String>? {
    if (!reactionId.startsWith(CHAT_STICKER_REACTION_ID_PREFIX)) return null
    val rest = reactionId.removePrefix(CHAT_STICKER_REACTION_ID_PREFIX)
    val slash = rest.indexOf('/')
    if (slash <= 0 || slash >= rest.lastIndex) return null
    val packKey = rest.substring(0, slash).trim()
    val stem = rest.substring(slash + 1).trim()
    if (packKey.isEmpty() || stem.isEmpty()) return null
    return packKey to stem
}

internal fun overlayStickerPackTabs(enabledKeys: Set<String>): List<OverlayStickerPackTab> {
    val misc = OverlayStickerPackTab(
        packKey = OVERLAY_REACTION_STICKER_PACK,
        titleRes = R.string.overlay_reactions_sticker_pack_misc,
    )
    val chat = StickerPacks.enabledPacks(enabledKeys).map { pack ->
        OverlayStickerPackTab(packKey = pack.packKey, titleRes = pack.titleRes)
    }
    return listOf(misc) + chat
}

internal fun overlayMiscStickerReactions(context: Context): List<OverlayQuickReaction> {
    val stems = OverlayReactionBitmapCache.listOverlayStickerStems(context)
        .ifEmpty { overlayStickerAssetStemsFallback.toList() }
    return stems.mapIndexed { index, stem ->
        OverlayQuickReaction(
            id = "sticker_%02d".format(index + 1),
            category = OverlayReactionCategory.STICKERS,
            labelRes = R.string.overlay_reaction_sticker_cd,
            tintHex = "#FFE8F0FF",
            stickerAssetStem = stem,
            stickerPackKey = OVERLAY_REACTION_STICKER_PACK,
            burstAccentHex = "#CC90A4AE",
        )
    }
}

internal fun overlayChatStickerReactions(
    context: Context,
    pack: ChatStickerPack,
): List<OverlayQuickReaction> =
    pack.listStems(context).map { stem ->
        OverlayQuickReaction(
            id = encodeChatStickerReactionId(pack.packKey, stem),
            category = OverlayReactionCategory.STICKERS,
            labelRes = R.string.overlay_reaction_sticker_cd,
            tintHex = "#FFE8F0FF",
            stickerAssetStem = stem,
            stickerPackKey = pack.packKey,
            burstAccentHex = "#CC90A4AE",
        )
    }

internal fun overlayStickerReactions(context: Context): List<OverlayQuickReaction> =
    overlayMiscStickerReactions(context)

internal fun overlayStickerReactionsForPack(
    context: Context,
    packKey: String,
    favorites: OverlayReactionFavoritesStore,
): List<OverlayQuickReaction> {
    val inTab = when (packKey) {
        OVERLAY_REACTION_STICKER_PACK -> overlayMiscStickerReactions(context)
        else -> StickerPacks.forKey(packKey)?.let { overlayChatStickerReactions(context, it) }.orEmpty()
    }
    if (inTab.isEmpty()) return emptyList()
    val favIds = favorites.favoriteIds()
    val (fav, rest) = inTab.partition { it.id in favIds }
    return fav.sortedBy { it.id } + rest.sortedBy { it.id }
}

internal fun reactionsInCategory(
    category: OverlayReactionCategory,
    context: Context,
): List<OverlayQuickReaction> =
    when (category) {
        OverlayReactionCategory.ANIMATIONS -> overlayAnimationReactions()
        OverlayReactionCategory.MEMES -> overlayMemeReactions()
        OverlayReactionCategory.STICKERS -> overlayStickerReactions(context)
        OverlayReactionCategory.TEXT -> emptyList()
    }

internal fun overlayQuickReactionCatalog(context: Context): List<OverlayQuickReaction> =
    overlayAnimationReactions() +
        overlayMemeReactions() +
        overlayMiscStickerReactions(context)

internal fun overlayQuickReactionById(context: Context, reactionId: String): OverlayQuickReaction {
    decodeTextReactionId(reactionId)?.let { text ->
        return OverlayQuickReaction(
            id = reactionId,
            category = OverlayReactionCategory.TEXT,
            labelRes = R.string.overlay_reactions_text_cd,
            tintHex = "#FFFFF8F0",
            textPayload = text,
            burstAccentHex = "#CCFFE082",
        )
    }
    overlayGifReactionById[reactionId]?.let { drawableRes ->
        return OverlayQuickReaction(
            id = reactionId,
            category = OverlayReactionCategory.ANIMATIONS,
            labelRes = R.string.overlay_reaction_gif_cd,
            tintHex = "#FFE8F0FF",
            gifDrawableRes = drawableRes,
            burstAccentHex = "#CC90A4AE",
        )
    }
    decodeChatStickerReactionId(reactionId)?.let { (packKey, stem) ->
        return OverlayQuickReaction(
            id = reactionId,
            category = OverlayReactionCategory.STICKERS,
            labelRes = R.string.overlay_reaction_sticker_cd,
            tintHex = "#FFE8F0FF",
            stickerAssetStem = stem,
            stickerPackKey = packKey,
            burstAccentHex = "#CC90A4AE",
        )
    }
    return overlayQuickReactionCatalog(context).find { it.id == reactionId }
        ?: overlayAnimationReactions().first()
}

internal fun overlayReactionSupportsAnimatedPreview(context: Context, reactionId: String): Boolean {
    val reaction = overlayQuickReactionById(context, reactionId)
    return reaction.lottieRawRes != null || reaction.gifDrawableRes != null
}

/** Избранные этой вкладки первыми, затем остальные. */
internal fun overlayReactionsForCategory(
    category: OverlayReactionCategory,
    favorites: OverlayReactionFavoritesStore,
    context: Context,
): List<OverlayQuickReaction> {
    val inTab = reactionsInCategory(category, context)
    if (inTab.isEmpty()) return emptyList()
    val favIds = favorites.favoriteIds()
    val (fav, rest) = inTab.partition { it.id in favIds }
    return fav.sortedBy { it.id } + rest.sortedBy { it.id }
}
