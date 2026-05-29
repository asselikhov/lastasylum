package com.lastasylum.alliance.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
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
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.theme.SquadRelayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * РљРѕРјРїР°РєС‚РЅРѕРµ РјРµРЅСЋ Р±С‹СЃС‚СЂС‹С… РєРѕРјР°РЅРґ: РІРєР»Р°РґРєРё С‚РёРїРѕРІ, РјРµР»РєРёРµ С‡РёРїС‹ РІР°СЂРёР°РЅС‚Р°, Р»Р°РєРѕРЅРёС‡РЅС‹Р№ РІРІРѕРґ РєРѕРѕСЂРґРёРЅР°С‚.
 */
class OverlayCommandsPopover(
    private val context: Context,
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val dp: (Int) -> Int,
    private val sendCoords: suspend (label: String, x: Int, y: Int, excavation: Boolean) -> Result<ChatMessage>,
    private val notifyExcavation: suspend () -> Result<ChatMessage>,
    private val warmupOverlayRaid: () -> Unit = {},
    private val prepareOptimisticRaidQuickCommand: (label: String, x: Int, y: Int, excavation: Boolean) -> String? =
        { _, _, _, _ -> null },
    private val prepareOptimisticRaidNotify: () -> String? = { null },
    private val removeOptimisticRaidSend: (pendingId: String) -> Unit = {},
    private val emitOverlayReaction: (targetUserId: String, reactionId: String) -> Unit = { _, _ -> },
    private val emitOverlayReactionBroadcast: (reactionId: String) -> Unit = {},
) {
    @Volatile
    private var menuScrim: FrameLayout? = null
    @Volatile
    private var coordScrim: FrameLayout? = null
    @Volatile
    private var reactionPickScrim: FrameLayout? = null
    private var reopenMenuOnReactionsTab = false
    private var reopenReactionSubcategory = OverlayReactionCategory.ANIMATIONS
    private var preselectedReactionUserIds: Set<String> = emptySet()
    private val reactionBurstPresenter = OverlayReactionBurstPresenter(context, mainHandler, dp)
    private var heartPreviewAnimator: Animator? = null
    private var reactionTilesAdapter: OverlayReactionTilesAdapter? = null
    private var reactionPreviewKeepAliveRunnable: Runnable? = null
    private var attachedWindowManager: WindowManager? = null
    /** РџР°СЂРЅС‹Рµ acquire/release: РјРµРЅСЋ, picker, РєРѕРѕСЂРґРёРЅР°С‚С‹, РІС…РѕРґСЏС‰РёР№ burst. */
    private var gameGateSuppressDepth = 0
    /** РњРµР¶РґСѓ СЃРјРµРЅРѕР№ scrim (РјРµРЅСЋ в†’ picker) [isShowing] РёРЅР°С‡Рµ РЅР° РјРіРЅРѕРІРµРЅРёРµ false Рё game gate СЃРЅРёРјР°РµС‚ HUD. */
    @Volatile
    private var surfaceTransitionDepth = 0
    private var pendingSuppressRelease: Runnable? = null

    fun isShowing(): Boolean =
        surfaceTransitionDepth > 0 ||
            menuScrim != null ||
            coordScrim != null ||
            reactionPickScrim != null ||
            reactionBurstPresenter.isActive()

    /** True while РјРµРЅСЋ/СЂРµР°РєС†РёРё/РєРѕРѕСЂРґРёРЅР°С‚С‹ РѕС‚РєСЂС‹С‚С‹ РёР»Рё РёРґС‘С‚ СЃРјРµРЅР° scrim (РґР»СЏ game gate РЅР° main). */
    fun isBlockingGameGateDismiss(): Boolean = isShowing()

    private inline fun withPopoverSurfaceTransition(block: () -> Unit) {
        surfaceTransitionDepth++
        try {
            block()
        } finally {
            surfaceTransitionDepth = (surfaceTransitionDepth - 1).coerceAtLeast(0)
        }
    }

    fun hide() {
        stopHeartPreviewPulse()
        reactionBurstPresenter.clear()
        hideReactionPickOnly()
        hideReactionBurstOnly()
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        attachedWindowManager = null
        surfaceTransitionDepth = 0
        clearGameGateSuppress()
    }

    private fun hideReactionPickOnly() {
        removeShell(reactionPickScrim)
        reactionPickScrim = null
        releasePopoverSuppressAfterUiClosed()
    }

    private fun hideReactionBurstOnly() {
        reactionBurstPresenter.clear()
        releasePopoverSuppressAfterUiClosed()
    }

    private fun hideCoordOnly() {
        removeShell(coordScrim)
        coordScrim = null
        releasePopoverSuppressAfterUiClosed()
    }

    /** Р”РµСЂР¶РёРј suppress, РїРѕРєР° РѕС‚РєСЂС‹С‚Рѕ Р»СЋР±РѕРµ РѕРєРЅРѕ РєРѕРјР°РЅРґ/СЂРµР°РєС†РёР№ (РёРЅР°С‡Рµ game gate СЃРЅРёРјР°РµС‚ FAB РЅР° С‡Р°СЃС‚Рё ROM). */
    private fun ensurePopoverSuppressHeld() {
        if (gameGateSuppressDepth == 0) {
            acquireGameGateSuppress()
        }
    }

    /**
     * РЎР±СЂР°СЃС‹РІР°РµРј suppress С‚РѕР»СЊРєРѕ РєРѕРіРґР° UI РїРѕРїР°РїР° РїРѕР»РЅРѕСЃС‚СЊСЋ Р·Р°РєСЂС‹С‚.
     * РќРµ РІС‹Р·С‹РІР°РµРј [OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction] вЂ” РѕРЅ СЃРЅРёРјР°РµС‚
     * СЃС‡С‘С‚С‡РёРє Hold Р±РµР· [gameGateSuppressDepth] Рё РґР°С‘С‚ Р»РѕР¶РЅС‹Р№ tick game gate.
     */
    private fun releasePopoverSuppressAfterUiClosed() {
        pendingSuppressRelease?.let { mainHandler.removeCallbacks(it) }
        pendingSuppressRelease = null
        if (!isShowing()) {
            val release = Runnable {
                pendingSuppressRelease = null
                if (!isShowing()) {
                    clearGameGateSuppress()
                }
            }
            pendingSuppressRelease = release
            mainHandler.postDelayed(release, POPOVER_SUPPRESS_RELEASE_DELAY_MS)
        }
    }

    private fun acquireGameGateSuppress() {
        gameGateSuppressDepth++
        OverlayChatInteractionHold.acquireGameForegroundSuppress()
    }

    private fun releaseGameGateSuppress() {
        if (gameGateSuppressDepth <= 0) return
        gameGateSuppressDepth--
        OverlayChatInteractionHold.releaseGameForegroundSuppress()
    }

    private fun clearGameGateSuppress() {
        while (gameGateSuppressDepth > 0) {
            releaseGameGateSuppress()
        }
    }

    private fun removeShell(shell: FrameLayout?) {
        val host = shell ?: return
        val wm = attachedWindowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { wm?.removeView(host) }
    }

    /**
     * Р—Р°РєСЂС‹С‚РёРµ РїРѕ scrim С‚РѕР»СЊРєРѕ РїСЂРё РѕС‚РїСѓСЃРєР°РЅРёРё РїР°Р»СЊС†Р° РІРЅРµ [card].
     * РРЅР°С‡Рµ РїРѕСЃР»Рµ РїРµСЂРµСЃР±РѕСЂРєРё СЃРµС‚РєРё СЂРµР°РєС†РёР№ (СЃРјРµРЅР° РІРєР»Р°РґРєРё) UP СѓС…РѕРґРёС‚ РЅР° scrim в†’ [hide] Рё game gate СЃРЅРёРјР°РµС‚ HUD.
     */
    private fun FrameLayout.setDismissOnOutsideCardTouch(card: View, onDismiss: () -> Unit) {
        isClickable = true
        setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
            val cardLoc = IntArray(2)
            card.getLocationOnScreen(cardLoc)
            val x = event.rawX
            val y = event.rawY
            val left = cardLoc[0].toFloat()
            val top = cardLoc[1].toFloat()
            val right = left + card.width
            val bottom = top + card.height
            if (x < left || x > right || y < top || y > bottom) {
                onDismiss()
            }
            true
        }
    }

    private fun View.consumeTouchesInSubtree() {
        isClickable = true
        setOnTouchListener { _, _ -> true }
    }

    private fun stopReactionPreviewKeepAlive() {
        reactionPreviewKeepAliveRunnable?.let { mainHandler.removeCallbacks(it) }
        reactionPreviewKeepAliveRunnable = null
    }

    /** Lottie РІ overlay-РѕРєРЅР°С… РЅР° С‡Р°СЃС‚Рё OEM РїРµСЂРµСЃС‚Р°С‘С‚ РєСЂСѓС‚РёС‚СЊСЃСЏ вЂ” РїРѕРґРЅРёРјР°РµРј СЃРЅРѕРІР°, РїРѕРєР° РјРµРЅСЋ РѕС‚РєСЂС‹С‚Рѕ. */
    private fun ensureReactionPreviewLottiesPlaying() {
        reactionTilesAdapter?.activePreviewLotties()?.forEach { lottie ->
            if (!lottie.isAttachedToWindow) return@forEach
            configureOverlayReactionLottie(lottie, playLoop = true)
            if (!lottie.isAnimating) {
                lottie.playAnimation()
            }
        }
    }

    private fun startReactionPreviewKeepAlive() {
        stopReactionPreviewKeepAlive()
        val tick = object : Runnable {
            override fun run() {
                if (menuScrim == null) return
                val lotties = reactionTilesAdapter?.activePreviewLotties().orEmpty()
                val needsKick = lotties.any { it.isAttachedToWindow && !it.isAnimating }
                if (needsKick) {
                    ensureReactionPreviewLottiesPlaying()
                }
                if (menuScrim != null) {
                    mainHandler.postDelayed(this, REACTION_PREVIEW_KEEP_ALIVE_MS)
                }
            }
        }
        reactionPreviewKeepAliveRunnable = tick
        mainHandler.postDelayed(tick, REACTION_PREVIEW_KEEP_ALIVE_MS)
    }

    private fun stopHeartPreviewPulse() {
        stopReactionPreviewKeepAlive()
        heartPreviewAnimator?.cancel()
        heartPreviewAnimator = null
        reactionTilesAdapter?.pauseAllPreviews()
    }

    private val reactionFavorites = OverlayReactionFavoritesStore(context)

    private fun startReactionStripPreviews() {
        startReactionPreviewKeepAlive()
    }

    private fun startHeartPreviewPulse(target: View) {
        stopHeartPreviewPulse()
        if (target is LottieAnimationView) {
            configureOverlayReactionLottie(target, playLoop = true)
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
        ensurePopoverSuppressHeld()
        warmupOverlayRaid()
        showMenu(windowManager)
    }

    /** Opens quick commands on the reactions tab; next recipient picker preselects [userId]. */
    fun openReactionsPreselectUser(windowManager: WindowManager, userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        preselectedReactionUserIds = setOf(id)
        reopenMenuOnReactionsTab = true
        if (isShowing()) hide()
        ensurePopoverSuppressHeld()
        warmupOverlayRaid()
        showMenu(windowManager)
    }

    /** РџРѕРєР°Р·Р°С‚СЊ РІСЃРїС‹С€РєСѓ СЂРµР°РєС†РёРё РѕС‚ СЃРѕРєРѕРјР°РЅРґРЅРёРєР° (РїСЂРёС€Р»Р° РїРѕ СЃРѕРєРµС‚Сѓ). */
    fun showIncomingReactionBurst(
        windowManager: WindowManager,
        fromUsername: String,
        reactionId: String = "heart",
        broadcast: Boolean = false,
    ) {
        attachedWindowManager = windowManager
        acquireGameGateSuppress()
        reactionBurstPresenter.enqueue(
            windowManager,
            OverlayReactionBurstRequest(
                fromDisplayName = fromUsername,
                reactionId = reactionId,
                broadcast = broadcast,
            ),
        ) {
            if (!reactionBurstPresenter.isActive()) {
                releaseGameGateSuppress()
            }
            releasePopoverSuppressAfterUiClosed()
        }
    }

    private fun emitReactionIfConnected(block: () -> Unit): Boolean {
        val connected = AppContainer.from(context).chatRepository.isChatSocketConnected()
        if (!connected) {
            Toast.makeText(
                context,
                context.getString(R.string.overlay_reaction_socket_offline),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }
        block()
        return true
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

    private fun iconCloseButton(): ImageView =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_overlay_close)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#99A8B4CC"))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(R.string.overlay_online_close_cd)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = rippleOn(
                roundedRect(fillColor = Color.parseColor("#12000000"), cornerDp = 999),
            )
            isClickable = true
        }

    private fun returnToReactionsList(windowManager: WindowManager) {
        reopenMenuOnReactionsTab = true
        withPopoverSurfaceTransition {
            removeShell(reactionPickScrim)
            reactionPickScrim = null
            showMenu(windowManager)
        }
    }

    private fun openCoordsFromMenu(commandLabel: String, excavation: Boolean) {
        val wm = attachedWindowManager ?: return
        hideCoordOnly()
        removeShell(menuScrim)
        menuScrim = null
        showCoordinateDialog(
            windowManager = wm,
            commandLabel = commandLabel,
            excavation = excavation,
        )
    }

    private fun showMenu(windowManager: WindowManager) {
        ensurePopoverSuppressHeld()
        warmupOverlayRaid()
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
        var selectedStickerPackKey = OVERLAY_REACTION_STICKER_PACK
        var enabledStickerPackKeys = AppContainer.from(context).usersRepository.peekMyProfile()
            ?.enabledStickerPacks
            ?.toSet()
            ?: emptySet()
        val reactionTileSize = dp(52)
        val reactionIconInner = dp(42)
        val reactionGridColumns = 5
        val reactionCellMargin = dp(3)

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
        val reactionSubStickerChip = choiceChip(
            context.getString(R.string.overlay_reactions_sub_stickers),
            selected = false,
        )
        val reactionSubTextChip = choiceChip(
            context.getString(R.string.overlay_reactions_sub_text),
            selected = false,
        )
        val reactionSubTabsInner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val reactionSubTabsRow = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(
                reactionSubTabsInner,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val reactionStickerPackPicker = OverlayReactionStickerPackDropdown(context, dp)

        val reactionTabEmpty = labelText(
            context.getString(R.string.overlay_reactions_stickers_empty),
            11f,
            Color.parseColor("#7A90A4B8"),
        ).apply { visibility = View.GONE }

        fun refreshReactionSubTabs() {
            data class Tab(val cat: OverlayReactionCategory, val chip: TextView)
            val tabs = listOf(
                Tab(OverlayReactionCategory.ANIMATIONS, reactionSubAnimChip),
                Tab(OverlayReactionCategory.MEMES, reactionSubMemeChip),
                Tab(OverlayReactionCategory.STICKERS, reactionSubStickerChip),
                Tab(OverlayReactionCategory.TEXT, reactionSubTextChip),
            )
            tabs.forEach { (cat, chip) ->
                val sel = selectedReactionSubcategory == cat
                chip.background = reactionSubChipBackground(sel)
                chip.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#9AB0C4D8"))
                chip.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }

        lateinit var rebuildReactionTiles: () -> Unit

        fun scheduleReactionTilesRebuild() {
            surfaceTransitionDepth++
            mainHandler.post {
                try {
                    if (menuScrim == null) return@post
                    rebuildReactionTiles()
                } finally {
                    surfaceTransitionDepth = (surfaceTransitionDepth - 1).coerceAtLeast(0)
                }
            }
        }

        fun refreshStickerPackPicker() {
            val tabs = overlayStickerPackTabs(enabledStickerPackKeys)
            if (tabs.none { it.packKey == selectedStickerPackKey }) {
                selectedStickerPackKey = OVERLAY_REACTION_STICKER_PACK
            }
            reactionStickerPackPicker.bind(tabs, selectedStickerPackKey)
        }

        reactionStickerPackPicker.onPackSelected = { packKey ->
            if (selectedStickerPackKey != packKey) {
                selectedStickerPackKey = packKey
                OverlayReactionBitmapCache.preloadStickerPack(context, packKey)
                refreshStickerPackPicker()
                scheduleReactionTilesRebuild()
            }
        }

        fun loadEnabledStickerPackKeys() {
            enabledStickerPackKeys = AppContainer.from(context).usersRepository.peekMyProfile()
                ?.enabledStickerPacks
                ?.toSet()
                ?: enabledStickerPackKeys
            refreshStickerPackPicker()
            val container = AppContainer.from(context)
            scope.launch {
                val keys = OverlayTeamContextCache.load(
                    usersRepository = container.usersRepository,
                    teamsRepository = container.teamsRepository,
                ).getOrNull()?.enabledStickerPackKeys ?: enabledStickerPackKeys
                withContext(Dispatchers.Main) {
                    if (menuScrim == null) return@withContext
                    if (keys != enabledStickerPackKeys) {
                        enabledStickerPackKeys = keys
                        refreshStickerPackPicker()
                        if (selectedReactionSubcategory == OverlayReactionCategory.STICKERS) {
                            scheduleReactionTilesRebuild()
                        }
                    }
                }
            }
        }

        val reactionTilesRecycler = RecyclerView(context).apply {
            layoutManager = OverlayReactionTilesAdapter.gridLayoutManager(context, reactionGridColumns)
            itemAnimator = null
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setHasFixedSize(false)
        }
        reactionTilesAdapter = OverlayReactionTilesAdapter(
            context = context,
            tileSizePx = reactionTileSize,
            iconInnerPx = reactionIconInner,
            cellMarginPx = reactionCellMargin,
            favorites = reactionFavorites,
            onPick = { reaction ->
                val wmUse = attachedWindowManager ?: return@OverlayReactionTilesAdapter
                reopenReactionSubcategory = selectedReactionSubcategory
                stopHeartPreviewPulse()
                overlayQuickReactionById(context, reaction.id).let { resolved ->
                    val stem = resolved.stickerAssetStem ?: return@let
                    val packKey = resolved.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
                    OverlayReactionBitmapCache.preloadSticker(context, packKey, stem)
                }
                showReactionRecipientPicker(wmUse, reaction.id)
            },
            onFavoritesChanged = { scheduleReactionTilesRebuild() },
        )
        reactionTilesRecycler.adapter = reactionTilesAdapter
        reactionTilesAdapter?.let { adapter ->
            OverlayReactionTilesAdapter.attachVisiblePreviewLifecycle(reactionTilesRecycler, adapter)
        }

        val reactionTextInput = EditText(context).apply {
            hint = context.getString(R.string.overlay_reactions_text_hint)
            setTextColor(Color.parseColor("#FFF4F7FF"))
            setHintTextColor(Color.parseColor("#7A90A4B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            background = fieldBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            gravity = Gravity.TOP or Gravity.START
            isFocusable = true
            isFocusableInTouchMode = true
            showSoftInputOnFocus = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 2
            isVerticalScrollBarEnabled = true
            filters = arrayOf(InputFilter.LengthFilter(OVERLAY_TEXT_REACTION_MAX_CHARS))
        }
        val reactionTextCounter = labelText(
            context.getString(
                R.string.overlay_reactions_text_counter,
                0,
                OVERLAY_TEXT_REACTION_MAX_CHARS,
            ),
            10f,
            Color.parseColor("#7A90A4B8"),
        )
        reactionTextInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val len = s?.length ?: 0
                    reactionTextCounter.text = context.getString(
                        R.string.overlay_reactions_text_counter,
                        len,
                        OVERLAY_TEXT_REACTION_MAX_CHARS,
                    )
                    reactionTextCounter.setTextColor(
                        Color.parseColor(
                            if (len >= OVERLAY_TEXT_REACTION_MAX_CHARS) "#FFFFB74D" else "#7A90A4B8",
                        ),
                    )
                }
            },
        )
        fun submitTextReaction() {
            val reactionId = encodeTextReactionId(reactionTextInput.text?.toString().orEmpty())
            if (reactionId == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.overlay_reactions_text_empty),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            val wmUse = attachedWindowManager ?: return
            hideKeyboard(reactionTextInput)
            reopenReactionSubcategory = OverlayReactionCategory.TEXT
            stopHeartPreviewPulse()
            showReactionRecipientPicker(wmUse, reactionId)
        }
        val reactionTextSend = TextView(context).apply {
            text = context.getString(R.string.overlay_reactions_text_send)
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(40)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = primarySendButtonBackground()
            isClickable = true
            setOnClickListener { submitTextReaction() }
        }
        val reactionTextPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = roundedRect(
                fillColor = Color.parseColor("#181C2438"),
                strokeColor = Color.parseColor("#334A62AA"),
                cornerDp = 10,
            )
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                reactionTextInput,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52),
                ),
            )
            addView(
                reactionTextCounter,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) },
            )
            addView(
                reactionTextSend,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }

        listOf(
            reactionSubAnimChip,
            reactionSubMemeChip,
            reactionSubStickerChip,
            reactionSubTextChip,
        ).forEach { chip ->
            reactionSubTabsInner.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) },
            )
        }

        val reactionGridScroll = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            val wrap = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(reactionTabEmpty)
                addView(
                    reactionTilesRecycler,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            addView(
                wrap,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
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

        rebuildReactionTiles = fun() {
            val isTextTab = selectedReactionSubcategory == OverlayReactionCategory.TEXT
            reactionGridScroll.visibility = if (isTextTab) View.GONE else View.VISIBLE
            reactionTextPanel.visibility = if (isTextTab) View.VISIBLE else View.GONE
            reactionHintRow.visibility = if (isTextTab) View.GONE else View.VISIBLE
            if (!isTextTab) {
                reactionHintRow.text = context.getString(R.string.overlay_reactions_pick_hint)
            }
            if (isTextTab) {
                reactionTabEmpty.visibility = View.GONE
                stopReactionPreviewKeepAlive()
                mainHandler.post {
                    if (menuScrim == null || reactionTextPanel.visibility != View.VISIBLE) return@post
                    focusReactionTextInputAndShowKeyboard(reactionTextInput)
                }
                return
            }
            hideKeyboard(reactionTextInput)
            val isStickersTab = selectedReactionSubcategory == OverlayReactionCategory.STICKERS
            if (isStickersTab) {
                refreshStickerPackPicker()
                reactionStickerPackPicker.root.visibility = View.VISIBLE
            } else {
                reactionStickerPackPicker.dismissPicker()
                reactionStickerPackPicker.root.visibility = View.GONE
            }
            val items = if (isStickersTab) {
                overlayStickerReactionsForPack(
                    context,
                    selectedStickerPackKey,
                    reactionFavorites,
                )
            } else {
                overlayReactionsForCategory(
                    selectedReactionSubcategory,
                    reactionFavorites,
                    context,
                )
            }
            reactionTabEmpty.visibility =
                if (items.isEmpty()) View.VISIBLE else View.GONE
            reactionTilesAdapter?.submitList(items)
            if (categories[selectedCategoryIndex].isReactions) {
                startReactionStripPreviews()
            }
        }

        fun selectReactionSubcategory(cat: OverlayReactionCategory) {
            if (selectedReactionSubcategory == cat) return
            selectedReactionSubcategory = cat
            reopenReactionSubcategory = cat
            if (cat == OverlayReactionCategory.STICKERS) {
                selectedStickerPackKey = OVERLAY_REACTION_STICKER_PACK
                loadEnabledStickerPackKeys()
                OverlayReactionBitmapCache.preloadStickerPack(context, OVERLAY_REACTION_STICKER_PACK)
            }
            if (cat == OverlayReactionCategory.TEXT) {
                stopReactionPreviewKeepAlive()
            }
            refreshReactionSubTabs()
            scheduleReactionTilesRebuild()
        }

        reactionSubAnimChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.ANIMATIONS) }
        reactionSubMemeChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.MEMES) }
        reactionSubStickerChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.STICKERS) }
        reactionSubTextChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.TEXT) }

        refreshStickerPackPicker()
        rebuildReactionTiles()

        val reactionRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            visibility = View.GONE
            addView(reactionHintRow)
            addView(
                reactionSubTabsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                reactionStickerPackPicker.root,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) },
            )
            addView(
                reactionTextPanel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
            addView(
                reactionGridScroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(220),
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

        fun refreshPrimaryAction(cat: CommandCategory) {
            when {
                cat.excavation -> {
                    coordsLabel.text = context.getString(R.string.overlay_cmd_excavation_notify)
                    coordsIcon.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_overlay_send)?.mutate()?.also { d ->
                            DrawableCompat.setTint(d, Color.parseColor("#FF8FAEFF"))
                        },
                    )
                }
                else -> {
                    coordsLabel.text = context.getString(R.string.overlay_cmd_column_open_coords)
                    coordsIcon.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_overlay_cmd_coords)?.mutate()?.also { d ->
                            DrawableCompat.setTint(d, Color.parseColor("#FF8FAEFF"))
                        },
                    )
                }
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
            if (cat.isReactions) {
                ensurePopoverSuppressHeld()
                OverlayReactionBitmapCache.preloadOverlayStickerPack(context)
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
                val pendingId = prepareOptimisticRaidNotify()
                CombatOverlayService.extendInGameOverlayUiHold()
                hide()
                scope.launch {
                    val result = notifyExcavation()
                    mainHandler.post {
                        result.onFailure { e ->
                            pendingId?.let(removeOptimisticRaidSend)
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
        if (reopenMenuOnReactionsTab) {
            reopenMenuOnReactionsTab = false
            val reactionsIndex = categories.indexOfFirst { it.isReactions }
            if (reactionsIndex >= 0) {
                applyCategory(reactionsIndex)
                selectReactionSubcategory(reopenReactionSubcategory)
            }
        }

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
            consumeTouchesInSubtree()
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 4, 8, 16))
            setDismissOnOutsideCardTouch(card) { hide() }
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
            OverlayWindowLayout.overlayModalWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
            OverlayWindowLayout.applyOverlayModalSoftInputMode(this)
        }

        if (runCatching { windowManager.addView(scrim, params) }.isFailure) return

        ensurePopoverSuppressHeld()
        menuScrim = scrim
        attachedWindowManager = windowManager
    }

    private fun showCoordinateDialog(
        windowManager: WindowManager,
        commandLabel: String,
        excavation: Boolean,
    ) {
        ensurePopoverSuppressHeld()
        CombatOverlayService.extendInGameOverlayUiHold()

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
            consumeTouchesInSubtree()
        }

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(100, 0, 0, 0))
            setDismissOnOutsideCardTouch(card) {
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
            releasePopoverSuppressAfterUiClosed()
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
            val pendingId = prepareOptimisticRaidQuickCommand(commandLabel, xv, yv, excavation)
            if (pendingId == null) {
                Toast.makeText(context, R.string.overlay_strip_no_raid, Toast.LENGTH_SHORT).show()
            }
            CombatOverlayService.extendInGameOverlayUiHold()
            hideCoordOnly()
            scope.launch {
                val result = sendCoords(commandLabel, xv, yv, excavation)
                mainHandler.post {
                    result.onFailure { e ->
                        pendingId?.let(removeOptimisticRaidSend)
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

    private fun showReactionRecipientPicker(windowManager: WindowManager, reactionId: String) {
        withPopoverSurfaceTransition {
            showReactionRecipientPickerInner(windowManager, reactionId)
        }
    }

    private fun showReactionRecipientPickerInner(windowManager: WindowManager, reactionId: String) {
        val previousPick = reactionPickScrim
        ensurePopoverSuppressHeld()
        attachedWindowManager = windowManager
        // Сначала новый scrim, потом снимаем меню — иначе [isShowing] на мгновение false.
        val menuToRemove = menuScrim

        val container = AppContainer.from(context)
        val cardW = minOf(dp(360), context.resources.displayMetrics.widthPixels - dp(16))
        val overlayService = context as CombatOverlayService
        val composeOwner = overlayService.obtainOverlayPopoverComposeOwner()
        val initialSelected = preselectedReactionUserIds
        preselectedReactionUserIds = emptySet()

        val composeHost = FrameLayout(context).apply {
            consumeTouchesInSubtree()
        }
        val compose = ComposeView(context)
        composeHost.addView(
            compose,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(110, 4, 8, 16))
            setDismissOnOutsideCardTouch(composeHost) { hideReactionPickOnly() }
        }
        scrim.addView(
            composeHost,
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
            releasePopoverSuppressAfterUiClosed()
            return
        }
        reactionPickScrim = scrim

        overlayService.attachOverlayComposeTree(scrim, composeHost, compose)
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides composeOwner,
                LocalViewModelStoreOwner provides composeOwner,
                LocalSavedStateRegistryOwner provides composeOwner,
                LocalOnBackPressedDispatcherOwner provides composeOwner,
            ) {
                SquadRelayTheme {
                    OverlayReactionRecipientSheet(
                        reactionId = reactionId,
                        initialSelectedUserIds = initialSelected,
                        onBack = { returnToReactionsList(windowManager) },
                        onDismiss = { hideReactionPickOnly() },
                        onSendToUserIds = { userIds ->
                            if (userIds.isEmpty()) return@OverlayReactionRecipientSheet
                            if (!emitReactionIfConnected {
                                    userIds.forEach { uid ->
                                        emitOverlayReaction(uid, reactionId)
                                    }
                                }
                            ) {
                                return@OverlayReactionRecipientSheet
                            }
                            hideReactionPickOnly()
                            Toast.makeText(
                                context,
                                context.getString(R.string.overlay_reaction_sent_selected, userIds.size),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        loadMembers = {
                            loadOverlayIngameReactionRecipients(
                                usersRepository = container.usersRepository,
                                teamsRepository = container.teamsRepository,
                            )
                        },
                    )
                }
            }
        }

        mainHandler.post {
            if (menuToRemove != null) {
                removeShell(menuToRemove)
                if (menuScrim === menuToRemove) {
                    menuScrim = null
                }
            }
            if (previousPick != null && previousPick !== scrim) {
                removeShell(previousPick)
            }
        }
        compose.post { compose.requestLayout() }
    }


    private fun hideKeyboard(edit: EditText) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(edit.windowToken, 0)
    }

    private fun focusReactionTextInputAndShowKeyboard(edit: EditText) {
        edit.requestFocus()
        showKeyboard(edit)
        edit.postDelayed({ showKeyboard(edit) }, 80L)
    }

    private fun showKeyboard(edit: EditText) {
        if (!edit.isFocused) edit.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        @Suppress("DEPRECATION")
        if (!imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)) {
            @Suppress("DEPRECATION")
            imm.showSoftInput(edit, InputMethodManager.SHOW_FORCED)
        }
    }

    private companion object {
        /** Delay suppress release after coord dialog closes so game gate does not tear down HUD. */
        private const val POPOVER_SUPPRESS_RELEASE_DELAY_MS = 500L
        /** Р РµР¶Рµ Р±СѓРґРёС‚СЊ Lottie-РїСЂРµРІСЊСЋ вЂ” РЅР° РіР»Р°РІРЅРѕРј РїРѕС‚РѕРєРµ С‚РѕР»СЊРєРѕ РґРѕ [MAX_REACTION_LOTTIE_PREVIEWS_PLAYING] С€С‚СѓРє. */
        const val REACTION_PREVIEW_KEEP_ALIVE_MS = 8_000L
    }
}
