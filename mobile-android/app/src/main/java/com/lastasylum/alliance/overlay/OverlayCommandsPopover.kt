package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Меню команд рядом с панелью оверлея + диалог ввода координат и отправки строки в текущую комнату чата.
 */
class OverlayCommandsPopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val sendCommand: suspend (String, Int, Int) -> Result<ChatMessage>,
) {
    private var menuScrim: FrameLayout? = null
    private var coordScrim: FrameLayout? = null
    private var attachedWindowManager: WindowManager? = null

    fun isShowing(): Boolean = menuScrim != null || coordScrim != null

    fun hide() {
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        attachedWindowManager = null
    }

    private fun hideCoordOnly() {
        OverlayChatInteractionHold.suppressGameForegroundGate = false
        removeShell(coordScrim)
        coordScrim = null
    }

    private fun removeShell(shell: FrameLayout?) {
        val host = shell ?: return
        val wm = attachedWindowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { wm?.removeView(host) }
    }

    fun toggle(
        windowManager: WindowManager,
        panelParams: WindowManager.LayoutParams,
        panelRoot: View,
        anchoredEnd: Boolean,
    ) {
        if (isShowing()) {
            hide()
            return
        }
        showMenu(windowManager, panelParams, panelRoot, anchoredEnd)
    }

    private fun showMenu(
        windowManager: WindowManager,
        panelParams: WindowManager.LayoutParams,
        panelRoot: View,
        anchoredEnd: Boolean,
    ) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val panelW = panelRoot.width.takeIf { it > 0 } ?: dp(120)
        val panelH = panelRoot.height.takeIf { it > 0 } ?: dp(180)

        val popoverW = minOf(dp(360), screenW - dp(16))
        val popoverH = minOf(dp(280), screenH - dp(24))

        var x = if (anchoredEnd) {
            panelParams.x - popoverW - dp(8)
        } else {
            panelParams.x + panelW + dp(8)
        }
        x = x.coerceIn(dp(8), (screenW - popoverW - dp(8)).coerceAtLeast(dp(8)))

        val yBottom = (panelParams.y + panelH / 2 - popoverH / 2)
            .coerceIn(0, (screenH - popoverH).coerceAtLeast(0))

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_title)
            setTextColor(Color.parseColor("#FFF1F5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val close = TextView(context).apply {
            text = "✕"
            contentDescription = context.getString(R.string.overlay_online_close_cd)
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(6), dp(4), dp(10), dp(4))
            isClickable = true
            setOnClickListener { hide() }
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(4), dp(4))
            addView(
                title,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        fun menuButton(labelRes: Int): TextView =
            TextView(context).apply {
                text = context.getString(labelRes)
                setTextColor(Color.parseColor("#FFE8ECF5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#33101828"))
                    setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#449B7CFF"))
                }
                isClickable = true
                val label = context.getString(labelRes)
                setOnClickListener {
                    val wm = attachedWindowManager ?: return@setOnClickListener
                    hideCoordOnly()
                    removeShell(menuScrim)
                    menuScrim = null
                    showCoordinateDialog(wm, label)
                }
            }

        fun column(vararg labels: Int): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                labels.forEach { id ->
                    val btn = menuButton(id)
                    addView(
                        btn,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            bottomMargin = dp(8)
                        },
                    )
                }
            }

        val columns = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(4), dp(10), dp(10))
            addView(column(R.string.overlay_cmd_attack_city, R.string.overlay_cmd_attack_player))
            addView(column(R.string.overlay_cmd_storm_city, R.string.overlay_cmd_storm_player))
            addView(column(R.string.overlay_cmd_reinf_city, R.string.overlay_cmd_reinf_player))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            elevation = dp(6).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#E610141E"))
                setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#559B7CFF"))
            }
            addView(headerRow)
            addView(columns)
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(0x66000000)
            isClickable = true
            setOnClickListener { hide() }
        }

        val cardLp = FrameLayout.LayoutParams(popoverW, popoverH).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = x
            bottomMargin = yBottom
        }
        scrim.addView(card, cardLp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            this.x = 0
            this.y = 0
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) return

        menuScrim = scrim
        attachedWindowManager = windowManager
    }

    private fun showCoordinateDialog(windowManager: WindowManager, commandLabel: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        OverlayChatInteractionHold.suppressGameForegroundGate = true

        val title = TextView(context).apply {
            text = commandLabel
            setTextColor(Color.parseColor("#FFF1F5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(12), dp(12), dp(4))
        }

        fun labeledField(hint: String): Pair<LinearLayout, EditText> {
            val label = TextView(context).apply {
                text = hint
                setTextColor(Color.parseColor("#99B8C0D9"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            val edit = EditText(context).apply {
                setHint(hint)
                setTextColor(Color.parseColor("#FFE8ECF5"))
                setHintTextColor(Color.parseColor("#668899AA"))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.parseColor("#33101828"))
                    setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#449B7CFF"))
                }
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label)
                addView(
                    edit,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4) },
                )
            }
            return row to edit
        }

        val (rowX, editX) = labeledField(context.getString(R.string.overlay_coord_x_label))
        val (rowY, editY) = labeledField(context.getString(R.string.overlay_coord_y_label))

        val sendBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_send)
            setTextColor(Color.parseColor("#FFE8ECF5"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#CC3D5AFE"))
            }
            isClickable = true
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_cancel)
            setTextColor(Color.parseColor("#FFB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
        }

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(12))
            addView(rowX)
            addView(
                rowY,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
            addView(
                buttonsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(14) },
            )
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(body)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            clipToPadding = false
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#F210141E"))
                setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#559B7CFF"))
            }
            addView(title)
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(136, 0, 0, 0))
            isClickable = true
            setOnClickListener {
                hideKeyboard(editX)
                hideKeyboard(editY)
                hideCoordOnly()
            }
        }

        val cardW = minOf(dp(320), context.resources.displayMetrics.widthPixels - dp(24))
        scrim.addView(
            card,
            FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            @Suppress("DEPRECATION")
            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) {
            OverlayChatInteractionHold.suppressGameForegroundGate = false
            return
        }

        coordScrim = scrim
        attachedWindowManager = windowManager

        cancelBtn.setOnClickListener {
            hideKeyboard(editX)
            hideKeyboard(editY)
            hideCoordOnly()
        }

        sendBtn.setOnClickListener {
            val xv = editX.text?.toString()?.trim()?.toIntOrNull()
            val yv = editY.text?.toString()?.trim()?.toIntOrNull()
            if (xv == null || yv == null) {
                Toast.makeText(context, R.string.overlay_coord_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            hideKeyboard(editX)
            hideKeyboard(editY)
            sendBtn.isEnabled = false
            cancelBtn.isEnabled = false
            scope.launch {
                val result = sendCommand(commandLabel, xv, yv)
                mainHandler.post {
                    sendBtn.isEnabled = true
                    cancelBtn.isEnabled = true
                    result.onSuccess {
                        hideCoordOnly()
                    }.onFailure { e ->
                        val msg = when (e.message) {
                            "no_room" -> context.getString(R.string.overlay_strip_no_room)
                            else ->
                                e.message?.takeIf { it.isNotBlank() }
                                    ?: context.getString(R.string.overlay_history_send_failed, e.javaClass.simpleName)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        mainHandler.post {
            editX.requestFocus()
            showKeyboard(editX)
        }
    }

    private fun hideKeyboard(edit: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(edit.windowToken, 0)
    }

    private fun showKeyboard(edit: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
    }
}
