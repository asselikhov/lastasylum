package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import java.time.Instant
import kotlin.math.abs

/**
 * Полноэкранное окно чата поверх игры: история, ввод и отправка.
 */
data class OverlayHistoryPanelViews(
    val root: FrameLayout,
    val scroll: ScrollView,
    val lines: LinearLayout,
    val input: TextInputEditText,
    val sendButton: FloatingActionButton,
    val statusView: TextView,
)

object OverlayChatHistoryPanel {
    fun create(
        context: Context,
        onClose: () -> Unit,
    ): OverlayHistoryPanelViews {
        val themed = OverlayTickerUi.themedFabContext(context)
        val padH = dp(context, 12f).toInt()
        val padTop = dp(context, 8f).toInt()

        val lines = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding(padH, dp(context, 6f).toInt(), padH, dp(context, 12f).toInt())
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            addView(
                lines,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val header = MaterialCardView(themed).apply {
            radius = dp(context, 0f)
            cardElevation = dp(context, 2f)
            strokeWidth = 0
            setCardBackgroundColor(ColorStateList.valueOf(Color.parseColor("#E6282548")))
            setUseCompatPadding(false)
            isClickable = false
        }

        val headerInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padH, padTop, padH, padTop)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_history_title)
            setTextColor(Color.parseColor("#F4F0FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val close = FloatingActionButton(themed).apply {
            OverlayTickerUi.styleOverlayFab(themed, this, 42f)
            setImageResource(R.drawable.ic_overlay_close)
            contentDescription = context.getString(R.string.overlay_history_close_cd)
            setOnClickListener { onClose() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        headerInner.addView(title)
        headerInner.addView(close)
        header.addView(
            headerInner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val statusView = TextView(context).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#FFB4AB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(padH, dp(context, 4f).toInt(), padH, dp(context, 4f).toInt())
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val inputLayout = TextInputLayout(themed).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            defaultHintTextColor = ColorStateList.valueOf(Color.parseColor("#B8B4C9"))
            setHintTextColor(ColorStateList.valueOf(Color.parseColor("#B8B4C9")))
            hint = context.getString(R.string.overlay_history_input_hint)
            isCounterEnabled = true
            counterMaxLength = 4000
            setBoxStrokeColorStateList(
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        Color.parseColor("#C4B5FD"),
                        Color.parseColor("#668899CC"),
                    ),
                ),
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginEnd = dp(context, 8f).toInt() }
        }

        val input = TextInputEditText(inputLayout.context).apply {
            setTextColor(Color.parseColor("#F4F0FF"))
            setHintTextColor(ColorStateList.valueOf(Color.parseColor("#8B879E")))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 6
            minLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        inputLayout.addView(input)

        val send = FloatingActionButton(themed).apply {
            OverlayTickerUi.styleOverlayFab(themed, this, 52f)
            setImageResource(R.drawable.ic_overlay_send)
            contentDescription = context.getString(R.string.overlay_history_send_cd)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.BOTTOM }
        }

        val composer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(padH, dp(context, 8f).toInt(), padH, dp(context, 10f).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#F0121018"))
            }
            addView(inputLayout)
            addView(send)
        }

