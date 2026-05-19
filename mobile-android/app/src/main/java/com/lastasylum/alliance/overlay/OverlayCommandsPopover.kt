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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Компактное меню быстрых команд: вкладки типов, мелкие чипы варианта, лаконичный ввод координат.
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
        cornerDp: Int = 8,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(fillColor)
            strokeColor?.let { setStroke(dp(1).coerceAtLeast(1), it) }
        }

    private fun rippleOn(base: GradientDrawable): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#28FFFFFF")),
            base,
            base,
        )

    private fun panelShellBackground(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#F2141C2A"),
                Color.parseColor("#EE0C1018"),
            ),
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#3D4A62AA"))
        }

    private fun optionChipBackground(selected: Boolean): GradientDrawable =
        roundedRect(
            fillColor = if (selected) Color.parseColor("#FF2A4558") else Color.parseColor("#FF1A2836"),
            strokeColor = if (selected) Color.parseColor("#775A9AB8") else Color.parseColor("#354A5E72"),
            cornerDp = 999,
        )

    private fun categoryIconBackground(selected: Boolean, accentColor: Int): GradientDrawable {
        val base = Color.argb(
            if (selected) 88 else 40,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor),
        )
        val edge = Color.argb(
            if (selected) 48 else 24,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor),
        )
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(base, edge),
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(
                dp(if (selected) 2 else 1).coerceAtLeast(1),
                if (selected) accentColor else Color.parseColor("#3D4A6088"),
            )
        }
    }

    private fun fieldBackground(): GradientDrawable =
        roundedRect(
            fillColor = Color.parseColor("#2A141C28"),
            strokeColor = Color.parseColor("#3D5A7CAA"),
            cornerDp = 8,
        )

    private fun coordsButtonBackground(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#1E2A3C48"),
                strokeColor = Color.parseColor("#CC5A7CFF"),
                cornerDp = 10,
            ),
        )

    private fun primarySendButtonBackground(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#FF3A4EC8"),
                cornerDp = 8,
            ),
        )

    private fun ghostButtonRipple(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#22182030"),
                strokeColor = Color.parseColor("#33445566"),
                cornerDp = 8,
            ),
        )

    private data class CommandOption(val labelDisplayRes: Int, val labelCommandRes: Int)

    private data class CommandCategory(
        val titleRes: Int,
        val shortLabelRes: Int,
        val iconRes: Int,
        val accentColor: Int,
        val options: List<CommandOption>? = null,
        val hintRes: Int? = null,
        val excavation: Boolean = false,
    )

    private fun labelText(
        text: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        paddingH: Int = 0,
        paddingV: Int = 0,
    ): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (paddingH > 0 || paddingV > 0) {
                setPadding(paddingH, paddingV, paddingH, paddingV)
            }
        }

    private fun choiceChip(text: String, selected: Boolean): TextView =
        TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor(if (selected) "#FFE8F4FF" else "#9AB0C4D8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = optionChipBackground(selected)
            isClickable = true
        }

    private fun categoryIconTab(
        category: CommandCategory,
        selected: Boolean,
    ): LinearLayout {
        val touchSize = dp(54)
        val iconSize = dp(26)
        val iconHost = FrameLayout(context).apply {
            background = rippleOn(categoryIconBackground(selected, category.accentColor))
            layoutParams = LinearLayout.LayoutParams(touchSize, touchSize)
        }
        val icon = ImageView(context).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, category.iconRes)?.mutate()?.also { d ->
                    DrawableCompat.setTint(
                        d,
                        if (selected) Color.WHITE else Color.parseColor("#C8D4E4"),
                    )
                },
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        iconHost.addView(
            icon,
            FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
        )
        val caption = labelText(
            context.getString(category.shortLabelRes),
            9f,
            if (selected) Color.parseColor("#FFF4F7FF") else Color.parseColor("#7A90A6B8"),
            bold = selected,
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(3), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            contentDescription = context.getString(category.shortLabelRes)
            addView(iconHost)
            addView(caption)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
    }

    private fun iconCloseButton(): TextView =
        TextView(context).apply {
            text = "✕"
            contentDescription = context.getString(R.string.overlay_online_close_cd)
            setTextColor(Color.parseColor("#99A8B4CC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rippleOn(
                roundedRect(fillColor = Color.parseColor("#12000000"), cornerDp = 999),
            )
            isClickable = true
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
        val popoverW = minOf(dp(328), screenW - dp(16))

        val categories = listOf(
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_attack,
                shortLabelRes = R.string.overlay_cmd_tab_attack,
                iconRes = R.drawable.ic_overlay_cmd_attack,
                accentColor = Color.parseColor("#FFE53935"),
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_attack_city),
                    CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_attack_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_storm,
                shortLabelRes = R.string.overlay_cmd_tab_storm,
                iconRes = R.drawable.ic_overlay_cmd_storm,
                accentColor = Color.parseColor("#FFFF9800"),
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_by_city, R.string.overlay_cmd_storm_city),
                    CommandOption(R.string.overlay_cmd_spinner_by_player, R.string.overlay_cmd_storm_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_reinf,
                shortLabelRes = R.string.overlay_cmd_tab_reinf,
                iconRes = R.drawable.ic_overlay_cmd_reinf,
                accentColor = Color.parseColor("#FF43A047"),
                options = listOf(
                    CommandOption(R.string.overlay_cmd_spinner_reinf_to_city, R.string.overlay_cmd_reinf_city),
                    CommandOption(R.string.overlay_cmd_spinner_reinf_to_player, R.string.overlay_cmd_reinf_player),
                ),
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_excavation,
                shortLabelRes = R.string.overlay_cmd_tab_excavation,
                iconRes = R.drawable.ic_overlay_cmd_excavation,
                accentColor = Color.parseColor("#FF7E57C2"),
                hintRes = R.string.overlay_cmd_excavation_hint,
                excavation = true,
            ),
        )

        var selectedCategoryIndex = 0
        var selectedOptionIndex = 0

        val raidPill = labelText(
            context.getString(R.string.overlay_commands_raid_pill),
            9f,
            Color.parseColor("#FF8FAEFF"),
            bold = true,
            paddingH = dp(6),
            paddingV = dp(2),
        ).apply {
            background = roundedRect(
                fillColor = Color.parseColor("#282A4A70"),
                strokeColor = Color.parseColor("#444A6ACC"),
                cornerDp = 999,
            )
        }

        val title = labelText(
            context.getString(R.string.overlay_commands_title),
            15f,
            Color.parseColor("#FFF4F7FF"),
            bold = true,
        )
        val close = iconCloseButton()

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(8), dp(6))
            addView(raidPill)
            addView(
                title,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(8)
                },
            )
            addView(close)
        }

        val tabViews = mutableListOf<LinearLayout>()
        val tabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }

        val accentDot = View(context)
        val categoryTitle = labelText("", 13.5f, Color.parseColor("#FFF4F7FF"), bold = true)
        val categoryHint = labelText("", 10.5f, Color.parseColor("#7A90A4B8"))
        val optionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        val optionChips = mutableListOf<TextView>()

        val coordsIcon = ImageView(context).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, R.drawable.ic_overlay_cmd_coords)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor("#FF8FAEFF"))
                },
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val coordsLabel = labelText(
            context.getString(R.string.overlay_cmd_column_open_coords),
            12.5f,
            Color.parseColor("#FFE8F0FF"),
            bold = true,
        )
        val coordsAction = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(36)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = coordsButtonBackground()
            isClickable = true
            addView(coordsIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(
                coordsLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) },
            )
        }

        val bodyColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(12))
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun refreshTabs() {
            tabViews.forEachIndexed { index, tab ->
                val sel = index == selectedCategoryIndex
                val cat = categories[index]
                val iconHost = tab.getChildAt(0) as? FrameLayout
                iconHost?.background = rippleOn(categoryIconBackground(sel, cat.accentColor))
                val icon = iconHost?.getChildAt(0) as? ImageView
                icon?.setImageDrawable(
                    AppCompatResources.getDrawable(context, cat.iconRes)?.mutate()?.also { d ->
                        DrawableCompat.setTint(
                            d,
                            if (sel) Color.WHITE else Color.parseColor("#C8D4E4"),
                        )
                    },
                )
                val caption = tab.getChildAt(1) as? TextView
                caption?.setTextColor(
                    Color.parseColor(if (sel) "#FFF0F4FF" else "#88A0B4C8"),
                )
                caption?.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        fun refreshOptions() {
            optionChips.forEachIndexed { index, chip ->
                val sel = index == selectedOptionIndex
                chip.background = optionChipBackground(sel)
                chip.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#9AB0C4D8"))
                chip.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        fun rebuildOptionsForCategory(cat: CommandCategory) {
            optionsRow.removeAllViews()
            optionChips.clear()
            val opts = cat.options
            if (opts == null) {
                optionsRow.visibility = View.GONE
                return
            }
            optionsRow.visibility = View.VISIBLE
            selectedOptionIndex = 0
            val gap = dp(8)
            opts.forEachIndexed { index, opt ->
                val chip = choiceChip(context.getString(opt.labelDisplayRes), index == 0)
                chip.setOnClickListener {
                    selectedOptionIndex = index
                    refreshOptions()
                }
                optionChips.add(chip)
                optionsRow.addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index < opts.lastIndex) marginEnd = gap
                    },
                )
            }
        }

        fun applyCategory(index: Int) {
            selectedCategoryIndex = index.coerceIn(0, categories.lastIndex)
            val cat = categories[selectedCategoryIndex]
            refreshTabs()
            accentDot.background = roundedRect(fillColor = cat.accentColor, cornerDp = 3)
            categoryTitle.text = context.getString(cat.titleRes)
            if (cat.hintRes != null) {
                categoryHint.visibility = View.VISIBLE
                categoryHint.text = context.getString(cat.hintRes)
            } else {
                categoryHint.visibility = View.GONE
            }
            rebuildOptionsForCategory(cat)
        }

        categories.forEachIndexed { index, cat ->
            val tab = categoryIconTab(cat, index == 0).apply {
                setOnClickListener { applyCategory(index) }
            }
            tabViews.add(tab)
            tabsRow.addView(
                tab,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index > 0) marginStart = dp(4)
                },
            )
        }

        titleRow.addView(
            accentDot,
            LinearLayout.LayoutParams(dp(3), dp(16)).apply { marginEnd = dp(8) },
        )
        titleRow.addView(categoryTitle)
        bodyColumn.addView(titleRow)
        bodyColumn.addView(
            categoryHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(2) },
        )
        bodyColumn.addView(
            optionsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        bodyColumn.addView(
            coordsAction,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        close.setOnClickListener { hide() }
        coordsAction.setOnClickListener {
            val cat = categories[selectedCategoryIndex]
            val label = if (cat.options != null) {
                val idx = selectedOptionIndex.coerceIn(0, cat.options.lastIndex)
                context.getString(cat.options[idx].labelCommandRes)
            } else {
                context.getString(cat.titleRes)
            }
            openCoordsFromMenu(label, cat.excavation)
        }

        applyCategory(0)

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#288899AA"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            elevation = dp(10).toFloat()
            background = panelShellBackground()
            addView(headerRow)
            addView(
                divider,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1),
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                },
            )
            addView(tabsRow)
            addView(bodyColumn)
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 4, 8, 16))
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
        val title = labelText(commandLabel, 14f, Color.parseColor("#FFF4F7FF"), bold = true)
        val subtitle = labelText(
            context.getString(R.string.overlay_coord_dialog_subtitle),
            10.5f,
            Color.parseColor("#7A90A4B8"),
        )

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(6))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(title)
                    addView(subtitle)
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        fun coordField(hint: String): Pair<LinearLayout, EditText> {
            val label = labelText(hint, 10f, Color.parseColor("#FF8FAEFF"), bold = true)
            val edit = EditText(context).apply {
                setHint("0")
                setTextColor(Color.parseColor("#FFF4F7FF"))
                setHintTextColor(Color.parseColor("#558899AA"))
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                background = fieldBackground()
                setPadding(dp(10), dp(7), dp(10), dp(7))
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
                    ).apply { topMargin = dp(4) },
                )
            }
            return col to edit
        }

        val (colX, editX) = coordField(context.getString(R.string.overlay_coord_x_label))
        val (colY, editY) = coordField(context.getString(R.string.overlay_coord_y_label))

        val coordsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gap = dp(8)
            addView(colX, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = primarySendButtonBackground()
            isClickable = true
        }

        val cancelBtn = TextView(context).apply {
            text = context.getString(R.string.overlay_coord_cancel)
            setTextColor(Color.parseColor("#FFB0BDD0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            minHeight = dp(34)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = ghostButtonRipple()
            isClickable = true
        }

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gap = dp(6)
            addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(
                sendBtn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                    marginStart = gap
                },
            )
        }

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(14), dp(12))
            addView(coordsRow)
            addView(
                buttonsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
        }

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#288899AA"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            elevation = dp(8).toFloat()
            background = panelShellBackground()
            addView(headerRow)
            addView(
                divider,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(1),
                ).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                },
            )
            addView(body)
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(100, 0, 0, 0))
            isClickable = true
            setOnClickListener {
                hideKeyboard(editX)
                hideKeyboard(editY)
                hideCoordOnly()
            }
        }

        val cardW = minOf(dp(288), context.resources.displayMetrics.widthPixels - dp(20))
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
