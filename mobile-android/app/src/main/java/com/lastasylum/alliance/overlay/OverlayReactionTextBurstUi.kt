package com.lastasylum.alliance.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.DecelerateInterpolator
import android.graphics.drawable.GradientDrawable

internal object OverlayReactionTextBurstUi {

    private const val TEXT_PREVIEW_SP = 13f
    private const val TEXT_MINI_SP = 9f
    private const val TEXT_PREVIEW_LINES_MAX = 2

    fun createTileTextView(
        context: Context,
        message: String,
        tileSizePx: Int,
        mini: Boolean = false,
        textSp: Float? = null,
        maxLines: Int? = null,
    ): TextView = createPreviewMessageTextView(
        context = context,
        message = message,
        maxWidthPx = tileSizePx,
        textSp = textSp ?: if (mini) TEXT_MINI_SP else TEXT_PREVIEW_SP,
        maxLines = maxLines ?: TEXT_PREVIEW_LINES_MAX,
    )

    fun createPreviewMessageTextView(
        context: Context,
        message: String,
        maxWidthPx: Int,
        textSp: Float = TEXT_PREVIEW_SP,
        maxLines: Int = TEXT_PREVIEW_LINES_MAX,
    ): TextView {
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 600, false)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = message
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.parseColor("#E6FDF8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp)
            this.typeface = typeface
            letterSpacing = 0.01f
            setLineSpacing(0f, 1.15f)
            setMaxLines(maxLines)
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#CC000000"))
            maxWidth = maxWidthPx
            val padH = (density * 6).toInt()
            val padV = (density * 4).toInt()
            setPadding(padH, padV, padH, padV)
            alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
            disableOverlayTouchTarget(this)
        }
    }

    fun createMessageTextView(context: Context, message: String, maxWidthPx: Int): TextView {
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 600, false)
        } else {
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        return TextView(context).apply {
            text = message
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.parseColor("#FFFDF8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, OverlayReactionBurstLayout.TEXT_MESSAGE_SP)
            this.typeface = typeface
            letterSpacing = 0.01f
            setLineSpacing(0f, 1.18f)
            maxLines = OverlayReactionBurstLayout.TEXT_LINES_MAX
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(14f, 0f, 4f, Color.parseColor("#E6000000"))
            maxWidth = maxWidthPx
            val padH = (context.resources.displayMetrics.density * 16).toInt()
            val padV = (context.resources.displayMetrics.density * 14).toInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D1524"))
                cornerRadius = context.resources.displayMetrics.density * 16f
                setStroke(
                    (context.resources.displayMetrics.density * 1.2f).toInt().coerceAtLeast(1),
                    Color.parseColor("#55FFFFFF"),
                )
            }
            disableOverlayTouchTarget(this)
        }
    }

    fun playRevealAnimation(messageView: TextView, senderLine: TextView) {
        messageView.alpha = 0f
        messageView.scaleX = 0.96f
        messageView.scaleY = 0.96f
        messageView.translationY = messageView.resources.displayMetrics.density * 10f
        senderLine.alpha = 0f
        senderLine.translationY = senderLine.resources.displayMetrics.density * 6f
        val ease = DecelerateInterpolator(1.4f)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(messageView, View.ALPHA, 0f, 1f).apply {
                    duration = 480
                    interpolator = ease
                },
                ObjectAnimator.ofFloat(messageView, View.SCALE_X, 0.96f, 1f).apply {
                    duration = 520
                    interpolator = ease
                },
                ObjectAnimator.ofFloat(messageView, View.SCALE_Y, 0.96f, 1f).apply {
                    duration = 520
                    interpolator = ease
                },
                ObjectAnimator.ofFloat(messageView, View.TRANSLATION_Y, messageView.translationY, 0f).apply {
                    duration = 480
                    interpolator = ease
                },
                ObjectAnimator.ofFloat(senderLine, View.ALPHA, 0f, OverlayReactionBurstLayout.CAPTION_ALPHA).apply {
                    duration = 360
                    startDelay = 140
                    interpolator = ease
                },
                ObjectAnimator.ofFloat(senderLine, View.TRANSLATION_Y, senderLine.translationY, 0f).apply {
                    duration = 360
                    startDelay = 140
                    interpolator = ease
                },
            )
            start()
        }
    }

    fun createSenderLine(
        context: Context,
        senderLineText: CharSequence,
        maxWidthPx: Int,
    ): TextView =
        TextView(context).apply {
            text = senderLineText
            setTextColor(Color.parseColor("#F5FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            maxWidth = maxWidthPx
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#99000000"))
            setPadding(
                context.resources.displayMetrics.density.times(10).toInt(),
                context.resources.displayMetrics.density.times(5).toInt(),
                context.resources.displayMetrics.density.times(10).toInt(),
                context.resources.displayMetrics.density.times(5).toInt(),
            )
            alpha = OverlayReactionBurstLayout.CAPTION_ALPHA
            disableOverlayTouchTarget(this)
        }
}