        val mainColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ),
            )
            addView(statusView)
            addView(composer, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        val root = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.parseColor("#F0080A10"))
            isClickable = true
            addView(mainColumn)
        }

        ViewCompat.setOnApplyWindowInsetsListener(mainColumn) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        return OverlayHistoryPanelViews(root, scroll, lines, input, send, statusView)
    }

    private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

    fun clear(lines: LinearLayout) {
        lines.removeAllViews()
    }

    fun populate(
        context: Context,
        lines: LinearLayout,
        messages: List<ChatMessage>,
        selfUserId: String?,
        receivedAt: Map<String, Instant>,
        dedupeOut: MutableSet<String>,
    ) {
        clear(lines)
        dedupeOut.clear()
        val sorted = messages.sortedBy { OverlayChatTime.effectiveInstant(it, receivedAt) }
        sorted.forEach { msg ->
            dedupeOut.add(msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey())
            addHistoryRow(context, lines, msg, selfUserId, receivedAt)
        }
    }

    /**
     * Добавить входящее сообщение в конец, если [dedupeIds] ещё не содержит ключ.
     * @return true если строка добавлена.
     */
    fun appendIncomingMessage(
        context: Context,
        lines: LinearLayout,
        msg: ChatMessage,
        selfUserId: String?,
        receivedAt: Map<String, Instant>,
        dedupeIds: MutableSet<String>,
    ): Boolean {
        val key = msg._id?.takeIf { it.isNotBlank() } ?: msg.stableKey()
        if (!dedupeIds.add(key)) return false
        addHistoryRow(context, lines, msg, selfUserId, receivedAt)
        return true
    }

    private fun addHistoryRow(
        context: Context,
        container: LinearLayout,
        msg: ChatMessage,
        selfUserId: String?,
        receivedAt: Map<String, Instant>,
    ) {
        val t = OverlayChatTime.effectiveInstant(msg, receivedAt)
        val timeStr = if (t == Instant.EPOCH) "—" else OverlayChatTime.formatClock(t)
        val nick = chatSenderDisplayWithTag(msg.senderTeamTag, msg.senderUsername).trim()
            .ifBlank { "—" }
        val role = msg.senderRole.trim()
        val isSelf = !selfUserId.isNullOrBlank() && msg.senderId == selfUserId
        val (accent, bodyMuted) = colorsFor(msg.senderId, nick, selfUserId)
        val nickColor = if (isSelf) Color.parseColor("#C4B5FD") else Color.parseColor("#F4F0FF")
        val bodyColor = if (isSelf) Color.parseColor("#E8DFFF") else bodyMuted

        val timeTv = TextView(context).apply {
            text = timeStr
            setTextColor(Color.parseColor("#8B93B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 36f).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 2f).toInt() }
        }

        val nickTv = TextView(context).apply {
            text = nick
            setTextColor(nickColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }

        val roleTv = TextView(context).apply {
            text = role.ifBlank { "" }
            visibility = if (role.isNotBlank()) View.VISIBLE else View.GONE
            setTextColor(Color.parseColor("#9AA3C4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(context, 6f).toInt() }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(timeTv)
            addView(nickTv)
            addView(roleTv)
        }

        val body = TextView(context).apply {
            text = msg.text.trimEnd()
            setTextColor(bodyColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 40
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 4f).toInt() }
        }

        val accentBar = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dp(context, 3f).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply { marginEnd = dp(context, 8f).toInt() }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 2f)
                setColor(accent)
            }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
            addView(topRow)
            addView(body)
        }

        val innerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(
                dp(context, 10f).toInt(),
                dp(context, 10f).toInt(),
                dp(context, 10f).toInt(),
                dp(context, 10f).toInt(),
            )
            addView(accentBar)
            addView(textCol)
        }

        val cardFill = Color.parseColor("#CC35285A")
        val cardStroke = Color.parseColor("#559B7CFF")
        val themed = OverlayTickerUi.themedFabContext(context)
        val row = MaterialCardView(themed).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(context, 8f).toInt()
            }
            radius = dp(context, 16f)
            cardElevation = dp(context, 3f)
            maxCardElevation = dp(context, 6f)
            strokeWidth = dp(context, 1f).toInt().coerceAtLeast(1)
            setStrokeColor(ColorStateList.valueOf(cardStroke))
            setCardBackgroundColor(ColorStateList.valueOf(cardFill))
            setUseCompatPadding(true)
            preventCornerOverlap = false
            isClickable = false
            addView(
                innerRow,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        container.addView(row)
    }

    private val palette: List<Pair<Int, Int>> = listOf(
        Color.parseColor("#FFD54F") to Color.parseColor("#E8EAEF"),
        Color.parseColor("#D4A5FF") to Color.parseColor("#E8DFFF"),
        Color.parseColor("#82B1FF") to Color.parseColor("#DCE7FF"),
        Color.parseColor("#9B7CFF") to Color.parseColor("#DDD4FF"),
        Color.parseColor("#2DD4BF") to Color.parseColor("#B5FFF0"),
        Color.parseColor("#FFB74D") to Color.parseColor("#FFE0B2"),
    )

    private fun colorsFor(
        senderId: String?,
        senderName: String,
        selfUserId: String?,
    ): Pair<Int, Int> {
        if (!selfUserId.isNullOrBlank() && senderId == selfUserId) {
            return Color.parseColor("#C4B5FD") to Color.parseColor("#E8DFFF")
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
