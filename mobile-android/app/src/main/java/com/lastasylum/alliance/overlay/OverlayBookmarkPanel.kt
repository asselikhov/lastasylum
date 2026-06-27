package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.game.OverlayBookmarkTag
import com.lastasylum.alliance.game.RaidShareTarget

/**
 * Плавающая панель «В закладки» поверх игрового окна «Добавить тег».
 *
 * Одной строкой: выпадающий список наших тегов (Враги/Друзья/Мобы/Сундуки/Города) + кнопка
 * «Добавить». Над строкой — компактная карточка цели (координаты + название), как при шаринге.
 * Касания вне карточки уходят в игру (popupWindowFlags). Сохранение делает [onAdd].
 */
class OverlayBookmarkPanel(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
    private val onAdd: (tag: OverlayBookmarkTag, target: RaidShareTarget) -> Unit,
) {
    private var root: LinearLayout? = null
    private var selectorLabel: TextView? = null
    private var selector: LinearLayout? = null
    private var target: RaidShareTarget? = null
    private var selectedTag = OverlayBookmarkTag.ENEMIES
    private var attached = false
    private var attachedWindowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = attached

    fun show(windowManager: WindowManager, target: RaidShareTarget) {
        runOnMain {
            this.target = target
            val view = root ?: buildView().also { root = it }
            if (!attached) {
                val params = buildParams().also { layoutParams = it }
                params.y = computePanelY(target.dialogTopPx, measurePanelHeight(view))
                runCatching { windowManager.addView(view, params) }
                    .onSuccess {
                        attached = true
                        attachedWindowManager = windowManager
                    }
                    .onFailure { e -> Log.w(TAG, "addView failed", e) }
            } else if (attachedWindowManager != windowManager) {
                runCatching { attachedWindowManager?.removeView(view) }
                attached = false
                attachedWindowManager = null
                show(windowManager, target)
            } else {
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
                }
                .onFailure { e -> Log.w(TAG, "hide failed", e) }
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
            // Фиксированная ширина: окно не «прыгает» при смене тега (длина текста меняется).
            dp(PANEL_WIDTH_DP),
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

    private fun measurePanelHeight(view: View): Int {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(dp(PANEL_WIDTH_DP), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return view.measuredHeight.takeIf { it > 0 } ?: view.height
    }

    private fun computePanelY(dialogTopPx: Int?, panelH: Int): Int {
        val minTop = dp(OverlayHudLayout.chatStripTopOffsetDp() + 8)
        val gap = dp(12)
        val screenH = context.resources.displayMetrics.heightPixels
        val dialogTop = dialogTopPx?.takeIf { it > screenH * DIALOG_TOP_MIN_FRACTION }
            ?: (screenH * DIALOG_TOP_FRACTION).toInt()
        return if (panelH > 0) {
            (dialogTop - panelH - gap).coerceAtLeast(minTop)
        } else {
            dp(OverlayHudLayout.chatStripTopOffsetDp() + 88)
        }
    }

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
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#F51E293B"), Color.parseColor("#F50B1220")),
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#3360A5FA"))
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        // Только строка: [выпадающий список тегов] [Добавить]
        val selectorLabelView = TextView(context).apply {
            text = context.getString(selectedTag.labelRes)
            setTextColor(Color.parseColor("#FFE8F4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            isSingleLine = true
        }
        selectorLabel = selectorLabelView
        val chevron = TextView(context).apply {
            text = "\u25BE"
            setTextColor(Color.parseColor("#8AA0B8D0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(6), 0, 0, 0)
        }
        val selectorView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(11).toFloat()
                setColor(Color.parseColor("#22305070"))
                setStroke(dp(1), Color.parseColor("#3D5A7CAA"))
            }
            isClickable = true
            addView(selectorLabelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            setOnClickListener { openTagPicker() }
        }
        selector = selectorView

        val addButton = TextView(context).apply {
            text = context.getString(R.string.overlay_bookmark_add)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            minimumHeight = dp(40)
            setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#FF3B82F6"), Color.parseColor("#FF6366F1")),
            ).apply { cornerRadius = dp(11).toFloat() }
            setOnClickListener {
                val t = this@OverlayBookmarkPanel.target ?: return@setOnClickListener
                onAdd(selectedTag, t)
            }
        }

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(selectorView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                addButton,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { marginStart = dp(8) },
            )
        }
        container.addView(actionRow)
        return container
    }

    private fun openTagPicker() {
        val anchor = selector ?: return
        val entries = OverlayBookmarkTag.entries
        val titles = entries.map { context.getString(it.labelRes) }
        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, titles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(context).apply {
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                }
                val sel = entries[position] == selectedTag
                row.text = getItem(position)
                row.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#C8DCE8F4"))
                row.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                row.setBackgroundColor(if (sel) Color.parseColor("#332A4558") else Color.TRANSPARENT)
                return row
            }
        }
        val popup = ListPopupWindow(context).apply {
            anchorView = anchor
            setAdapter(adapter)
            width = anchor.width.coerceAtLeast(dp(180))
            isModal = true
            inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(
                GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#F0141C28"))
                    setStroke(dp(1), Color.parseColor("#3D5A7CAA"))
                },
            )
            setOnItemClickListener { _, _, position, _ ->
                val picked = entries.getOrNull(position)
                dismiss()
                if (picked != null) {
                    selectedTag = picked
                    selectorLabel?.text = context.getString(picked.labelRes)
                }
            }
        }
        anchor.post {
            if (!anchor.isAttachedToWindow) return@post
            runCatching { popup.show() }
        }
    }

    companion object {
        private const val TAG = "OverlayBookmarkPanel"
        // Фиксированная ширина окна (вмещает самый длинный тег + кнопку «Добавить»).
        private const val PANEL_WIDTH_DP = 280
        // Игровое окно «Добавить тег» — фиксированный модал, его верх выше шер-диалога
        // (≈0.258 высоты экрана). Панель «В закладки» садится над ним с тем же отступом dp(12).
        // Игровое dialogTopPx для этого окна приходит недостоверным (≈417 на 2400) — отбрасываем.
        private const val DIALOG_TOP_FRACTION = 0.258f
        private const val DIALOG_TOP_MIN_FRACTION = 0.20f
    }
}
