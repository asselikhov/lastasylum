package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
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
 * Чекбоксы «Атака»/«Штурм»/«Подкрепление» — одиночный выбор (тап по активному снимает выбор);
 * по умолчанию ничего не выбрано и префикс не добавляется.
 */
class OverlayRaidSharePanel(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    /** commandLabel = подпись выбранного чекбокса или null (ничего не выбрано). */
    private val onSend: (commandLabel: String?, target: RaidShareTarget) -> Unit,
) {
    private data class Command(val label: String)

    private val commands: List<Command> by lazy {
        listOf(
            Command(context.getString(R.string.overlay_cmd_column_attack)),
            Command(context.getString(R.string.overlay_cmd_column_storm)),
            Command(context.getString(R.string.overlay_cmd_column_reinf)),
        )
    }

    private val accent = Color.parseColor("#FF3B82F6")
    private val cardBg = Color.parseColor("#F00F172A")
    private val chipBg = Color.parseColor("#1FFFFFFF")
    private val chipText = Color.parseColor("#FFCBD5E1")
    private val titleColor = Color.parseColor("#FFE2E8F0")
    private val subColor = Color.parseColor("#FF94A3B8")

    private var root: LinearLayout? = null
    private var summaryView: TextView? = null
    private val chipViews = mutableListOf<TextView>()
    private var selectedIndex: Int = -1
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
            selectedIndex = -1
            val view = root ?: buildView().also { root = it }
            summaryView?.text = summaryFor(target)
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
        val pad = dp(14)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(cardBg)
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
            layoutParams = ViewGroup.LayoutParams(
                dp(320),
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_raid_share_title)
            setTextColor(titleColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = TextView(context).apply {
            text = "\u00D7"
            setTextColor(subColor)
            textSize = 22f
            contentDescription = context.getString(R.string.overlay_raid_share_close_cd)
            setPadding(dp(10), dp(2), dp(6), dp(2))
            setOnClickListener { dismiss() }
        }
        header.addView(title)
        header.addView(close)
        container.addView(header)

        summaryView = TextView(context).apply {
            setTextColor(subColor)
            textSize = 13f
            setPadding(0, dp(4), 0, dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        container.addView(summaryView)

        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        chipViews.clear()
        commands.forEachIndexed { index, command ->
            val chip = TextView(context).apply {
                text = command.label
                gravity = Gravity.CENTER
                textSize = 13f
                setPadding(dp(6), dp(9), dp(6), dp(9))
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) lp.leftMargin = dp(8)
                layoutParams = lp
                setOnClickListener {
                    selectedIndex = if (selectedIndex == index) -1 else index
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
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(accent)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(12) }
            setOnClickListener { onSendClicked() }
        }
        container.addView(send)

        syncChips()
        return container
    }

    private fun onSendClicked() {
        val t = target ?: return
        val label = selectedIndex.takeIf { it in commands.indices }?.let { commands[it].label }
        onSend(label, t)
    }

    private fun dismiss() {
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.let { hide(it) }
    }

    private fun syncChips() {
        chipViews.forEachIndexed { index, chip ->
            val selected = index == selectedIndex
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(if (selected) accent else chipBg)
            }
            chip.setTextColor(if (selected) Color.WHITE else chipText)
            chip.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    /** Человеческое описание цели для подзаголовка. */
    private fun summaryFor(t: RaidShareTarget): String {
        val coords = buildString {
            if (t.serverNumber != null) append("S:").append(t.serverNumber).append(" ")
            append(t.x).append(",").append(t.y)
        }
        val head: String? = if (t.isChest) {
            val chest = buildString {
                t.gradeLabel()?.let { append(it) }
                t.stars?.let { s ->
                    if (isNotEmpty()) append(" ")
                    append("\u2605".repeat(s.coerceIn(1, 5)))
                }
                t.playerName?.let { p ->
                    if (isNotEmpty()) append(" \u00B7 ")
                    append(p)
                }
            }
            chest.ifBlank { t.playerName ?: t.name ?: "" }.takeIf { it.isNotBlank() }
        } else {
            t.name?.takeIf { it.isNotBlank() } ?: t.playerName?.takeIf { it.isNotBlank() }
        }
        return if (head.isNullOrBlank()) coords else "$head \u00B7 $coords"
    }
}
