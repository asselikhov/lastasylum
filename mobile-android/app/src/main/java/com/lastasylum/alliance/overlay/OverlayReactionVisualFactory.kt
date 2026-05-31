package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback

internal class OverlayReactionVisualFactory(
    private val context: Context,
    private val configureLottie: (LottieAnimationView) -> Unit,
) {
    fun createAnimView(reaction: OverlayQuickReaction, maxSidePx: Int, playLottie: Boolean): android.view.View {
        reaction.stickerAssetStem?.let { stem ->
            val packKey = reaction.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
            return nonClippingImage(maxSidePx).also { image ->
                OverlayReactionBitmapCache.loadAsync(context, packKey, stem) { bmp ->
                    if (bmp != null && image.isAttachedToWindow) image.setImageBitmap(bmp)
                }
            }
        }
        reaction.gifDrawableRes?.let { res ->
            return nonClippingImage(maxSidePx).apply { bindOverlayGif(context, res) }
        }
        reaction.memeDrawableRes?.let { res ->
            return nonClippingImage(maxSidePx).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, res))
            }
        }
        reaction.lottieRawRes?.let { res ->
            return LottieAnimationView(context).apply {
                setAnimation(res)
                reaction.lottieTintHex?.let { tint ->
                    addValueCallback(
                        KeyPath("**"),
                        LottieProperty.COLOR_FILTER,
                        LottieValueCallback(
                            PorterDuffColorFilter(Color.parseColor(tint), PorterDuff.Mode.SRC_ATOP),
                        ),
                    )
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (playLottie) configureLottie(this)
                maxWidth = maxSidePx
                maxHeight = maxSidePx
            }
        }
        return nonClippingImage(maxSidePx).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, reaction.iconRes)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor(reaction.tintHex))
                },
            )
        }
    }

    private fun nonClippingImage(maxSidePx: Int): ImageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
            disableOverlayTouchTarget(this)
            maxWidth = maxSidePx
            maxHeight = maxSidePx
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
    lottie.repeatCount = LottieDrawable.INFINITE
    lottie.repeatMode = LottieDrawable.RESTART
    lottie.enableMergePathsForKitKatAndAbove(true)
    lottie.setRenderMode(RenderMode.AUTOMATIC)
    lottie.alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
    disableOverlayTouchTarget(lottie)
    if (!lottie.isAnimating) lottie.playAnimation()
}
