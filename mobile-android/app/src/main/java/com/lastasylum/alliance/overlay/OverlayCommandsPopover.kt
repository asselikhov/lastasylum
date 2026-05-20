package com.lastasylum.alliance.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Компактное меню быстрых команд: вкладки типов, мелкие чипы варианта, лаконичный ввод координат.
 */
class OverlayCommandsPopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val sendCoords: suspend (label: String, x: Int, y: Int, excavation: Boolean) -> Result<ChatMessage>,
    private val notifyExcavation: suspend () -> Result<ChatMessage>,
    private val emitOverlayReaction: (targetUserId: String, reactionId: String) -> Unit = { _, _ -> },
    private val emitOverlayReactionBroadcast: (reactionId: String) -> Unit = {},
) {
    private var menuScrim: FrameLayout? = null
    private var coordScrim: FrameLayout? = null
    private var reactionPickScrim: FrameLayout? = null
    private var reactionBurstScrim: FrameLayout? = null
    private var reactionBurstLottie: LottieAnimationView? = null
    private var heartPreviewAnimator: Animator? = null
    private var reactionPreviewLotties: List<LottieAnimationView> = emptyList()
    private var attachedWindowManager: WindowManager? = null
    private var gameGateSuppressHeld = false

    fun isShowing(): Boolean =
        menuScrim != null ||
            coordScrim != null ||
            reactionPickScrim != null ||
            reactionBurstScrim != null

    fun hide() {
        stopHeartPreviewPulse()
        hideReactionPickOnly()
        hideReactionBurstOnly()
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        attachedWindowManager = null
        releaseGameGateSuppress()
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
    }

    private fun hideReactionPickOnly() {
        removeShell(reactionPickScrim)
        reactionPickScrim = null
        if (!isShowing()) {
            releaseGameGateSuppress()
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        }
    }

    private fun hideReactionBurstOnly() {
        reactionBurstLottie?.cancelAnimation()
        reactionBurstLottie = null
        removeShell(reactionBurstScrim)
        reactionBurstScrim = null
        if (!isShowing()) {
            releaseGameGateSuppress()
            OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
        }
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

    private fun stopHeartPreviewPulse() {
        heartPreviewAnimator?.cancel()
        heartPreviewAnimator = null
        reactionPreviewLotties.forEach { lottie ->
            lottie.cancelAnimation()
            lottie.progress = 0f
        }
    }

    private fun applyLottieReactionTint(view: LottieAnimationView, tintHex: String) {
        val color = Color.parseColor(tintHex)
        view.addValueCallback(
            KeyPath("**"),
            LottieProperty.COLOR_FILTER,
            LottieValueCallback(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)),
        )
    }

    private fun createReactionTileIcon(reaction: OverlayQuickReaction): View {
        val memeRes = reaction.memeDrawableRes
        if (memeRes != null) {
            return ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
        val lottieRes = reaction.lottieRawRes
        if (lottieRes != null) {
            return LottieAnimationView(context).apply {
                setAnimation(lottieRes)
                reaction.lottieTintHex?.let { applyLottieReactionTint(this, it) }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                repeatCount = LottieDrawable.INFINITE
            }
        }
        return ImageView(context).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, reaction.iconRes)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor(reaction.tintHex))
                },
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun startReactionStripPreviews() {
        stopHeartPreviewPulse()
        reactionPreviewLotties.forEach { lottie ->
            lottie.repeatCount = LottieDrawable.INFINITE
            lottie.playAnimation()
        }
    }

    private fun startHeartPreviewPulse(target: View) {
        stopHeartPreviewPulse()
        if (target is LottieAnimationView) {
            target.repeatCount = LottieDrawable.INFINITE
            target.playAnimation()
            return
        }
        val image = target as? ImageView ?: return
        val scaleX = ObjectAnimator.ofFloat(image, "scaleX", 1f, 1.14f, 1f).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(image, "scaleY", 1f, 1.14f, 1f).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
        }
        val glow = ObjectAnimator.ofFloat(image, "alpha", 1f, 0.82f, 1f).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
        }
        heartPreviewAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, glow)
            start()
        }
    }

    fun toggle(windowManager: WindowManager) {
        if (isShowing()) {
            hide()
            return
        }
        showMenu(windowManager)
    }

    /** Показать вспышку реакции от сокомандника (пришла по сокету). */
    fun showIncomingReactionBurst(
        windowManager: WindowManager,
        fromUsername: String,
        reactionId: String = "heart",
    ) {
        showReactionBurst(windowManager, fromUsername, reactionId)
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
        val isReactions: Boolean = false,
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
        compactTabs: Boolean,
    ): LinearLayout {
        val touchSize = if (compactTabs) dp(46) else dp(54)
        val iconSize = if (compactTabs) dp(22) else dp(26)
        val captionSp = if (compactTabs) 8f else 9f
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
            captionSp,
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
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_reactions,
                shortLabelRes = R.string.overlay_cmd_tab_reactions,
                iconRes = R.drawable.ic_overlay_cmd_reaction,
                accentColor = Color.parseColor("#FFE91E63"),
                hintRes = R.string.overlay_reactions_hint,
                isReactions = true,
            ),
        )

        val compactTabs = categories.size >= 5
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

        var selectedReactionSubcategory = OverlayReactionCategory.ANIMATIONS
        val reactionTilesPadding = dp(2)
        val reactionTileSize = dp(54)
        val reactionIconInner = dp(46)

        val reactionTilesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, reactionTilesPadding, 0)
        }

        fun reactionSubChipBackground(selected: Boolean): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                if (selected) {
                    setColor(Color.parseColor("#553A5A78"))
                    setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#88A8C4E8"))
                } else {
                    setColor(Color.parseColor("#22182533"))
                    setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#33445566"))
                }
            }

        val reactionSubAnimChip = choiceChip(
            context.getString(R.string.overlay_reactions_sub_animations),
            selected = true,
        )
        val reactionSubMemeChip = choiceChip(
            context.getString(R.string.overlay_reactions_sub_memes),
            selected = false,
        )
        val reactionSubTabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }

        fun refreshReactionSubTabs() {
            val animSel = selectedReactionSubcategory == OverlayReactionCategory.ANIMATIONS
            reactionSubAnimChip.background = reactionSubChipBackground(animSel)
            reactionSubAnimChip.setTextColor(Color.parseColor(if (animSel) "#FFE8F4FF" else "#9AB0C4D8"))
            reactionSubAnimChip.typeface = if (animSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            reactionSubMemeChip.background = reactionSubChipBackground(!animSel)
            reactionSubMemeChip.setTextColor(Color.parseColor(if (!animSel) "#FFE8F4FF" else "#9AB0C4D8"))
            reactionSubMemeChip.typeface = if (!animSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun rebuildReactionTiles() {
            reactionTilesRow.removeAllViews()
            val previewBuilder = mutableListOf<LottieAnimationView>()
            val items = overlayQuickReactionCatalog().filter { it.category == selectedReactionSubcategory }
            for (reaction in items) {
                val icon = createReactionTileIcon(reaction).apply {
                    contentDescription = context.getString(reaction.labelRes)
                }
                if (icon is LottieAnimationView) previewBuilder.add(icon)
                val host = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(reactionTileSize, reactionTileSize).apply {
                        marginEnd = dp(8)
                    }
                    background = rippleOn(
                        GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(12).toFloat()
                            setColor(Color.parseColor("#33182533"))
                            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#33445566"))
                        },
                    )
                    isClickable = true
                    addView(
                        icon,
                        FrameLayout.LayoutParams(reactionIconInner, reactionIconInner, Gravity.CENTER),
                    )
                    setOnClickListener {
                        val wmUse = attachedWindowManager ?: return@setOnClickListener
                        stopHeartPreviewPulse()
                        removeShell(menuScrim)
                        menuScrim = null
                        showReactionRecipientPicker(wmUse, reaction.id)
                    }
                }
                reactionTilesRow.addView(host)
            }
            reactionPreviewLotties = previewBuilder
            if (categories[selectedCategoryIndex].isReactions) {
                startReactionStripPreviews()
            }
        }

        reactionSubAnimChip.setOnClickListener {
            if (selectedReactionSubcategory == OverlayReactionCategory.ANIMATIONS) return@setOnClickListener
            selectedReactionSubcategory = OverlayReactionCategory.ANIMATIONS
            refreshReactionSubTabs()
            rebuildReactionTiles()
        }
        reactionSubMemeChip.setOnClickListener {
            if (selectedReactionSubcategory == OverlayReactionCategory.MEMES) return@setOnClickListener
            selectedReactionSubcategory = OverlayReactionCategory.MEMES
            refreshReactionSubTabs()
            rebuildReactionTiles()
        }
        reactionSubTabsRow.addView(
            reactionSubAnimChip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(8) },
        )
        reactionSubTabsRow.addView(reactionSubMemeChip)

        rebuildReactionTiles()

        val reactionScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                reactionTilesRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val reactionHintRow = labelText(
            context.getString(R.string.overlay_reactions_pick_hint),
            10.5f,
            Color.parseColor("#7A90A4B8"),
        ).apply {
            setPadding(0, 0, 0, dp(2))
        }

        val reactionRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            visibility = View.GONE
            addView(reactionHintRow)
            addView(reactionSubTabsRow)
            addView(
                reactionScroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
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

        fun refreshPrimaryAction(cat: CommandCategory) {
            if (cat.excavation) {
                coordsLabel.text = context.getString(R.string.overlay_cmd_excavation_notify)
                coordsIcon.setImageDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_overlay_send)?.mutate()?.also { d ->
                        DrawableCompat.setTint(d, Color.parseColor("#FF8FAEFF"))
                    },
                )
            } else {
                coordsLabel.text = context.getString(R.string.overlay_cmd_column_open_coords)
                coordsIcon.setImageDrawable(
                    AppCompatResources.getDrawable(context, R.drawable.ic_overlay_cmd_coords)?.mutate()?.also { d ->
                        DrawableCompat.setTint(d, Color.parseColor("#FF8FAEFF"))
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
            if (cat.isReactions) {
                coordsAction.visibility = View.GONE
                reactionRow.visibility = View.VISIBLE
                startReactionStripPreviews()
            } else {
                stopHeartPreviewPulse()
                coordsAction.visibility = View.VISIBLE
                reactionRow.visibility = View.GONE
                refreshPrimaryAction(cat)
            }
        }

        categories.forEachIndexed { index, cat ->
            val tab = categoryIconTab(cat, index == 0, compactTabs).apply {
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
                    if (index > 0) marginStart = if (compactTabs) dp(2) else dp(4)
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
            reactionRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
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
            if (cat.excavation) {
                coordsAction.isEnabled = false
                scope.launch {
                    val result = notifyExcavation()
                    mainHandler.post {
                        coordsAction.isEnabled = true
                        result.onSuccess {
                            hide()
                        }.onFailure { e ->
                            val msg = when (e.message) {
                                "no_room" -> context.getString(R.string.overlay_strip_no_room)
                                "no_raid" -> context.getString(R.string.overlay_strip_no_raid)
                                else ->
                                    e.message?.takeIf { it.isNotBlank() }
                                        ?: context.getString(
                                            R.string.overlay_history_send_failed,
                                            e.javaClass.simpleName,
                                        )
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return@setOnClickListener
            }
            val label = if (cat.options != null) {
                val idx = selectedOptionIndex.coerceIn(0, cat.options.lastIndex)
                context.getString(cat.options[idx].labelCommandRes)
            } else {
                context.getString(cat.titleRes)
            }
            openCoordsFromMenu(label, excavation = false)
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
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
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
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
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

    private fun reactionSendAllRow(memberCount: Int, onPick: () -> Unit): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rippleOn(
                roundedRect(
                    fillColor = Color.parseColor("#2A1E2838"),
                    strokeColor = Color.parseColor("#55FFB74D"),
                    cornerDp = 12,
                ),
            )
            isClickable = true
            setOnClickListener { onPick() }
        }
        val name = labelText(
            context.getString(R.string.overlay_reactions_send_all),
            13.5f,
            Color.parseColor("#FFFFE082"),
            bold = true,
        )
        val subtitle = labelText(
            context.getString(R.string.overlay_reactions_send_all_subtitle, memberCount),
            10f,
            Color.parseColor("#9AB0C4D8"),
        )
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(subtitle)
        }
        row.addView(
            textCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(10) }
        return row
    }

    private fun memberPickRow(
        member: PlayerTeamMemberDto,
        onPick: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rippleOn(
                roundedRect(
                    fillColor = Color.parseColor("#22182533"),
                    strokeColor = Color.parseColor("#33445566"),
                    cornerDp = 12,
                ),
            )
            isClickable = true
            setOnClickListener { onPick() }
        }
        val name = labelText(member.username, 13.5f, Color.parseColor("#FFF4F7FF"), bold = true)
        val role = labelText(
            member.teamRole.trim().ifBlank { "R1" },
            10f,
            Color.parseColor("#7A90A4B8"),
        )
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(role)
        }
        row.addView(
            textCol,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
        return row
    }

    private fun showReactionRecipientPicker(windowManager: WindowManager, reactionId: String) {
        hideReactionPickOnly()
        acquireGameGateSuppress()
        attachedWindowManager = windowManager

        val selectedReaction = overlayQuickReactionById(reactionId)
        val container = AppContainer.from(context)
        val close = iconCloseButton()
        val title = labelText(
            context.getString(R.string.overlay_reactions_recipient_title),
            14f,
            Color.parseColor("#FFF4F7FF"),
            bold = true,
        )
        val reactionPreviewSize = dp(72)
        val reactionPreview = createReactionTileIcon(selectedReaction).apply {
            contentDescription = context.getString(R.string.overlay_reactions_recipient_preview_cd)
            if (this is LottieAnimationView) {
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
        }
        val reactionPreviewRow = FrameLayout(context).apply {
            setPadding(0, dp(8), 0, dp(4))
            addView(
                reactionPreview,
                FrameLayout.LayoutParams(reactionPreviewSize, reactionPreviewSize, Gravity.CENTER),
            )
        }
        val loading = labelText(
            context.getString(R.string.overlay_reactions_loading),
            12f,
            Color.parseColor("#8A9AA8B8"),
            paddingH = dp(14),
            paddingV = dp(10),
        )

        val listColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                listColumn,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(6))
            addView(
                title,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(close)
        }

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#288899AA"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = panelShellBackground()
            elevation = dp(10).toFloat()
            addView(headerRow)
            addView(reactionPreviewRow)
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
            addView(loading)
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(320),
                ),
            )
            setOnClickListener { }
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 4, 8, 16))
            isClickable = true
            setOnClickListener { hideReactionPickOnly() }
        }
        val cardW = minOf(dp(340), context.resources.displayMetrics.widthPixels - dp(16))
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
            OverlayWindowLayout.popupWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) {
            if (!isShowing()) {
                releaseGameGateSuppress()
            }
            return
        }
        reactionPickScrim = scrim
        close.setOnClickListener { hideReactionPickOnly() }

        scope.launch {
            val loadResult = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = OverlayTeamContextCache.load(
                        usersRepository = container.usersRepository,
                        teamsRepository = container.teamsRepository,
                    ).getOrThrow()
                    val team = container.teamsRepository.getTeam(ctx.teamId).getOrThrow()
                    val self = ctx.currentUserId
                    OverlayGameStatusHudRefresh.filterTeamIngameOverlayMembers(team.members)
                        .filter { it.userId != self }
                }
            }
            mainHandler.post {
                listColumn.removeAllViews()
                loading.visibility = View.GONE
                loadResult.fold(
                    onFailure = { e ->
                        val msg = when (e.message) {
                            "no_team" -> context.getString(R.string.overlay_reactions_no_team)
                            else ->
                                e.message?.takeIf { it.isNotBlank() }
                                    ?: context.getString(
                                        R.string.overlay_history_send_failed,
                                        e.javaClass.simpleName,
                                    )
                        }
                        listColumn.addView(
                            labelText(
                                msg,
                                12f,
                                Color.parseColor("#FFFF8A80"),
                                paddingH = dp(14),
                                paddingV = dp(10),
                            ),
                        )
                    },
                    onSuccess = { members ->
                        if (members.isEmpty()) {
                            listColumn.addView(
                                labelText(
                                    context.getString(R.string.overlay_reactions_none_ingame),
                                    12f,
                                    Color.parseColor("#9AB0C4D8"),
                                    paddingH = dp(14),
                                    paddingV = dp(10),
                                ),
                            )
                        } else {
                            listColumn.addView(
                                reactionSendAllRow(members.size) {
                                    hideReactionPickOnly()
                                    emitOverlayReactionBroadcast(reactionId)
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.overlay_reaction_sent_all,
                                            members.size,
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            members.forEach { m ->
                                listColumn.addView(
                                    memberPickRow(m) {
                                        hideReactionPickOnly()
                                        emitOverlayReaction(m.userId, reactionId)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.overlay_reaction_sent, m.username),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    private fun reactionBurstSenderCardBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(14).toFloat()
            setColor(Color.parseColor("#E8141C2A"))
            setStroke(dp(1).coerceAtLeast(1), Color.parseColor("#4D5C7499"))
        }

    private fun createReactionBurstAnimView(reaction: OverlayQuickReaction): View {
        val memeRes = reaction.memeDrawableRes
        if (memeRes != null) {
            return ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        val lottieRes = reaction.lottieRawRes
        if (lottieRes != null) {
            return LottieAnimationView(context).apply {
                setAnimation(lottieRes)
                reaction.lottieTintHex?.let { applyLottieReactionTint(this, it) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }.also { reactionBurstLottie = it }
        }
        return ImageView(context).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, reaction.iconRes)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor(reaction.tintHex))
                },
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun showReactionBurst(
        windowManager: WindowManager,
        subtitleUsername: String,
        reactionId: String = "heart",
    ) {
        hideReactionBurstOnly()
        attachedWindowManager = windowManager

        val displayName = subtitleUsername.trim().ifBlank { "—" }
        val reaction = overlayQuickReactionById(reactionId)

        val root = OverlayPassthroughMultitouchFrameLayout(context).apply {
            setBackgroundColor(Color.argb(38, 6, 12, 22))
        }
        val burstAnimSize = when {
            reaction.memeDrawableRes != null -> dp(200)
            reaction.lottieRawRes != null -> dp(160)
            else -> dp(120)
        }
        val heart = createReactionBurstAnimView(reaction)
        val accentOpaque = Color.parseColor(reaction.burstAccentHex)
        val accentTransparent = accentOpaque and 0x00FFFFFF
        val accentBar = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    accentTransparent,
                    accentOpaque,
                    accentTransparent,
                ),
            ).apply {
                cornerRadius = dp(2).toFloat()
            }
        }
        val caption = TextView(context).apply {
            text = context.getString(R.string.overlay_reaction_burst_caption)
            setTextColor(Color.parseColor("#9AB0C8DD"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT
            letterSpacing = 0.06f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val nameLabel = TextView(context).apply {
            text = displayName
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(dp(1).toFloat(), 0f, 1.2f, Color.parseColor("#58000000"))
        }
        val textInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(accentBar, LinearLayout.LayoutParams(dp(120), dp(3)).apply { bottomMargin = dp(10) })
            addView(caption)
            addView(
                nameLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) },
            )
        }
        val textCard = FrameLayout(context).apply {
            background = reactionBurstSenderCardBackground()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            addView(textInner)
        }
        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(heart, LinearLayout.LayoutParams(burstAnimSize, burstAnimSize))
            addView(
                textCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(18)
                },
            )
        }
        root.addView(
            stack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            OverlayWindowLayout.reactionBurstWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
        }

        if (runCatching { windowManager.addView(root, params) }.isFailure) {
            attachedWindowManager = null
            return
        }
        reactionBurstScrim = root

        heart.scaleX = 0.25f
        heart.scaleY = 0.25f
        heart.alpha = 0f
        textCard.alpha = 0f
        textCard.translationY = dp(8).toFloat()
        heart.post {
            heart.pivotX = heart.width * 0.5f
            heart.pivotY = heart.height * 0.5f
        }

        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(heart, "scaleX", 0.25f, 1.18f, 1f).setDuration(720),
                ObjectAnimator.ofFloat(heart, "scaleY", 0.25f, 1.18f, 1f).setDuration(720),
                ObjectAnimator.ofFloat(heart, "alpha", 0f, 1f).setDuration(380),
            )
        }
        val cardIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(textCard, "alpha", 0f, 1f).setDuration(420),
                ObjectAnimator.ofFloat(textCard, "translationY", dp(8).toFloat(), 0f).setDuration(520),
            )
            startDelay = 200
        }
        enter.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val beatX = ObjectAnimator.ofFloat(heart, "scaleX", 1f, 1.12f, 1f).apply {
                        duration = 700
                        repeatCount = 2
                    }
                    val beatY = ObjectAnimator.ofFloat(heart, "scaleY", 1f, 1.12f, 1f).apply {
                        duration = 700
                        repeatCount = 2
                    }
                    AnimatorSet().apply { playTogether(beatX, beatY) }.start()
                }
            },
        )
        enter.start()
        cardIn.start()

        mainHandler.postDelayed(
            {
                val done = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(heart, "alpha", 1f, 0f).setDuration(400),
                        ObjectAnimator.ofFloat(heart, "scaleX", 1f, 1.35f).setDuration(400),
                        ObjectAnimator.ofFloat(heart, "scaleY", 1f, 1.35f).setDuration(400),
                        ObjectAnimator.ofFloat(textCard, "alpha", 1f, 0f).setDuration(380),
                        ObjectAnimator.ofFloat(textCard, "translationY", 0f, (-dp(6)).toFloat()).setDuration(380),
                    )
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                hideReactionBurstOnly()
                            }
                        },
                    )
                }
                done.start()
            },
            OVERLAY_REACTION_BURST_VISIBLE_MS,
        )
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
