package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.RaidShareTarget

/**
 * Плавающая панель «В рейд» поверх игрового окна выбора канала.
 *
 * Окно `WRAP_CONTENT` сверху по центру: касания по карточке ловим, всё вне карточки уходит в игру
 * ([OverlayWindowLayout.popupWindowFlags]). Поэтому штатный список каналов игры остаётся доступен,
 * а тап вне игрового окна (в т.ч. по нашей кнопке) закрывает его — данные цели мы уже получили.
 *
 * Чекбоксы «Атака»/«Штурм»/«Подкр.» — одиночный выбор (галочка только на одном, повторный тап снимает);
 * по умолчанию ничего не отмечено и префикс не добавляется.
 */
class OverlayRaidSharePanel(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    /** commandLabel = подпись отмеченного чекбокса или null (ничего не отмечено). */
    private val onSend: (commandLabel: String?, target: RaidShareTarget) -> Unit,
) {
    private data class Command(val label: String, val color: Int)

    private val commands: List<Command> by lazy {
        listOf(
            Command(context.getString(R.string.overlay_cmd_column_attack), Color.parseColor("#FFF43F5E")), // красный
            Command(context.getString(R.string.overlay_cmd_column_storm), Color.parseColor("#FFF59E0B")), // янтарный
            Command(context.getString(R.string.overlay_raid_cmd_reinf), Color.parseColor("#FF22C55E")), // зелёный
        )
    }

    private val accent = Color.parseColor("#FF3B82F6")
    private val coordColor = Color.parseColor("#FF7DD3FC")
    private val titleColor = Color.parseColor("#FFF1F5F9")
    private val infoColor = Color.parseColor("#FFE2E8F0")
    private val subColor = Color.parseColor("#FF94A3B8")

    private var root: LinearLayout? = null
    private var infoView: TextView? = null
    private var coordsView: TextView? = null
    private val chipViews = mutableListOf<TextView>()
    private val selected = mutableSetOf<Int>()
    private var target: RaidShareTarget? = null
    private var attached = false

    /** Подстраховка: если сигнал закрытия панели игры будет пропущен — прячем сами. */
    private val autoHideMs = 25_000L
    private val autoHideRunnable = Runnable {
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.let { hide(it) }
    }

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager, target: RaidShareTarget) {
        mainHandler.post {
            this.target = target
            selected.clear()
            val view = root ?: buildView().also { root = it }
            bindTarget(target)
            syncChips()
            if (!attached) {
                runCatching { windowManager.addView(view, buildParams()) }
                    .onSuccess { attached = true }
            }
            mainHandler.removeCallbacks(autoHideRunnable)
            mainHandler.postDelayed(autoHideRunnable, autoHideMs)
        }
    }

    fun hide(windowManager: WindowManager) {
        mainHandler.post {
            mainHandler.removeCallbacks(autoHideRunnable)
            val view = root ?: return@post
            if (attached) {
                runCatching { windowManager.removeView(view) }
                attached = false
            }
        }
    }

    private fun buildParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(40)
        }
    }

    private fun buildView(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F51E293B"), Color.parseColor("#F50B1220")),
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#3360A5FA"))
            }
            layoutParams = ViewGroup.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // Заголовок: цветная точка-акцент + название + крестик.
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) }
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_raid_share_title)
            setTextColor(titleColor)
            textSize = 15f
            letterSpacing = 0.02f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = TextView(context).apply {
            text = "\u00D7"
            setTextColor(subColor)
            textSize = 20f
            contentDescription = context.getString(R.string.overlay_raid_share_close_cd)
            setPadding(dp(10), dp(0), dp(4), dp(2))
            setOnClickListener { dismiss() }
        }
        header.addView(dot)
        header.addView(title)
        header.addView(close)
        container.addView(header)

        // Карточка цели: имя/инфо + координаты акцентным цветом.
        val infoCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1FFFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
        }
        infoView = TextView(context).apply {
            setTextColor(infoColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        coordsView = TextView(context).apply {
            setTextColor(coordColor)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        }
        infoCard.addView(infoView)
        infoCard.addView(coordsView)
        container.addView(infoCard)

        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        chipViews.clear()
        commands.forEachIndexed { index, command ->
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 12.5f
                setPadding(dp(4), dp(8), dp(4), dp(8))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.leftMargin = dp(7)
                layoutParams = lp
                setOnClickListener {
                    // Одиночный выбор: отметка одного снимает остальные; повторный тап снимает галочку.
                    if (index in selected) {
                        selected.clear()
                    } else {
                        selected.clear()
                        selected.add(index)
                    }
                    syncChips()
                }
            }
            chipViews.add(chip)
            chipRow.addView(chip)
        }
        container.addView(chipRow)

        val send = TextView(context).apply {
            text = context.getString(R.string.overlay_raid_share_send)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF3B82F6"), Color.parseColor("#FF6366F1")),
            ).apply { cornerRadius = dp(11).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
            setOnClickListener { onSendClicked() }
        }
        container.addView(send)

        syncChips()
        return container
    }

    /** Заполняет карточку цели: имя/инфо (строка 1) и координаты (строка 2). */
    private fun bindTarget(t: RaidShareTarget) {
        val info = (listOf(t.titleLine()) + t.metaParts()).joinToString(" \u00B7 ")
        val badge = t.chestGradeStars()
        val gradeColor = gradeColor(t.grade)
        infoView?.text = if (badge != null && gradeColor != null) {
            SpannableString(info).apply {
                val start = info.indexOf(badge)
                if (start >= 0) {
                    setSpan(
                        ForegroundColorSpan(gradeColor),
                        start,
                        start + badge.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        start + badge.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        } else {
            info
        }
        coordsView?.text = buildString {
            append("[")
            if (t.serverNumber != null) append("S:").append(t.serverNumber).append(" ")
            append("X:").append(t.x).append(" Y:").append(t.y)
            append("]")
        }
    }

    /** Цвет грейда сундука: SR — синий, SSR — фиолетовый, UR — золотой. */
    private fun gradeColor(grade: Int?): Int? = when (grade) {
        3 -> Color.parseColor("#FF60A5FA") // SR
        4 -> Color.parseColor("#FFC084FC") // SSR
        5 -> Color.parseColor("#FFFBBF24") // UR
        else -> null
    }

    private fun onSendClicked() {
        val t = target ?: return
        val label = commands.indices
            .filter { it in selected }
            .joinToString(", ") { commands[it].label }
            .takeIf { it.isNotEmpty() }
        onSend(label, t)
    }

    private fun dismiss() {
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.let { hide(it) }
    }

    private fun syncChips() {
        chipViews.forEachIndexed { index, chip ->
            val command = commands[index]
            val isOn = index in selected
            val mark = if (isOn) "\u2611 " else "\u2610 " // ☑ / ☐
            chip.text = mark + command.label
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                if (isOn) {
                    setColor(command.color)
                } else {
                    setColor(withAlpha(command.color, 0x24))
                    setStroke(dp(1), withAlpha(command.color, 0x80))
                }
            }
            chip.setTextColor(if (isOn) Color.WHITE else command.color)
            chip.typeface = if (isOn) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)
}
