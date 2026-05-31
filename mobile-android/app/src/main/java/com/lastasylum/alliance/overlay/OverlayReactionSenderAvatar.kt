package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import coil3.load
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lastasylum.alliance.ui.util.telegramAvatarUrl

internal object OverlayReactionSenderAvatar {
    private const val AVATAR_DP = 22

    fun create(
        context: Context,
        displayName: String,
        fromUserId: String,
        dp: (Int) -> Int,
    ): FrameLayout {
        val side = dp(AVATAR_DP)
        val safeName = displayName.trim().take(22).ifBlank { "—" }
        val initial = safeName.first().uppercaseChar()
        val accent = accentColorFor(fromUserId, safeName)
        val gradient = intArrayOf(
            ColorUtils.blendARGB(accent, Color.BLACK, 0.35f),
            accent,
        )
        val telegram = OverlayTeamContextCache.memberTelegramUsername(fromUserId)
        val avatarUrl = telegramAvatarUrl(telegram)

        val image = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = if (avatarUrl != null) View.VISIBLE else View.GONE
            disableOverlayTouchTarget(this)
        }
        val initialView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.CENTER
            text = initial.toString()
            setTextColor(Color.parseColor("#F8F6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                gradient,
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
            }
            disableOverlayTouchTarget(this)
        }
        return FrameLayout(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(side, side).apply {
                marginEnd = dp(6)
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            addView(image)
            addView(initialView)
            if (avatarUrl != null) {
                image.load(avatarUrl) {
                    size(96)
                    listener(
                        object : ImageRequest.Listener {
                            override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                                initialView.visibility = View.GONE
                            }

                            override fun onError(request: ImageRequest, result: ErrorResult) {
                                initialView.visibility = View.VISIBLE
                            }
                        },
                    )
                }
            }
            disableOverlayTouchTarget(this)
        }
    }

    private fun accentColorFor(userId: String, displayName: String): Int {
        val seed = (userId.hashCode() xor displayName.hashCode())
        val hue = (seed and 0xFFFF) % 360
        return Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.82f))
    }
}
