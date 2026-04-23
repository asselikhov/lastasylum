package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import coil.Coil
import coil.request.ImageRequest
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import kotlin.math.abs

/**
 * Лента чата в оверлее: каждое сообщение — «баннер» как в мобильных стратегиях
 * (скруглённая подложка, слева маркер/«аватар», две строки текста, справа ✕).
 * Палитра в духе SquadRelay (тёмно-фиолет + #9B7CFF).
 */
object OverlayChatStripUi {
    private val palette: List<Pair<Int, Int>> = listOf(
        Color.parseColor("#FFD54F") to Color.parseColor("#E8EAEF"),
        Color.parseColor("#D4A5FF") to Color.parseColor("#E8DFFF"),
        Color.parseColor("#82B1FF") to Color.parseColor("#DCE7FF"),
        Color.parseColor("#9B7CFF") to Color.parseColor("#DDD4FF"),
        Color.parseColor("#2DD4BF") to Color.parseColor("#B5FFF0"),
        Color.parseColor("#FFB74D") to Color.parseColor("#FFE0B2"),
    )
    private val selfNameColor = Color.parseColor("#C4B5FD")
    private val selfTextColor = Color.parseColor("#E8DFFF")
    private val cardFill = Color.parseColor("#E8141828")
    private val cardStroke = Color.parseColor("#AA9B7CFF")
    private val noticeAvatarFill = Color.parseColor("#9B7CFF")

