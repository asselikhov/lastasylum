package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.lastasylum.alliance.R

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

internal fun overlayStickerReactions(context: Context): List<OverlayQuickReaction> {
    val stems = OverlayReactionBitmapCache.listOverlayStickerStems(context)
        .ifEmpty { overlayStickerAssetStemsFallback.toList() }
    return stems.mapIndexed { index, stem ->
        OverlayQuickReaction(
            id = "sticker_%02d".format(index + 1),
            category = OverlayReactionCategory.STICKERS,
            labelRes = R.string.overlay_reaction_sticker_cd,
            tintHex = "#FFE8F0FF",
            stickerAssetStem = stem,
            burstAccentHex = "#CC90A4AE",
        )
    }
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
    overlayAnimationReactions() + overlayMemeReactions() + overlayStickerReactions(context)

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
    return overlayQuickReactionCatalog(context).find { it.id == reactionId }
        ?: overlayAnimationReactions().first()
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

internal fun loadStickerReactionBitmap(context: Context, stem: String): android.graphics.Bitmap? =
    OverlayReactionBitmapCache.loadSync(context, stem)
