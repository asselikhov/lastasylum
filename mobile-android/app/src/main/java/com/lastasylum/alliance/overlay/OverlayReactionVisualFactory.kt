package com.lastasylum.alliance.overlay

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.airbnb.lottie.LottieAnimationView

internal class OverlayReactionVisualFactory(
    private val context: Context,
    private val configureLottie: (LottieAnimationView) -> Unit,
) {
    fun createAnimView(reaction: OverlayQuickReaction, maxSidePx: Int, playLottie: Boolean): View {
        preloadStickerIfNeeded(reaction)
        val icon = createOverlayReactionTileIcon(context, reaction, playAnimatedPreview = playLottie)
        when (icon) {
            is LottieAnimationView -> {
                icon.layoutParams = FrameLayout.LayoutParams(maxSidePx, maxSidePx, Gravity.CENTER)
                if (playLottie) {
                    configureLottie(icon)
                    icon.post {
                        if (icon.isAttachedToWindow && !icon.isAnimating) {
                            icon.playAnimation()
                        }
                    }
                }
            }
            else -> {
                icon.apply {
                    layoutParams = FrameLayout.LayoutParams(maxSidePx, maxSidePx, Gravity.CENTER)
                    alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
                    maxWidth = maxSidePx
                    maxHeight = maxSidePx
                }
            }
        }
        disableOverlayTouchTarget(icon)
        return icon
    }

    private fun preloadStickerIfNeeded(reaction: OverlayQuickReaction) {
        val stem = reaction.stickerAssetStem ?: return
        val packKey = reaction.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
        OverlayReactionBitmapCache.preloadSticker(context, packKey, stem)
        OverlayReactionBitmapCache.loadSync(context, packKey, stem)
    }
}

internal fun View.findLottieInSubtree(): LottieAnimationView? {
    if (this is LottieAnimationView) return this
    if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) {
            val found = getChildAt(i).findLottieInSubtree()
            if (found != null) return found
        }
    }
    return null
}

internal fun configureBurstLottie(lottie: LottieAnimationView) {
    lottie.repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
    lottie.repeatMode = com.airbnb.lottie.LottieDrawable.RESTART
    lottie.enableMergePathsForKitKatAndAbove(true)
    lottie.setRenderMode(com.airbnb.lottie.RenderMode.AUTOMATIC)
    lottie.alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
    disableOverlayTouchTarget(lottie)
    if (!lottie.isAnimating) lottie.playAnimation()
}
