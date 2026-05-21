package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack

/** Время показа входящей реакции на экране (мс). */
const val OVERLAY_REACTION_BURST_VISIBLE_MS = 5_000L

internal enum class OverlayReactionCategory {
    ANIMATIONS,
    MEMES,
    STICKERS,
}

internal data class OverlayQuickReaction(
    val id: String,
    val category: OverlayReactionCategory,
    @DrawableRes val iconRes: Int = R.drawable.ic_overlay_cmd_reaction,
    @StringRes val labelRes: Int,
    val tintHex: String,
    @RawRes val lottieRawRes: Int? = null,
    val lottieTintHex: String? = null,
    @DrawableRes val memeDrawableRes: Int? = null,
    val stickerAssetStem: String? = null,
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
)

internal fun overlayAnimationReactions(): List<OverlayQuickReaction> = listOf(
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
        tintHex = "#FFFF5252",
        lottieRawRes = R.raw.overlay_reaction_plane_heart,
        burstAccentHex = "#CCFF5252",
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

/** Реакции-стикеры (не пак Zlobyaka чата) — контент добавляется отдельно. */
internal fun overlayStickerReactions(): List<OverlayQuickReaction> = emptyList()

internal fun reactionsInCategory(category: OverlayReactionCategory): List<OverlayQuickReaction> =
    when (category) {
        OverlayReactionCategory.ANIMATIONS -> overlayAnimationReactions()
        OverlayReactionCategory.MEMES -> overlayMemeReactions()
        OverlayReactionCategory.STICKERS -> overlayStickerReactions()
    }

internal fun overlayQuickReactionCatalog(): List<OverlayQuickReaction> =
    overlayAnimationReactions() + overlayMemeReactions() + overlayStickerReactions()

internal fun overlayQuickReactionById(reactionId: String): OverlayQuickReaction =
    overlayQuickReactionCatalog().find { it.id == reactionId }
        ?: overlayAnimationReactions().first()

/** Избранные этой вкладки первыми, затем остальные. */
internal fun overlayReactionsForCategory(
    category: OverlayReactionCategory,
    favorites: OverlayReactionFavoritesStore,
): List<OverlayQuickReaction> {
    val inTab = reactionsInCategory(category)
    if (inTab.isEmpty()) return emptyList()
    val favIds = favorites.favoriteIds()
    val (fav, rest) = inTab.partition { it.id in favIds }
    return fav.sortedBy { it.id } + rest.sortedBy { it.id }
}

internal fun loadStickerReactionBitmap(context: Context, stem: String) =
    runCatching {
        context.assets.open("stickerpacks/${ZlobyakaStickerPack.PACK_KEY}/$stem.png").use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