    fun styleStripScroll(context: Context, scroll: ScrollView) {
        scroll.isVerticalScrollBarEnabled = true
        scroll.isScrollbarFadingEnabled = false
        scroll.setPadding(
            dp(context, 5f).toInt(),
            dp(context, 5f).toInt(),
            dp(context, 5f).toInt(),
            dp(context, 5f).toInt(),
        )
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 16f)
            setColor(Color.parseColor("#A010121E"))
            setStroke(dp(context, 1.5f).toInt(), Color.parseColor("#669B7CFF"))
        }
        scroll.background = bg
    }

    fun createLinesContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(
                dp(context, 3f).toInt(),
                dp(context, 2f).toInt(),
                dp(context, 3f).toInt(),
                dp(context, 2f).toInt(),
            )
        }
    }

    /** Системное сообщение в том же визуальном стиле (нет комнаты, ошибка истории). */
    fun addNoticeLine(context: Context, container: LinearLayout, message: String) {
        val title = context.getString(R.string.app_name)
        addMessageCard(
            context = context,
            container = container,
            avatarLetter = "S",
            avatarGradient = intArrayOf(
                ColorUtils.blendARGB(noticeAvatarFill, Color.BLACK, 0.25f),
                noticeAvatarFill,
            ),
            teamTag = null,
            titleText = title,
            titleColor = Color.parseColor("#F4F0FF"),
            senderRole = null,
            stickerStem = null,
            bodyText = message.replace("\n", " ").trim(),
            bodyColor = Color.parseColor("#D8D0EC"),
            showDismiss = false,
        )
    }

    fun addLine(
        context: Context,
        container: LinearLayout,
        teamTag: String?,
        username: String,
        text: String,
        senderId: String?,
        senderRole: String?,
        selfUserId: String?,
        showDismiss: Boolean = true,
    ) {
        val stickerStem = ZlobyakaStickerPack.parseStem(text)
        val safe = text.replace("\n", " ").trim()
        val preview = if (stickerStem != null) {
            context.getString(R.string.chat_reply_preview_sticker)
        } else if (safe.length > 160) {
            safe.take(160) + "…"
        } else {
            safe
        }
        val safeName = username.trim().take(22).ifBlank { "—" }
        val initial = safeName.first().uppercaseChar()
        val (accent, bodyMuted) = colorsFor(senderId, safeName, selfUserId)
        val isSelf = !selfUserId.isNullOrBlank() && senderId == selfUserId
        val titleColor = if (isSelf) selfNameColor else Color.parseColor("#F4F0FF")
        val bodyColor = if (isSelf) selfTextColor else bodyMuted
        val avatarColors = intArrayOf(
            ColorUtils.blendARGB(accent, Color.BLACK, 0.35f),
            accent,
        )
        addMessageCard(
            context = context,
            container = container,
            avatarLetter = initial.toString(),
            avatarGradient = avatarColors,
            teamTag = teamTag?.trim()?.takeIf { it.isNotBlank() }?.take(8),
            titleText = safeName,
            titleColor = titleColor,
            senderRole = senderRole?.trim()?.take(12)?.takeIf { it.isNotBlank() },
            stickerStem = stickerStem,
            bodyText = preview,
            bodyColor = bodyColor,
            showDismiss = showDismiss,
        )
    }

    private fun addMessageCard(
        context: Context,
        container: LinearLayout,
        avatarLetter: String,
        avatarGradient: IntArray,
        teamTag: String?,
        titleText: String,
        titleColor: Int,
        senderRole: String?,
        stickerStem: String?,
        bodyText: String,
        bodyColor: Int,
        showDismiss: Boolean = true,
    ) {
        val avatarSide = dp(context, 36f).toInt()
        val cornerCard = dp(context, 18f)
        val cornerAvatar = dp(context, 8f)

        val avatar = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(avatarSide, avatarSide).apply {
                marginEnd = dp(context, 8f).toInt()
            }
            gravity = Gravity.CENTER
            text = avatarLetter
            setTextColor(Color.parseColor("#F8F6FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                avatarGradient,
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerAvatar
            }
        }

        val tagView = TextView(context).apply {
            val t = teamTag.orEmpty()
            visibility = if (t.isNotBlank()) View.VISIBLE else View.GONE
            text = "[$t]"
            setTextColor(Color.parseColor("#C4B5FD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(context, 6f).toInt()
            }
        }

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = titleText
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(tagView)
            addView(titleView)
        }

        val roleView = TextView(context).apply {
            val r = senderRole.orEmpty()
            visibility = if (r.isNotBlank()) View.VISIBLE else View.GONE
            text = r
            setTextColor(Color.parseColor("#9AA3C4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 1f).toInt() }
        }

        val stickerSide = dp(context, 64f).toInt()
        val bodyView: View = if (!stickerStem.isNullOrBlank()) {
            ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(stickerSide, stickerSide).apply {
                    topMargin = dp(context, 3f).toInt()
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                contentDescription = context.getString(R.string.cd_chat_sticker)
                Coil.imageLoader(context).enqueue(
                    ImageRequest.Builder(context)
                        .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
                        .size(160)
                        .target(this)
                        .build(),
                )
            }
        } else {
            TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 3f).toInt() }
                text = bodyText
                setTextColor(bodyColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            }
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            addView(titleRow)
            addView(roleView)
            addView(bodyView)
        }

        val closeSize = dp(context, 36f).toInt()
        val close = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(closeSize, closeSize).apply {
                marginStart = dp(context, 4f).toInt()
            }
            visibility = if (showDismiss) View.VISIBLE else View.GONE
            gravity = Gravity.CENTER
            text = "✕"
            contentDescription = context.getString(R.string.overlay_chat_dismiss_cd)
            setTextColor(Color.parseColor("#C4B8DC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(context, 4f).toInt(), 0, dp(context, 4f).toInt(), 0)
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(context, 10f).toInt(),
                dp(context, 7f).toInt(),
                dp(context, 8f).toInt(),
                dp(context, 7f).toInt(),
            )
            addView(avatar)
            addView(textColumn)
            addView(close)
        }

        val themed = OverlayTickerUi.themedFabContext(context)
        val card = MaterialCardView(themed).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 5f).toInt()
            }
            radius = cornerCard
            cardElevation = dp(context, 5f)
            maxCardElevation = dp(context, 10f)
            strokeWidth = dp(context, 1.25f).toInt().coerceAtLeast(1)
            setStrokeColor(ColorStateList.valueOf(cardStroke))
            setCardBackgroundColor(ColorStateList.valueOf(cardFill))
            setUseCompatPadding(true)
            preventCornerOverlap = false
            isClickable = false
            addView(
                inner,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        if (showDismiss) {
            close.setOnClickListener {
                (card.parent as? LinearLayout)?.removeView(card)
            }
        }

        container.addView(card)
    }

    fun clearLines(container: LinearLayout) {
        container.removeAllViews()
    }

    private fun colorsFor(
        senderId: String?,
        senderName: String,
        selfUserId: String?,
    ): Pair<Int, Int> {
        if (!selfUserId.isNullOrBlank() && senderId == selfUserId) {
            return selfNameColor to selfTextColor
        }
        val key = when {
            !senderId.isNullOrBlank() -> abs(senderId.hashCode())
            else -> abs(senderName.hashCode())
        }
        return palette[key % palette.size]
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        )
    }
}
