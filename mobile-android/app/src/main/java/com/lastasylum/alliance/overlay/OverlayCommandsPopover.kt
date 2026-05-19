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
 * Меню быстрых команд: вертикальный список карточек с понятным выбором типа и кнопкой координат.
 */
class OverlayCommandsPopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
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
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
    }

    private fun hideCoordOnly() {
        removeShell(coordScrim)
        coordScrim = null
        if (!isShowing()) {
            releaseGameGateSuppress()
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
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
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#F01A2234"),
                Color.parseColor("#E80E1420"),
            ),
        ).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#4D5A7CFF"))
        }

    private fun commandCardBackground(accentColor: Int): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.argb(48, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)),
                Color.parseColor("#2A1A2438"),
            ),
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#33445577"))
        }

    private fun chipBackground(selected: Boolean): GradientDrawable =
        roundedRect(
            fillColor = if (selected) Color.parseColor("#FF3D52D4") else Color.parseColor("#22182030"),
            strokeColor = if (selected) Color.parseColor("#885A7CFF") else Color.parseColor("#33445566"),
            cornerDp = 10,
        )

    private fun fieldBackground(): GradientDrawable =
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
                cornerRadius = dp(12).toFloat()
            },
        )

    private fun secondaryButtonBackground(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#28182030"),
                strokeColor = Color.parseColor("#446B7C9C"),
                cornerDp = 12,
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

    private data class CommandOption(val labelDisplayRes: Int, val labelCommandRes: Int)

    private data class CommandCategory(
        val titleRes: Int,
        val accentColor: Int,
        val glyph: String,
        val options: List<CommandOption>? = null,
        val hintRes: Int? = null,
        val excavation: Boolean = false,
    )

    private fun choiceChip(text: String, selected: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (selected) "#FFF8FAFF" else "#99B0C4D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            minimumHeight = dp(36)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = chipBackground(selected)
            isClickable = true
        }

    private fun buildCommandCard(
        category: CommandCategory,
        onOpenCoords: (commandLabel: String, excavation: Boolean) -> Unit,
    ): LinearLayout {
        val options = category.options
        var selectedIndex = 0
        val chipViews = mutableListOf<TextView>()

        fun refreshChips() {
            chipViews.forEachIndexed { index, chip ->
                val sel = index == selectedIndex
                chip.background = chipBackground(sel)
                chip.setTextColor(Color.parseColor(if (sel) "#FFF8FAFF" else "#99B0C4D8"))
                chip.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        val accentStrip = View(context).apply {
            background = roundedRect(fillColor = category.accentColor, cornerDp = 4)
        }

        val glyphView = TextView(context).apply {
            text = category.glyph
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(10), 0)
        }

        val titleView = TextView(context).apply {
            text = context.getString(category.titleRes)
            setTextColor(Color.parseColor("#FFF4F7FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                accentStrip,
                LinearLayout.LayoutParams(dp(4), dp(22)).apply { marginEnd = dp(8) },
            )
            addView(glyphView)
            addView(titleView)
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = commandCardBackground(category.accentColor)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(titleRow)
        }

        if (category.hintRes != null) {
            card.addView(
                TextView(context).apply {
                    text = context.getString(category.hintRes)
                    setTextColor(Color.parseColor("#88A0B4C8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, dp(6), 0, 0)
                },
            )
        }

        if (options != null) {
            val chipsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val gap = dp(6)
                options.forEachIndexed { index, opt ->
                    val chip = choiceChip(context.getString(opt.labelDisplayRes), index == 0)
                    chip.setOnClickListener {
                        selectedIndex = index
                        refreshChips()
                    }
                    chipViews.add(chip)
                    addView(
                        chip,
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (index < options.lastIndex) marginEnd = gap
                        },
                    )
                }
            }
            card.addView(
                chipsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
        }

        val actionBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_cmd_column_open_coords)
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minimumHeight = dp(42)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = primaryButtonBackground()
            isClickable = true
            setOnClickListener {
                val label = if (options != null) {
                    val idx = selectedIndex.coerceIn(0, options.lastIndex)
                    context.getString(options[idx].labelCommandRes)
                } else {
                    context.getString(category.titleRes)
                }
                onOpenCoords(label, category.excavation)
            }
        }
        card.addView(
            actionBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        return card
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
        val popoverW = minOf(dp(360), screenW - dp(24))

        val raidPill = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_raid_pill)
            setTextColor(Color.parseColor("#FF8FAEFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedRect(
                fillColor = Color.parseColor("#332A4A8C"),
                strokeColor = Color.parseColor("#555A7CFF"),
                cornerDp = 999,
            )
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_title)
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.overlay_commands_subtitle)
            setTextColor(Color.parseColor("#9AB0C4D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(4), 0, 0)
        }
        val titleBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(raidPill, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            addView(
                title,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
            )
            addView(subtitle)
        }

        val close = iconCloseButton().apply { setOnClickListener { hide() } }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(12), dp(10))
            addView(
                titleBlock,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        val headerDivider = View(context).apply {
            setBackgroundColor(Color.parseColor("#338899CC"))
        }

        val onOpenCoords: (String, Boolean) -> Unit = { label, excavation ->
            openCoordsFromMenu(label, excavation)
        }

        val categories = listOf(
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_attack,
                accentColor = Color.parseColor("#FFE53935"),
                glyph = "⚔",
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_attack_city),
                    CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_attack_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_storm,
                accentColor = Color.parseColor("#FFFF9800"),
                glyph = "⚡",
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_storm_city),
                    CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_storm_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_reinf,
                accentColor = Color.parseColor("#FF43A047"),
                glyph = "🛡",
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_reinf_to_city, R.string.overlay_cmd_reinf_city),
                    CommandOption(R.string.overlay_cmd_spinner_reinf_to_player, R.string.overlay_cmd_reinf_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_excavation,
                accentColor = Color.parseColor("#FF7E57C2"),
                glyph = "⛏",
                hintRes = R.string.overlay_cmd_excavation_hint,
                excavation = true,
            ),
        )

        val listColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val gap = dp(10)
            categories.forEachIndexed { index, cat ->
                addView(
                    buildCommandCard(cat, onOpenCoords),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index > 0) topMargin = gap
                    },
                )
            }
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                listColumn,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            clipChildren = false
            elevation = dp(16).toFloat()
            background = cardShellBackground()
            addView(headerRow)
            addView(
                headerDivider,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1),
                ).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                },
            )
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(14)
                    marginStart = dp(14)
                    marginEnd = dp(14)
                },
            )
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(130, 4, 8, 16))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
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
                background = fieldBackground()
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
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
