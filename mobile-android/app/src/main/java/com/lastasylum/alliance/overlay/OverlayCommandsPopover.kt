package com.lastasylum.alliance.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
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
    /** [excavation] → сообщение раскопок в «Рейд» + push союзникам вне игры. */
    private val sendCoords: suspend (label: String, x: Int, y: Int, excavation: Boolean) -> Result<ChatMessage>,
) {
    private var menuScrim: FrameLayout? = null
    private var coordScrim: FrameLayout? = null
    private var attachedWindowManager: WindowManager? = null
    private var gameGateSuppressHeld = false

    fun isShowing(): Boolean = menuScrim != null || coordScrim != null

    fun hide() {
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        attachedWindowManager = null
        releaseGameGateSuppress()
    }

    private fun hideCoordOnly() {
        removeShell(coordScrim)
        coordScrim = null
        if (!isShowing()) {
            releaseGameGateSuppress()
        }
    }

    private fun acquireGameGateSuppress() {
        if (gameGateSuppressHeld) return
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
        gameGateSuppressHeld = true
    }

    private fun releaseGameGateSuppress() {
        if (!gameGateSuppressHeld) return
        OverlayChatInteractionHold.releaseGameForegroundSuppress()
        gameGateSuppressHeld = false
    }

    private fun removeShell(shell: FrameLayout?) {
        val host = shell ?: return
        val wm = attachedWindowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { wm?.removeView(host) }
    }

    fun toggle(windowManager: WindowManager) {
        if (isShowing()) {
            hide()
            return
        }
        showMenu(windowManager)
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun roundedRect(
        fillColor: Int,
        strokeColor: Int? = null,
        cornerDp: Int = 12,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1).coerceAtLeast(1), it) }
        }

    private fun rippleOn(base: GradientDrawable): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
            base,
            base,
        )

    private fun cardShellBackground(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#F0222838"),
                Color.parseColor("#E8121824"),
            ),
        ).apply {
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#557C9CFF"))
        }

    private fun sectionTileBackground(): GradientDrawable =
        roundedRect(
            fillColor = Color.parseColor("#2A1E2A3C"),
            strokeColor = Color.parseColor("#3D7C9CFF"),
            cornerDp = 14,
        )

    private fun spinnerFieldBackground(): GradientDrawable =
        roundedRect(
            fillColor = Color.parseColor("#38101828"),
            strokeColor = Color.parseColor("#4D6B8CFF"),
            cornerDp = 10,
        )

    private fun primaryButtonBackground(): RippleDrawable =
        rippleOn(
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#FF4A5FE8"),
                    Color.parseColor("#FF3D52D4"),
                ),
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(11).toFloat()
            },
        )

    private fun secondaryButtonBackground(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#28182030"),
                strokeColor = Color.parseColor("#446B7C9C"),
                cornerDp = 11,
            ),
        )

    private fun iconCloseButton(): TextView =
        TextView(context).apply {
            text = "✕"
            contentDescription = context.getString(R.string.overlay_online_close_cd)
            setTextColor(Color.parseColor("#CCB8C0D9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rippleOn(
                roundedRect(fillColor = Color.parseColor("#15202838"), cornerDp = 999),
            )
            isClickable = true
        }

    private fun sectionTitleView(titleRes: Int): TextView =
        TextView(context).apply {
            text = context.getString(titleRes).uppercase()
            setTextColor(Color.parseColor("#FF8FAEFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            letterSpacing = 0.1f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }

    private fun sectionHintView(textRes: Int): TextView =
        TextView(context).apply {
            text = context.getString(textRes)
            setTextColor(Color.parseColor("#88A0B4C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setLineSpacing(0f, 1.15f)
            maxLines = 3
            setPadding(0, dp(4), 0, 0)
        }

    private fun buildSpinner(options: List<CommandOption>): Spinner =
        Spinner(context).apply {
            val labels = options.map { context.getString(it.labelDisplayRes) }
            adapter = ArrayAdapter(
                context,
                R.layout.overlay_command_spinner_item,
                labels,
            ).also { a ->
                a.setDropDownViewResource(R.layout.overlay_command_spinner_dropdown)
            }
            background = spinnerFieldBackground()
            setPopupBackgroundDrawable(
                roundedRect(
                    fillColor = Color.parseColor("#F0182030"),
                    strokeColor = Color.parseColor("#668899CC"),
                    cornerDp = 10,
                ),
            )
            minimumHeight = dp(42)
            setPadding(dp(8), dp(4), dp(28), dp(4))
        }

    private fun coordsButton(onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = context.getString(R.string.overlay_cmd_column_open_coords)
            setTextColor(Color.parseColor("#FFF4F7FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            minimumHeight = dp(40)
            setPadding(dp(8), dp(9), dp(8), dp(9))
            background = primaryButtonBackground()
            isClickable = true
            setOnClickListener { onClick() }
        }

    private data class CommandOption(val labelDisplayRes: Int, val labelCommandRes: Int)

    private fun commandSectionTile(
        titleRes: Int,
        options: List<CommandOption>?,
        hintRes: Int? = null,
        onOpenCoords: (commandLabel: String, excavation: Boolean) -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = sectionTileBackground()
            setPadding(dp(10), dp(10), dp(10), dp(10))

            addView(sectionTitleView(titleRes))

            if (hintRes != null) {
                addView(sectionHintView(hintRes))
            }

            if (options != null) {
                val spinner = buildSpinner(options)
                addView(
                    spinner,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) },
                )
                addView(
                    coordsButton {
                        val idx = spinner.selectedItemPosition.coerceIn(0, options.lastIndex)
                        val label = context.getString(options[idx].labelCommandRes)
                        onOpenCoords(label, false)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(8) },
                )
            } else {
                addView(
                    coordsButton {
                        val label = context.getString(titleRes)
                        onOpenCoords(label, true)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(10) },
                )
            }
        }

    private fun gridRow(cells: List<View>): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            val gap = dp(8)
            cells.forEachIndexed { index, cell ->
                addView(
                    cell,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (index < cells.lastIndex) marginEnd = gap
                    },
                )
            }
        }

    private fun openCoordsFromMenu(commandLabel: String, excavation: Boolean) {
        val wm = attachedWindowManager ?: return
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        showCoordinateDialog(wm, commandLabel, excavation)
    }

    private fun showMenu(windowManager: WindowManager) {
        val screenW = context.resources.displayMetrics.widthPixels
        val popoverW = minOf(dp(392), screenW - dp(20))

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_title)
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            letterSpacing = 0.01f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_subtitle)
            setTextColor(Color.parseColor("#8FA3B8C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(3), 0, 0)
        }
        val titleBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle)
        }

        val close = iconCloseButton().apply { setOnClickListener { hide() } }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(12), dp(12))
            addView(
                titleBlock,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            }
            setBackgroundColor(Color.parseColor("#338899CC"))
        }

        val onOpenCoords: (String, Boolean) -> Unit = { label, excavation ->
            openCoordsFromMenu(label, excavation)
        }

        val attackTile = commandSectionTile(
            R.string.overlay_cmd_column_attack,
            listOf(
                CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_attack_city),
                CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_attack_player),
            ),
            onOpenCoords = onOpenCoords,
        )
        val stormTile = commandSectionTile(
            R.string.overlay_cmd_column_storm,
            listOf(
                CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_storm_city),
                CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_storm_player),
            ),
            onOpenCoords = onOpenCoords,
        )
        val reinfTile = commandSectionTile(
            R.string.overlay_cmd_column_reinf,
            listOf(
                CommandOption(R.string.overlay_cmd_spinner_reinf_to_city, R.string.overlay_cmd_reinf_city),
                CommandOption(R.string.overlay_cmd_spinner_reinf_to_player, R.string.overlay_cmd_reinf_player),
            ),
            onOpenCoords = onOpenCoords,
        )
        val excavationTile = commandSectionTile(
            R.string.overlay_cmd_column_excavation,
            options = null,
            hintRes = R.string.overlay_cmd_excavation_hint,
            onOpenCoords = onOpenCoords,
        )

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(16))
            addView(gridRow(listOf(attackTile, stormTile)))
            addView(
                gridRow(listOf(reinfTile, excavationTile)),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            elevation = dp(14).toFloat()
            background = cardShellBackground()
            addView(headerRow)
            addView(headerDivider)
            addView(
                grid,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            isClickable = true
            setOnClickListener { hide() }
        }

        scrim.addView(
            card,
            FrameLayout.LayoutParams(popoverW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) return

        acquireGameGateSuppress()
        menuScrim = scrim
        attachedWindowManager = windowManager
    }

    private fun showCoordinateDialog(
        windowManager: WindowManager,
        commandLabel: String,
        excavation: Boolean,
    ) {
        acquireGameGateSuppress()

        val close = iconCloseButton()

        val title = TextView(context).apply {
            text = commandLabel
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
        }
        val dialogSubtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_dialog_subtitle)
            setTextColor(Color.parseColor("#8FA3B8C8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }
        val titleBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(dialogSubtitle)
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(10))
            addView(
                titleBlock,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        val headerDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
            }
            setBackgroundColor(Color.parseColor("#338899CC"))
        }

        fun coordField(hint: String): Pair<LinearLayout, EditText> {
            val label = TextView(context).apply {
                text = hint
                setTextColor(Color.parseColor("#FF8FAEFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
            }
            val edit = EditText(context).apply {
                setHint("0")
                setTextColor(Color.parseColor("#FFF4F7FF"))
                setHintTextColor(Color.parseColor("#558899AA"))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                background = spinnerFieldBackground()
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
            }
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label)
                addView(
                    edit,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(6) },
                )
            }
            return col to edit
        }

        val (colX, editX) = coordField(context.getString(R.string.overlay_coord_x_label))
        val (colY, editY) = coordField(context.getString(R.string.overlay_coord_y_label))

        val coordsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gap = dp(10)
            addView(
                colX,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                colY,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = gap
                },
            )
        }

        val sendBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_send)
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            setPadding(dp(18), dp(11), dp(18), dp(11))
            background = primaryButtonBackground()
            isClickable = true
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_cancel)
            setTextColor(Color.parseColor("#FFB8C6DD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            gravity = Gravity.CENTER
            minimumHeight = dp(44)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = secondaryButtonBackground()
            isClickable = true
        }

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            val gap = dp(8)
            addView(
                cancelBtn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                sendBtn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f).apply {
                    marginStart = gap
                },
            )
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
            addView(coordsRow)
            addView(
                buttonsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(16) },
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            clipToPadding = false
            elevation = dp(12).toFloat()
            background = cardShellBackground()
            addView(headerRow)
            addView(headerDivider)
            addView(body)
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            isClickable = true
            setOnClickListener {
                hideKeyboard(editX)
                hideKeyboard(editY)
                hideCoordOnly()
            }
        }

        val cardW = minOf(dp(340), context.resources.displayMetrics.widthPixels - dp(24))
        scrim.addView(
            card,
            FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            OverlayWindowLayout.applyCoordinateDialogSoftInputMode(this)
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) {
            if (!isShowing()) {
                releaseGameGateSuppress()
            }
            return
        }

        coordScrim = scrim
        attachedWindowManager = windowManager

        close.setOnClickListener {
            hideKeyboard(editX)
            hideKeyboard(editY)
            hideCoordOnly()
        }

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
                val result = sendCoords(commandLabel, xv, yv, excavation)
                mainHandler.post {
                    sendBtn.isEnabled = true
                    cancelBtn.isEnabled = true
                    result.onSuccess {
                        hideCoordOnly()
                    }.onFailure { e ->
                        val msg = when (e.message) {
                            "no_room" -> context.getString(R.string.overlay_strip_no_room)
                            "no_raid" -> context.getString(R.string.overlay_strip_no_raid)
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
