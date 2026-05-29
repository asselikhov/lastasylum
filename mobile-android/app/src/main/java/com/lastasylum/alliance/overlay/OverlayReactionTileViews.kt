package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Animatable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.lastasylum.alliance.R

internal fun configureOverlayReactionLottie(lottie: LottieAnimationView, playLoop: Boolean) {
    lottie.repeatCount = if (playLoop) LottieDrawable.INFINITE else 0
    lottie.repeatMode = LottieDrawable.RESTART
    lottie.enableMergePathsForKitKatAndAbove(true)
    lottie.setRenderMode(com.airbnb.lottie.RenderMode.AUTOMATIC)
}

internal fun applyOverlayLottieReactionTint(view: LottieAnimationView, tintHex: String) {
    val color = Color.parseColor(tintHex)
    view.addValueCallback(
        KeyPath("**"),
        LottieProperty.COLOR_FILTER,
        LottieValueCallback(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)),
    )
}

internal fun bindOverlayGifPreview(image: ImageView, context: Context, res: Int, play: Boolean) {
    image.bindOverlayGif(context, res)
    if (!play) {
        val d = image.drawable
        if (d is Animatable && d.isRunning) d.stop()
    }
}

internal fun resumeOverlayReactionTilePreview(icon: ImageView) {
    when (icon) {
        is LottieAnimationView -> {
            configureOverlayReactionLottie(icon, playLoop = true)
            if (!icon.isAnimating) icon.playAnimation()
        }
        else -> {
            val d = icon.drawable
            if (d is Animatable && !d.isRunning) d.start()
        }
    }
}

internal fun createOverlayReactionTileIcon(
    context: Context,
    reaction: OverlayQuickReaction,
    playAnimatedPreview: Boolean,
): ImageView {
    val stickerStem = reaction.stickerAssetStem
    if (stickerStem != null) {
        val packKey = reaction.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
        val bitmapKey = OverlayReactionBitmapCache.cacheKey(packKey, stickerStem)
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setTag(R.id.tag_overlay_reaction_stem, bitmapKey)
            OverlayReactionBitmapCache.get(packKey, stickerStem)?.let { setImageBitmap(it) }
                ?: run {
                    setImageDrawable(null)
                    OverlayReactionBitmapCache.loadAsync(context, packKey, stickerStem) { bmp ->
                        post {
                            if (getTag(R.id.tag_overlay_reaction_stem) != bitmapKey) return@post
                            if (bmp != null) {
                                setImageBitmap(bmp)
                            }
                        }
                    }
                }
        }
    }
    reaction.gifDrawableRes?.let { gifRes ->
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            bindOverlayGifPreview(this, context, gifRes, play = playAnimatedPreview)
        }
    }
    reaction.memeDrawableRes?.let { memeRes ->
        return ImageView(context).apply {
            setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    }
    reaction.lottieRawRes?.let { lottieRes ->
        return LottieAnimationView(context).apply {
            setAnimation(lottieRes)
            reaction.lottieTintHex?.let { applyOverlayLottieReactionTint(this, it) }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            if (playAnimatedPreview) {
                configureOverlayReactionLottie(this, playLoop = true)
                playAnimation()
            } else {
                configureOverlayReactionLottie(this, playLoop = false)
                progress = 0f
            }
        }
    }
    return ImageView(context).apply {
        setImageDrawable(
            AppCompatResources.getDrawable(context, reaction.iconRes)?.mutate()?.also { d ->
                DrawableCompat.setTint(d, Color.parseColor(reaction.tintHex))
            },
        )
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
}

internal fun stopOverlayReactionTileAnimation(icon: ImageView) {
    when (icon) {
        is LottieAnimationView -> {
            icon.cancelAnimation()
            icon.progress = 0f
        }
        else -> {
            val d = icon.drawable
            if (d is Animatable && d.isRunning) d.stop()
        }
    }
}

internal fun FrameLayout.bindOverlayReactionTile(
    reaction: OverlayQuickReaction,
    iconInnerPx: Int,
    playAnimatedPreview: Boolean,
    onPick: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorite: Boolean,
) {
    removeAllViews()
    val icon = createOverlayReactionTileIcon(context, reaction, playAnimatedPreview).apply {
        contentDescription = context.getString(reaction.labelRes)
    }
    isClickable = true
    addView(
        icon,
        FrameLayout.LayoutParams(iconInnerPx, iconInnerPx, Gravity.CENTER),
    )
    val star = android.widget.TextView(context).apply {
        text = if (isFavorite) "★" else "☆"
        setTextColor(Color.parseColor("#FFFFB74D"))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(3, 0, 3, 0)
        isClickable = true
        setOnClickListener { onToggleFavorite() }
    }
    addView(
        star,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END,
        ),
    )
    icon.setOnClickListener { onPick() }
    tag = icon
}
