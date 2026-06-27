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
import android.view.View
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
    /** chipLabel — для чекбокса (допустимо сокращение), messageLabel — для текста сообщения (полное). */
    private data class Command(val chipLabel: String, val messageLabel: String, val color: Int)

    private val commands: List<Command> by lazy {
        listOf(
            Command(
                chipLabel = context.getString(R.string.overlay_cmd_column_attack),
                messageLabel = context.getString(com.lastasylum.alliance.game.RaidCommandStyle.ATTACK.fullLabelRes),
                color = com.lastasylum.alliance.game.RaidCommandStyle.ATTACK.colorArgb.toInt(),
            ),
            Command(
                chipLabel = context.getString(R.string.overlay_cmd_column_storm),
                messageLabel = context.getString(com.lastasylum.alliance.game.RaidCommandStyle.STORM.fullLabelRes),
                color = com.lastasylum.alliance.game.RaidCommandStyle.STORM.colorArgb.toInt(),
            ),
            Command(
                // Полная подпись «Подкрепление» — кнопка обнимает текст с равными отступами.
                chipLabel = context.getString(R.string.overlay_cmd_column_reinf),
                messageLabel = context.getString(com.lastasylum.alliance.game.RaidCommandStyle.REINFORCE.fullLabelRes),
                color = com.lastasylum.alliance.game.RaidCommandStyle.REINFORCE.colorArgb.toInt(),
            ),
        )
    }

    private val coordColor = Color.parseColor("#FF7DD3FC")
    private val infoColor = Color.parseColor("#FFE2E8F0")

    private var root: LinearLayout? = null
    private var infoView: TextView? = null
    private var coordsView: TextView? = null
    private var statsView: TextView? = null
    private var chipRow: LinearLayout? = null
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
            Log.i("RaidShareDiag", "panel.show attached=$attached canDraw=${canDrawOverlays()} seq=${target.seq}")
            if (!attached) {
                // Позиционируем по финальному Y ещё ДО addView: иначе панель сначала
                // появляется на стартовой координате, затем прыгает на место — это и есть «моргание».
                val params = buildParams().also { layoutParams = it }
                params.y = computePanelY(target.dialogTopPx, measurePanelHeight(view))
                runCatching { windowManager.addView(view, params) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                        Log.i("RaidShareDiag", "panel addView OK seq=${target.seq} y=${params.y}")
                    }
                    .onFailure { e ->
                        Log.w("RaidShareDiag", "panel addView FAILED seq=${target.seq}: $e", e)
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

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            android.provider.Settings.canDrawOverlays(context)

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

    /** Явный замер высоты панели до первого layout-прохода (нужна сразу для позиционирования). */
    private fun measurePanelHeight(view: View): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dp(300), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return view.measuredHeight.takeIf { it > 0 } ?: view.height
    }

    /** Финальный Y панели над игровым окном выбора канала (с небольшим отступом). */
    private fun computePanelY(dialogTopPx: Int?, panelH: Int): Int {
        val minTop = dp(OverlayHudLayout.chatStripTopOffsetDp() + 8)
        val gap = dp(12)
        val screenH = context.resources.displayMetrics.heightPixels
        // Верх игрового диалога выбора канала: точное значение из игры используем
        // только если оно правдоподобно (диалог не может быть в верхней четверти экрана),
        // иначе берём долю высоты экрана — диалог имеет фиксированную позицию.
        val dialogTop = dialogTopPx?.takeIf { it > screenH * DIALOG_TOP_MIN_FRACTION }
            ?: (screenH * DIALOG_TOP_FRACTION).toInt()
        return if (panelH > 0) {
            (dialogTop - panelH - gap).coerceAtLeast(minTop)
        } else {
            dp(OverlayHudLayout.chatStripTopOffsetDp() + 88)
        }
    }

    /** Разместить панель над игровым окном выбора канала с небольшим отступом. */
    private fun applyVerticalPosition(dialogTopPx: Int?) {
        val view = root ?: return
        val wm = attachedWindowManager ?: return
        val params = layoutParams ?: return
        val place = Runnable {
            params.y = computePanelY(dialogTopPx, measurePanelHeight(view))
            runCatching { wm.updateViewLayout(view, params) }
        }
        if (attached) place.run() else view.post(place)
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
        // Координаты — первой строкой карточки.
        coordsView = TextView(context).apply {
            setTextColor(coordColor)
            textSize = 12f
        }
        infoView = TextView(context).apply {
            setTextColor(infoColor)
            // Вторая строка (описание цели) — мельче первой строки с координатами.
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }
        // Мощь/Поверженные — отдельной (третьей) строкой под координатами.
        statsView = TextView(context).apply {
            setTextColor(infoColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(3), 0, 0)
        }
        infoCard.addView(coordsView)
        infoCard.addView(infoView)
        infoCard.addView(statsView)
        container.addView(infoCard)

        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // Кнопки по размеру текста — группой по центру.
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) }
        }
        chipViews.clear()
        commands.forEachIndexed { index, command ->
            val chip = TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 12f
                // Подпись чекбокса всегда в одну строку — без переноса на вторую.
                maxLines = 1
                setSingleLine(true)
                includeFontPadding = false
                // Равные отступы со всех сторон: кнопка обнимает текст симметрично.
                setPadding(dp(8), dp(8), dp(8), dp(8))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                if (index > 0) lp.leftMargin = dp(8)
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
        this.chipRow = chipRow
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
        // У конвоев и сундуков команды «Атака»/«Штурм»/«Подкрепление» неприменимы — скрываем ряд.
        chipRow?.visibility = if (t.isChest || t.isTruck) View.GONE else View.VISIBLE
        infoView?.text = buildInfoText(t)
        coordsView?.text = buildString {
            append("[")
            if (t.serverNumber != null) append("S:").append(t.serverNumber).append(" ")
            append("X:").append(t.x).append(" Y:").append(t.y)
            append("]")
        }
        // Мощь/поверженные теперь встроены в строку с ником (buildInfoText) — отдельная строка не нужна.
        statsView?.apply {
            text = ""
            visibility = View.GONE
        }
    }

    private fun buildInfoText(t: RaidShareTarget): CharSequence {
        val builder = SpannableStringBuilder()
        fun sep() { if (builder.isNotEmpty()) builder.append(' ') }
        // Ур.N перед тегом альянса, разделитель — пробел.
        t.levelPrefix()?.let { sep(); builder.append(it) }
        sep(); builder.append(t.titleLine())
        t.metaPartsForOverlay().forEach { part -> sep(); builder.append(part) }
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
        // Мощь/поверженные — на этой же строке сразу после ника. Перед иконкой добавляем
        // реальный пробел: одиночный пробел под иконкой «съедается» ImageSpan.
        t.powerLabel()?.let { label ->
            if (builder.isNotEmpty()) builder.append(' ')
            appendStatWithIcon(builder, label, t.powerIcon, R.drawable.ic_overlay_game_power)
        }
        t.killsLabel()?.let { label ->
            if (builder.isNotEmpty()) builder.append(' ')
            appendStatWithIcon(builder, label, t.killsIcon, R.drawable.ic_overlay_game_kills)
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
        val h = dp(15)
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: h
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: h
        val w = (h * iw / ih).coerceAtLeast(1)
        drawable.setBounds(0, 0, w, h)
        val start = builder.length
        builder.append(' ')
        builder.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.append('\u200A')
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
            .joinToString(", ") { commands[it].messageLabel }
            .takeIf { it.isNotEmpty() }
        onSend(label, t)
    }

    private fun syncChips() {
        chipViews.forEachIndexed { index, chip ->
            val command = commands[index]
            val isOn = index in selected
            val mark = if (isOn) "\u2611 " else "\u2610 "
            chip.text = mark + command.chipLabel
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
        // Доля высоты экрана до верха игрового диалога выбора канала (фиксированная позиция).
        private const val DIALOG_TOP_FRACTION = 0.31f
        // Ниже этой доли игровое dialogTopPx считаем недостоверным (диалог не в верхней четверти).
        private const val DIALOG_TOP_MIN_FRACTION = 0.25f
    }
}
