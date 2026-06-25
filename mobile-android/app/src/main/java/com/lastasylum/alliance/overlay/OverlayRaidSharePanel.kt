package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Looper
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.RaidShareTarget

/**
 * Плавающая панель «В рейд» поверх игрового окна выбора канала.
 *
 * Компактное окно [WRAP_CONTENT] сверху по центру: касания вне карточки уходят в игру
 * ([OverlayWindowLayout.popupWindowFlags]), список каналов остаётся доступен.
 * Закрытие — кнопка «В рейд» или закрытие игрового диалога (тап по пустой области).
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
            Command(context.getString(R.string.overlay_cmd_column_attack), Color.parseColor("#FFF43F5E")),
            Command(context.getString(R.string.overlay_cmd_column_storm), Color.parseColor("#FFF59E0B")),
            Command(context.getString(R.string.overlay_raid_cmd_reinf), Color.parseColor("#FF22C55E")),
        )
    }

    private val coordColor = Color.parseColor("#FF7DD3FC")
    private val infoColor = Color.parseColor("#FFE2E8F0")

    private var root: LinearLayout? = null
    private var infoView: TextView? = null
    private var coordsView: TextView? = null
    private val chipViews = mutableListOf<TextView>()
    private val selected = mutableSetOf<Int>()
    private var target: RaidShareTarget? = null
    private var attached = false
    private var attachedWindowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager, target: RaidShareTarget) {
        runOnMain {
            this.target = target
            selected.clear()
            val view = root ?: buildView().also { root = it }
            bindTarget(target)
            syncChips()
            applyVerticalPosition(target.dialogTopPx)
            if (!attached) {
                runCatching { windowManager.addView(view, buildParams().also { layoutParams = it }) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                        logDebug("show ok seq=${target.seq}")
                    }
                    .onFailure { e ->
                        Log.w(TAG, "show addView failed seq=${target.seq}", e)
                    }
            } else if (attachedWindowManager != windowManager) {
                val oldMgr = attachedWindowManager
                runCatching { oldMgr?.removeView(view) }
                attached = false
                attachedWindowManager = null
                show(windowManager, target)
            } else {
                logDebug("show update seq=${target.seq}")
                applyVerticalPosition(target.dialogTopPx)
            }
        }
    }

    fun hide(windowManager: WindowManager) {
        runOnMain {
            val view = root ?: return@runOnMain
            if (!attached) return@runOnMain
            val mgr = attachedWindowManager ?: windowManager
            runCatching { mgr.removeView(view) }
                .onSuccess {
                    attached = false
                    attachedWindowManager = null
                    logDebug("hide ok")
                }
                .onFailure { e -> Log.w(TAG, "hide removeView failed", e) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) block() else mainHandler.post(block)
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
            y = dp(OverlayHudLayout.chatStripTopOffsetDp() + 88)
        }
    }

    /** Разместить панель чуть выше игрового окна выбора канала. */
    private fun applyVerticalPosition(dialogTopPx: Int?) {
        val view = root ?: return
        val wm = attachedWindowManager ?: return
        val params = layoutParams ?: return
        val minTop = dp(OverlayHudLayout.chatStripTopOffsetDp() + 8)
        val gap = dp(8)
        val place = Runnable {
            val panelH = view.height.takeIf { it > 0 } ?: view.measuredHeight
            params.y = if (dialogTopPx != null && dialogTopPx > 0 && panelH > 0) {
                (dialogTopPx - panelH - gap).coerceAtLeast(minTop)
            } else if (dialogTopPx != null && dialogTopPx > 0) {
                (dialogTopPx - gap - dp(120)).coerceAtLeast(minTop)
            } else {
                dp(OverlayHudLayout.chatStripTopOffsetDp() + 88)
            }
            runCatching { wm.updateViewLayout(view, params) }
        }
        if (view.height > 0) {
            place.run()
        } else {
            view.post(place)
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
            )
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

    private fun bindTarget(t: RaidShareTarget) {
        infoView?.text = buildInfoText(t)
        coordsView?.text = buildString {
            append("[")
            if (t.serverNumber != null) append("S:").append(t.serverNumber).append(" ")
            append("X:").append(t.x).append(" Y:").append(t.y)
            append("]")
        }
    }

    private fun buildInfoText(t: RaidShareTarget): CharSequence {
        val builder = SpannableStringBuilder()
        val title = t.titleLine()
        builder.append(title)
        val meta = t.metaPartsForOverlay()
        if (meta.isNotEmpty()) {
            builder.append(" \u00B7 ")
            builder.append(meta.joinToString(" \u00B7 "))
        }
        t.powerLabel()?.let { label ->
            if (builder.isNotEmpty()) builder.append(" \u00B7 ")
            appendStatWithIcon(builder, label, t.powerIcon, R.drawable.ic_overlay_game_power)
        }
        t.killsLabel()?.let { label ->
            if (builder.isNotEmpty()) builder.append(" \u00B7 ")
            appendStatWithIcon(builder, label, t.killsIcon, R.drawable.ic_overlay_game_kills)
        }
        val badge = t.chestGradeStars()
        val gradeColor = gradeColor(t.grade)
        if (badge != null && gradeColor != null) {
            val full = builder.toString()
            val start = full.indexOf(badge)
            if (start >= 0) {
                builder.setSpan(
                    ForegroundColorSpan(gradeColor),
                    start,
                    start + badge.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + badge.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return builder
    }

    private fun appendStatWithIcon(
        builder: SpannableStringBuilder,
        label: String,
        gameSprite: String?,
        fallbackDrawable: Int,
    ) {
        val drawable = context.getDrawable(resolveGameIconDrawable(gameSprite, fallbackDrawable)) ?: return
        val size = dp(14)
        drawable.setBounds(0, 0, size, size)
        val start = builder.length
        builder.append(' ')
        builder.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append(label)
    }

    private fun resolveGameIconDrawable(gameSprite: String?, fallback: Int): Int {
        val key = gameSprite?.lowercase().orEmpty()
        return when {
            key.contains("zhanli") || key.contains("power") || key.contains("shili") -> R.drawable.ic_overlay_game_power
            key.contains("jisha") || key.contains("kill") -> R.drawable.ic_overlay_game_kills
            else -> fallback
        }
    }

    private fun gradeColor(grade: Int?): Int? = when (grade) {
        3 -> Color.parseColor("#FF60A5FA")
        4 -> Color.parseColor("#FFC084FC")
        5 -> Color.parseColor("#FFFBBF24")
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

    private fun syncChips() {
        chipViews.forEachIndexed { index, chip ->
            val command = commands[index]
            val isOn = index in selected
            val mark = if (isOn) "\u2611 " else "\u2610 "
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

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    companion object {
        private const val TAG = "OverlayRaidSharePanel"
    }
}
