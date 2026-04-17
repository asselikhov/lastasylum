package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.lastasylum.alliance.R
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
    private val cardFill = Color.parseColor("#D835285A")
    private val cardStroke = Color.parseColor("#809B7CFF")
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
            cornerRadius = dp(context, 12f)
            setColor(Color.parseColor("#9910121E"))
            setStroke(dp(context, 1f).toInt(), Color.parseColor("#559B7CFF"))
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
            titleText = title,
            titleColor = Color.parseColor("#F4F0FF"),
            bodyText = message.replace("\n", " ").trim(),
            bodyColor = Color.parseColor("#D8D0EC"),
        )
    }

    fun addLine(
        context: Context,
        container: LinearLayout,
        sender: String,
        text: String,
        senderId: String?,
        selfUserId: String?,
    ) {
        val safe = text.replace("\n", " ").trim()
        val preview = if (safe.length > 120) safe.take(120) + "…" else safe
        val displayName = sender.trim().take(22).ifBlank { "—" }
        val initial = displayName.first().uppercaseChar()
        val (accent, bodyMuted) = colorsFor(senderId, displayName, selfUserId)
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
            titleText = displayName,
            titleColor = titleColor,
            bodyText = preview,
            bodyColor = bodyColor,
        )
    }

    private fun addMessageCard(
        context: Context,
        container: LinearLayout,
        avatarLetter: String,
        avatarGradient: IntArray,
        titleText: String,
        titleColor: Int,
        bodyText: String,
        bodyColor: Int,
    ) {
        val avatarSide = dp(context, 36f).toInt()
        val cornerCard = dp(context, 14f)
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

        val titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            text = titleText
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val bodyView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 2f).toInt() }
            text = bodyText
            setTextColor(bodyColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            addView(titleView)
            addView(bodyView)
        }

        val closeSize = dp(context, 36f).toInt()
        val close = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(closeSize, closeSize).apply {
                marginStart = dp(context, 4f).toInt()
            }
            gravity = Gravity.CENTER
            text = "✕"
            contentDescription = context.getString(R.string.overlay_chat_dismiss_cd)
            setTextColor(Color.parseColor("#C4B8DC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(context, 4f).toInt(), 0, dp(context, 4f).toInt(), 0)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 6f).toInt()
            }
            setPadding(
                dp(context, 10f).toInt(),
                dp(context, 8f).toInt(),
                dp(context, 8f).toInt(),
                dp(context, 8f).toInt(),
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerCard
                setColor(cardFill)
                setStroke(dp(context, 1f).toInt(), cardStroke)
            }
            addView(avatar)
            addView(textColumn)
            addView(close)
        }

        close.setOnClickListener {
            (card.parent as? LinearLayout)?.removeView(card)
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
