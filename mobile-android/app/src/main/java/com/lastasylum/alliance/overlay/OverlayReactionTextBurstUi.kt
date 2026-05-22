package com.lastasylum.alliance.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.view.animation.OvershootInterpolator

internal object OverlayReactionTextBurstUi {

    fun createMessageTextView(context: Context, message: String, maxWidthPx: Int): TextView {
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.SANS_SERIF, 500, false)
        } else {
            Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        return TextView(context).apply {
            text = message
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(Color.parseColor("#FFFFF8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, OverlayReactionBurstLayout.TEXT_MESSAGE_SP)
            this.typeface = typeface
            letterSpacing = 0.03f
            setLineSpacing(0f, 1.12f)
            maxLines = OverlayReactionBurstLayout.TEXT_LINES_MAX
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(10f, 0f, 3f, Color.parseColor("#CC1A0A28"))
            maxWidth = maxWidthPx
            disableOverlayTouchTarget(this)
            post {
                val w = width.toFloat().coerceAtLeast(1f)
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    w,
                    0f,
                    intArrayOf(
                        Color.parseColor("#FFFFF5E6"),
                        Color.parseColor("#FFFFE082"),
                        Color.parseColor("#FFFFF8F0"),
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP,
                )
                invalidate()
            }
        }
    }

    fun playRevealAnimation(messageView: TextView, senderLine: TextView) {
        messageView.alpha = 0f
        messageView.scaleX = 0.82f
        messageView.scaleY = 0.82f
        messageView.translationY = messageView.resources.displayMetrics.density * 28f
        senderLine.alpha = 0f
        senderLine.translationY = senderLine.resources.displayMetrics.density * 12f
        val pop = OvershootInterpolator(1.08f)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(messageView, View.ALPHA, 0f, 1f).apply {
                    duration = 520
                },
                ObjectAnimator.ofFloat(messageView, View.SCALE_X, 0.82f, 1.04f, 1f).apply {
                    duration = 680
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(messageView, View.SCALE_Y, 0.82f, 1.04f, 1f).apply {
                    duration = 680
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(messageView, View.TRANSLATION_Y, messageView.translationY, 0f).apply {
                    duration = 560
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(senderLine, View.ALPHA, 0f, OverlayReactionBurstLayout.CAPTION_ALPHA).apply {
                    duration = 420
                    startDelay = 180
                },
                ObjectAnimator.ofFloat(senderLine, View.TRANSLATION_Y, senderLine.translationY, 0f).apply {
                    duration = 420
                    startDelay = 180
                    interpolator = pop
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
            setTextColor(Color.parseColor("#F2FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            maxWidth = maxWidthPx
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(
                context.resources.displayMetrics.density.times(10).toInt(),
                context.resources.displayMetrics.density.times(6).toInt(),
                context.resources.displayMetrics.density.times(10).toInt(),
                context.resources.displayMetrics.density.times(6).toInt(),
            )
            alpha = OverlayReactionBurstLayout.CAPTION_ALPHA
            disableOverlayTouchTarget(this)
        }
}
