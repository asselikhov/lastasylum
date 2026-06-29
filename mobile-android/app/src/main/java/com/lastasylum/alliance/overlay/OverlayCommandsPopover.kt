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
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
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
import androidx.annotation.RawRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.game.GameAutoAssaultBridge
import com.lastasylum.alliance.game.GameMapNavigator
import com.lastasylum.alliance.game.OverlayBookmarkStore
import com.lastasylum.alliance.game.OverlayBookmarkTag
import com.lastasylum.alliance.game.RaidShareTarget
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
    private val sendCoords: suspend (label: String, x: Int, y: Int, serverNumber: Int) -> Result<ChatMessage>,
    private val notifyGameEvent: suspend (eventId: String) -> Result<ChatMessage>,
    private val warmupOverlayRaid: () -> Unit = {},
    private val prepareOptimisticRaidQuickCommand: (label: String, x: Int, y: Int, serverNumber: Int) -> String? =
        { _, _, _, _ -> null },
    private val prepareOptimisticGameEvent: (eventId: String) -> String? = { null },
    private val removeOptimisticRaidSend: (pendingId: String) -> Unit = {},
    private val emitOverlayReaction: (targetUserId: String, reactionId: String) -> Unit = { _, _ -> },
    private val emitOverlayReactionReply: (targetUserId: String, reactionId: String, replyToLogId: String) -> Unit =
        { _, _, _ -> },
    private val emitOverlayReactionBroadcast: (reactionId: String) -> Unit = {},
    private val reshareBookmark: (target: com.lastasylum.alliance.game.RaidShareTarget) -> Unit = {},
) {
    private var hudReactionAnchor: () -> OverlayReactionAnchorRect? = { null }

    internal fun setHudReactionAnchorProvider(provider: () -> OverlayReactionAnchorRect?) {
        hudReactionAnchor = provider
    }

    internal fun setSafeTopMinYProvider(provider: () -> Int?) {
        reactionBurstPresenter.setSafeTopMinYProvider(provider)
    }
    @Volatile
    private var menuScrim: FrameLayout? = null
    @Volatile
    private var coordScrim: FrameLayout? = null
    @Volatile
    private var reactionPickScrim: FrameLayout? = null
    private var assaultPickScrim: FrameLayout? = null
    private var reopenMenuOnReactionsTab = false
    private var reopenReactionSubcategory = OverlayReactionCategory.ANIMATIONS
    private var preselectedReactionUserIds: Set<String> = emptySet()
    private var preselectedReplyToLogId: String? = null
    private var preselectedReplyMode: Boolean = false
    private var popoverCard: View? = null
    private var popoverLayoutListener: View.OnLayoutChangeListener? = null
    private val reactionBurstPresenter = OverlayReactionBurstPresenter(context, mainHandler, dp).also {
        it.setAnchorResolver { resolveReactionBurstAnchor() }
    }
    private var reactionGridScroll: android.widget.ScrollView? = null
    private var reactionRow: LinearLayout? = null
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
    private var menuRevealCategory: ((Boolean) -> Unit)? = null

    fun isShowing(): Boolean =
        surfaceTransitionDepth > 0 ||
            (menuScrim?.visibility == View.VISIBLE) ||
            coordScrim != null ||
            reactionPickScrim != null ||
            assaultPickScrim != null ||
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
        hideAssaultPickOnly()
        hideReactionBurstOnly()
        hideCoordOnly()
        clearPreselectedReactionContext()
        removePopoverLayoutListener()
        val cachedMenu = menuScrim
        if (cachedMenu != null && cachedMenu.isAttachedToWindow) {
            cachedMenu.visibility = View.GONE
        } else {
            removeShell(menuScrim)
            menuScrim = null
            popoverCard = null
            reactionGridScroll = null
            reactionRow = null
            menuRevealCategory = null
        }
        attachedWindowManager = null
        surfaceTransitionDepth = 0
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
        releasePopoverSuppressAfterUiClosed()
    }

    /** Close menu scrim after send without immediately dropping game-gate suppress. */
    private fun hideMenuOnly() {
        stopHeartPreviewPulse()
        reactionBurstPresenter.clear()
        hideReactionPickOnly()
        hideAssaultPickOnly()
        hideReactionBurstOnly()
        hideCoordOnly()
        clearPreselectedReactionContext()
        removePopoverLayoutListener()
        val cachedMenu = menuScrim
        if (cachedMenu != null && cachedMenu.isAttachedToWindow) {
            cachedMenu.visibility = View.GONE
        } else {
            removeShell(menuScrim)
            menuScrim = null
            popoverCard = null
            reactionGridScroll = null
            reactionRow = null
            menuRevealCategory = null
        }
        attachedWindowManager = null
        surfaceTransitionDepth = 0
        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(isOverlayUi = true)
        releasePopoverSuppressAfterUiClosed()
    }

    /** Снять кэш меню (выход из игры / removeOverlayControl). */
    fun destroyCachedShells() {
        removePopoverLayoutListener()
        removeShell(menuScrim)
        menuScrim = null
        popoverCard = null
        reactionGridScroll = null
        reactionRow = null
        menuRevealCategory = null
        hideReactionPickOnly()
        hideAssaultPickOnly()
        hideReactionBurstOnly()
        hideCoordOnly()
        stopHeartPreviewPulse()
        reactionBurstPresenter.clear()
        clearPreselectedReactionContext()
        attachedWindowManager = null
        surfaceTransitionDepth = 0
        clearGameGateSuppress()
    }

    private fun hideReactionPickOnly() {
        removeShell(reactionPickScrim)
        reactionPickScrim = null
        releasePopoverSuppressAfterUiClosed()
    }

    private fun hideAssaultPickOnly() {
        removeShell(assaultPickScrim)
        assaultPickScrim = null
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
        // Generic entrypoint must always start in regular (non-reply) mode.
        clearPreselectedReactionContext()
        ensurePopoverSuppressHeld()
        showMenu(windowManager)
    }

    /** Opens quick commands on the reactions tab without a preselected recipient. */
    fun openReactionsTab(windowManager: WindowManager) {
        clearPreselectedReactionContext()
        reopenMenuOnReactionsTab = true
        if (isShowing()) hide()
        ensurePopoverSuppressHeld()
        showMenu(windowManager)
    }

    /** Opens quick commands on the reactions tab; next recipient picker preselects [userId]. */
    fun openReactionsPreselectUser(
        windowManager: WindowManager,
        userId: String,
        replyToLogId: String? = null,
    ) {
        val id = userId.trim()
        if (id.isEmpty()) return
        preselectedReactionUserIds = setOf(id)
        preselectedReplyToLogId = replyToLogId?.trim()?.takeIf { it.isNotEmpty() }
        preselectedReplyMode = preselectedReplyToLogId != null
        reopenMenuOnReactionsTab = true
        if (isShowing()) hide()
        ensurePopoverSuppressHeld()
        showMenu(windowManager)
    }

    private fun clearPreselectedReactionContext() {
        preselectedReactionUserIds = emptySet()
        preselectedReplyToLogId = null
        preselectedReplyMode = false
    }

    /** РџРѕРєР°Р·Р°С‚СЊ РІСЃРїС‹С€РєСѓ СЂРµР°РєС†РёРё РѕС‚ СЃРѕРєРѕРјР°РЅРґРЅРёРєР° (РїСЂРёС€Р»Р° РїРѕ СЃРѕРєРµС‚Сѓ). */
    /** Anchor below reaction tile grid when popover reactions tab is open. */
    internal fun reactionBurstAnchor(): OverlayReactionAnchorRect? {
        val row = reactionRow ?: return null
        if (row.visibility != View.VISIBLE) return null
        val target = reactionGridScroll?.takeIf { it.visibility == View.VISIBLE } ?: row
        val maxW = popoverCard?.width?.takeIf { it > 0 }
        return OverlayReactionAnchorLayout.anchorFromView(target, HorizontalAlign.CENTER, maxW)
    }

    private fun removePopoverLayoutListener() {
        val card = popoverCard
        val listener = popoverLayoutListener
        if (card != null && listener != null) {
            card.removeOnLayoutChangeListener(listener)
        }
        popoverLayoutListener = null
    }

    private fun attachPopoverLayoutListener(card: View) {
        removePopoverLayoutListener()
        popoverCard = card
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            invalidateReactionBurstAnchor()
        }
        popoverLayoutListener = listener
        card.addOnLayoutChangeListener(listener)
    }

    private fun resolveReactionBurstAnchor(): OverlayReactionAnchorRect? =
        hudReactionAnchor()

    fun invalidateReactionBurstAnchor() {
        reactionBurstPresenter.invalidateReactionBurstAnchor()
    }

    fun showIncomingReactionBurst(
        windowManager: WindowManager,
        fromUserId: String,
        fromUsername: String,
        reactionId: String = "heart",
        broadcast: Boolean = false,
        replyToLog: com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo? = null,
    ) {
        attachedWindowManager = windowManager
        acquireGameGateSuppress()
        reactionBurstPresenter.enqueue(
            windowManager,
            OverlayReactionBurstRequest(
                fromUserId = fromUserId,
                fromDisplayName = fromUsername,
                reactionId = reactionId,
                broadcast = broadcast,
                replyToLog = replyToLog,
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

    private fun primarySendButtonBackground(): RippleDrawable =
        rippleOn(
            roundedRect(
                fillColor = Color.parseColor("#FF3A4EC8"),
                cornerDp = 8,
            ),
        )

    private data class CommandOption(val labelDisplayRes: Int, val labelCommandRes: Int)

    private data class CommandCategory(
        val titleRes: Int,
        val shortLabelRes: Int,
        val icon: ImageVector,
        val accentColor: Int,
        val options: List<CommandOption>? = null,
        val hintRes: Int? = null,
        val isPush: Boolean = false,
        val isReactions: Boolean = false,
        val isTarget: Boolean = false,
    )

    /** Compact numeric field (label + EditText) reused by the Цель → Поиск coordinate block. */
    private fun coordFieldView(
        hint: String,
        defaultText: String? = null,
        signed: Boolean = true,
    ): Pair<LinearLayout, EditText> {
        val label = labelText(hint, 10f, Color.parseColor("#FF8FAEFF"), bold = true)
        val edit = EditText(context).apply {
            setHint("0")
            setTextColor(Color.parseColor("#FFF4F7FF"))
            setHintTextColor(Color.parseColor("#558899AA"))
            inputType = InputType.TYPE_CLASS_NUMBER or
                if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0
            isSingleLine = true
            maxLines = 1
            background = fieldBackground()
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            defaultText?.let { setText(it) }
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

    /**
     * ScrollView с ограничением высоты: когда [maxHeightPx] > 0, контент выше лимита
     * прокручивается (как лента реакций). Используется для списка закладок свыше 5 записей.
     */
    private inner class MaxHeightScrollView(ctx: Context) : ScrollView(ctx) {
        var maxHeightPx: Int = 0
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val spec = if (maxHeightPx > 0) {
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            } else {
                heightMeasureSpec
            }
            super.onMeasure(widthMeasureSpec, spec)
        }
    }

    /** Карточка цели в табе «Закладки»: координаты + строка-инфо (как при шаринге); тап — меню действий. */
    private fun buildBookmarkCardView(
        target: RaidShareTarget,
        tag: OverlayBookmarkTag,
        onChanged: () -> Unit,
    ): View {
        val coords = TextView(context).apply {
            text = buildString {
                append("[")
                if (target.serverNumber != null) append("S:").append(target.serverNumber).append(" ")
                append("X:").append(target.x).append(" Y:").append(target.y)
                append("]")
            }
            setTextColor(Color.parseColor("#FF7DD3FC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        }
        // Одна инфо-строка: Ур.N + имя/[тег]Ник (+ грейд/звёзды сундука) + мощь/поверженные
        // с игровыми иконками через пробел (как в карточке «В рейд»).
        val meta = TextView(context).apply {
            text = buildBookmarkInfoSpannable(target)
            setTextColor(Color.parseColor("#FFE2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(2), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rippleOn(
                GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#18FFFFFF"))
                    setStroke(dp(1), Color.parseColor("#22507090"))
                },
            )
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) }
            addView(coords)
            addView(meta)
            setOnClickListener { showBookmarkActions(this, target, tag, onChanged) }
        }
    }

    /** Инфо-строка карточки закладки: уровень, имя/[тег]Ник, цветной грейд/звёзды сундука, мощь/поверженные. */
    private fun buildBookmarkInfoSpannable(target: RaidShareTarget): CharSequence {
        val builder = SpannableStringBuilder()
        fun sep() { if (builder.isNotEmpty()) builder.append(' ') }
        target.levelPrefix()?.let { sep(); builder.append(it) }
        sep(); builder.append(target.titleLine())
        target.metaPartsForOverlay().forEach { part -> sep(); builder.append(part) }
        // Цвет грейда/звёзд сундука — как при шаринге.
        val badge = target.chestGradeStars()
        val gradeColor = bookmarkGradeColor(target.grade)
        if (badge != null && gradeColor != null) {
            val start = builder.toString().indexOf(badge)
            if (start >= 0) {
                builder.setSpan(ForegroundColorSpan(gradeColor), start, start + badge.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, start + badge.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // Только мощь с иконкой — на той же строке, через реальный пробел после имени
        // (одиночный пробел под иконкой «съедается» ImageSpan, поэтому добавляем отдельный).
        // Поверженные в закладках не показываем — экономим место.
        target.powerLabel()?.let { label ->
            if (builder.isNotEmpty()) builder.append(' ')
            appendBookmarkStatIcon(builder, label, target.powerIcon, R.drawable.ic_overlay_game_power)
        }
        return builder
    }

    private fun bookmarkGradeColor(grade: Int?): Int? = when (grade) {
        3 -> Color.parseColor("#FF60A5FA")
        4 -> Color.parseColor("#FFC084FC")
        5 -> Color.parseColor("#FFFBBF24")
        else -> null
    }

    private fun appendBookmarkStatIcon(
        builder: SpannableStringBuilder,
        label: String,
        gameSprite: String?,
        fallbackDrawable: Int,
    ) {
        val key = gameSprite?.lowercase().orEmpty()
        val drawableRes = when {
            key.contains("zhanli") || key.contains("power") || key.contains("shili") -> R.drawable.ic_overlay_game_power
            key.contains("jisha") || key.contains("kill") -> R.drawable.ic_overlay_game_kills
            else -> fallbackDrawable
        }
        val drawable = context.getDrawable(drawableRes) ?: return
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

    /** Меню действий по закладке: перелёт / отправить в «Рейд» / удалить. */
    private fun showBookmarkActions(
        anchor: View,
        target: RaidShareTarget,
        tag: OverlayBookmarkTag,
        onChanged: () -> Unit,
    ) {
        val popupContext = OverlayTickerUi.themedFabContext(context)
        val labels = listOf(
            context.getString(R.string.overlay_bookmark_action_fly),
            context.getString(R.string.overlay_bookmark_action_reshare),
            context.getString(R.string.overlay_bookmark_action_delete),
        )
        val adapter = object : android.widget.ArrayAdapter<String>(
            popupContext,
            android.R.layout.simple_list_item_1,
            labels,
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = (convertView as? TextView) ?: TextView(popupContext).apply {
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                }
                row.text = getItem(position)
                val delete = position == 2
                row.setTextColor(Color.parseColor(if (delete) "#FFFF8A8A" else "#FFE8F4FF"))
                row.typeface = Typeface.DEFAULT
                return row
            }
        }
        val popup = android.widget.ListPopupWindow(popupContext).apply {
            anchorView = anchor
            setAdapter(adapter)
            width = anchor.width.coerceAtLeast(dp(200))
            isModal = true
            inputMethodMode = android.widget.ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            setBackgroundDrawable(
                roundedRect(
                    fillColor = Color.parseColor("#F0141C28"),
                    strokeColor = Color.parseColor("#3D5A7CAA"),
                    cornerDp = 10,
                ),
            )
            setOnItemClickListener { _, _, position, _ ->
                dismiss()
                when (position) {
                    0 -> {
                        val server = target.serverNumber ?: DEFAULT_COORD_SERVER
                        GameMapNavigator.open(context, target.x, target.y, server)
                        hide()
                    }
                    1 -> reshareBookmark(target)
                    2 -> {
                        OverlayBookmarkStore.remove(context, tag, target)
                        onChanged()
                    }
                }
            }
        }
        anchor.post {
            if (!anchor.isAttachedToWindow) return@post
            runCatching { popup.show() }
        }
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
        val icon = OverlayMaterialIconHost(context, iconSize).apply {
            setIcon(
                category.icon,
                if (selected) Color.WHITE else Color.parseColor("#C8D4E4"),
            )
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

    private fun showMenu(windowManager: WindowManager) {
        ensurePopoverSuppressHeld()
        scope.launch(Dispatchers.IO) {
            val container = AppContainer.from(context)
            val uid = container.usersRepository.peekMyProfile()?.id?.trim().orEmpty()
            if (uid.isNotEmpty()) {
                OverlayTeamContextCache.hydrateFromDisk(
                    uid,
                    container.usersRepository,
                    container.launchDiskCache,
                )
            }
        }
        GameAutoAssaultBridge.sync(context)
        menuScrim?.takeIf { it.isAttachedToWindow }?.let { cached ->
            cached.visibility = View.VISIBLE
            attachedWindowManager = windowManager
            val reopenOnReactions = reopenMenuOnReactionsTab
            reopenMenuOnReactionsTab = false
            menuRevealCategory?.invoke(reopenOnReactions)
            return
        }
        val screenW = context.resources.displayMetrics.widthPixels
        val popoverW = minOf(dp(328), screenW - dp(16))

        val categories = listOf(
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_target,
                shortLabelRes = R.string.overlay_cmd_tab_target,
                icon = OverlayQuickCommandIcons.target,
                accentColor = Color.parseColor("#FF26A69A"),
                isTarget = true,
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_push,
                shortLabelRes = R.string.overlay_cmd_tab_push,
                icon = OverlayQuickCommandIcons.push,
                accentColor = Color.parseColor("#FF5C6BC0"),
                hintRes = R.string.overlay_cmd_push_hint,
                isPush = true,
            ),
            CommandCategory(
                titleRes = R.string.overlay_cmd_column_reactions,
                shortLabelRes = R.string.overlay_cmd_tab_reactions,
                icon = OverlayQuickCommandIcons.reactions,
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

        val optionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        val optionChips = mutableListOf<TextView>()

        // ---- Цель (Target) section: Поиск / Закладки / Штурм sub-tabs -------------------------
        var selectedTargetTab = 0 // 0 = Поиск, 1 = Закладки, 2 = Штурм
        var selectedBookmark = OverlayBookmarkTag.ENEMIES

        val assaultPrefs = UserSettingsPreferences(context)
        var assaultEnabled = assaultPrefs.isAutoAssaultEnabledRaw()
        var assaultSquads = assaultPrefs.getAutoAssaultSquads().toMutableSet()
        var assaultAllowedIds = assaultPrefs.getAutoAssaultAllowedMemberIds().toMutableSet()
        val assaultTypes = assaultPrefs.getAutoAssaultTargetTypes().toMutableSet()

        fun assaultAlliesLabel(): String =
            if (assaultAllowedIds.isEmpty()) {
                context.getString(R.string.overlay_assault_allies_all)
            } else {
                context.getString(R.string.overlay_assault_allies_count, assaultAllowedIds.size)
            }

        fun persistAssaultSettings() {
            assaultPrefs.setAutoAssaultSquads(assaultSquads)
            assaultPrefs.setAutoAssaultAllowedMemberIds(assaultAllowedIds)
            assaultPrefs.setAutoAssaultTargetTypes(assaultTypes)
            // setAutoAssaultEnabled пересчитывает срок авто-выключения по текущей длительности.
            assaultPrefs.setAutoAssaultEnabled(assaultEnabled)
            GameAutoAssaultBridge.write(context, assaultPrefs)
        }

        val targetSearchTabChip = choiceChip(context.getString(R.string.overlay_target_tab_search), true)
        val targetBookmarksTabChip = choiceChip(context.getString(R.string.overlay_target_tab_bookmarks), false)
        val targetAssaultTabChip = choiceChip(context.getString(R.string.overlay_target_tab_assault), false)
        val targetSubTabsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            addView(
                targetSearchTabChip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) },
            )
            addView(
                targetBookmarksTabChip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) },
            )
            addView(targetAssaultTabChip)
        }

        // Поиск: server / X / Y inputs + a search-icon fly button.
        val (colTargetServer, editTargetServer) = coordFieldView(
            hint = context.getString(R.string.overlay_coord_server_label),
            defaultText = DEFAULT_COORD_SERVER.toString(),
            signed = false,
        )
        val (colTargetX, editTargetX) = coordFieldView(context.getString(R.string.overlay_coord_x_label))
        val (colTargetY, editTargetY) = coordFieldView(context.getString(R.string.overlay_coord_y_label))
        val targetCoordsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val gap = dp(6)
            addView(colTargetServer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.85f))
            addView(
                colTargetX,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = gap },
            )
            addView(
                colTargetY,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = gap },
            )
        }
        val targetSearchIcon = OverlayMaterialIconHost(context, dp(18)).apply {
            setIcon(OverlayQuickCommandIcons.search, Color.WHITE)
        }
        val targetSearchLabel = labelText(
            context.getString(R.string.overlay_target_fly_btn),
            12.5f,
            Color.parseColor("#FFF8FAFF"),
            bold = true,
        )
        val targetSearchButton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(38)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = primarySendButtonBackground()
            isClickable = true
            contentDescription = context.getString(R.string.overlay_target_search_cd)
            addView(targetSearchIcon, LinearLayout.LayoutParams(dp(18), dp(18)))
            addView(
                targetSearchLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(8) },
            )
        }
        val targetSearchContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                targetCoordsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                targetSearchButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) },
            )
        }

        // Закладки: dropdown of bookmark categories + target list (empty for now).
        val bookmarkSelectorLabel = labelText(
            context.getString(selectedBookmark.labelRes),
            12.5f,
            Color.parseColor("#FFE8F4FF"),
            bold = true,
        )
        val bookmarkSelectorChevron = ImageView(context).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, R.drawable.ic_overlay_ui_expand)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor("#8AA0B8D0"))
                },
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val bookmarkSelector = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(36)
            setPadding(dp(12), dp(8), dp(10), dp(8))
            background = rippleOn(fieldBackground())
            isClickable = true
            addView(
                bookmarkSelectorLabel,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(bookmarkSelectorChevron, LinearLayout.LayoutParams(dp(18), dp(18)))
        }
        val bookmarkEmpty = labelText(
            context.getString(R.string.overlay_bookmarks_empty),
            11f,
            Color.parseColor("#7A90A4B8"),
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(18))
        }
        val bookmarkListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bookmarkEmpty)
        }
        // Список закладок свыше 5 записей прокручивается внутри ограниченной по высоте области.
        val bookmarkListScroll = MaxHeightScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                bookmarkListContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val targetBookmarksContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                bookmarkSelector,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                bookmarkListScroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
        }

        fun assaultNumField(initial: String, hintText: String): EditText =
            EditText(context).apply {
                setText(initial)
                hint = hintText
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(Color.parseColor("#FFF8FAFF"))
                setHintTextColor(Color.parseColor("#6A8098B0"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = fieldBackground()
                filters = arrayOf(InputFilter.LengthFilter(12))
            }

        val assaultPowerMinEdits = Array(3) { idx ->
            assaultNumField(
                assaultPrefs.getAutoAssaultSquadPowerMin(idx).toString(),
                context.getString(R.string.overlay_assault_power_hint_example),
            )
        }
        val assaultPowerMaxEdits = Array(3) { idx ->
            assaultNumField(
                assaultPrefs.getAutoAssaultSquadPowerMax(idx).toString(),
                context.getString(R.string.overlay_assault_power_max_hint),
            )
        }
        val assaultDistanceEdit = assaultNumField(
            assaultPrefs.getAutoAssaultMaxDistance().toString(),
            "",
        )
        val assaultLevelMinEdit = assaultNumField(
            assaultPrefs.getAutoAssaultTargetLevelMin().takeIf { it > 0 }?.toString() ?: "",
            context.getString(R.string.overlay_assault_level_min_hint),
        )
        val assaultLevelMaxEdit = assaultNumField(
            assaultPrefs.getAutoAssaultTargetLevelMax().takeIf { it > 0 }?.toString() ?: "",
            context.getString(R.string.overlay_assault_level_max_hint),
        )
        val assaultMinRemainingEdit = assaultNumField(
            assaultPrefs.getAutoAssaultMinRemainingSec().toString(),
            "",
        )
        val assaultCooldownEdit = assaultNumField(
            assaultPrefs.getAutoAssaultCooldownSec().toString(),
            "",
        )
        val assaultMaxConcurrentEdit = assaultNumField(
            assaultPrefs.getAutoAssaultMaxConcurrent().toString(),
            "",
        )
        val assaultDurationEdit = assaultNumField(
            assaultPrefs.getAutoAssaultDurationMin().toString(),
            "",
        )

        fun readAssaultPowerPrefs() {
            for (idx in 0 until 3) {
                val min = assaultPowerMinEdits[idx].text?.toString()?.trim()?.toLongOrNull() ?: 0L
                val max = assaultPowerMaxEdits[idx].text?.toString()?.trim()?.toLongOrNull()
                    ?: UserSettingsPreferences.AUTO_ASSAULT_POWER_MAX_DEFAULT
                assaultPrefs.setAutoAssaultSquadPowerMin(idx, min)
                assaultPrefs.setAutoAssaultSquadPowerMax(idx, max)
            }
            val dist = assaultDistanceEdit.text?.toString()?.trim()?.toIntOrNull()
                ?: UserSettingsPreferences.AUTO_ASSAULT_MAX_DISTANCE_DEFAULT
            assaultPrefs.setAutoAssaultMaxDistance(dist)
            assaultPrefs.setAutoAssaultTargetLevelMin(
                assaultLevelMinEdit.text?.toString()?.trim()?.toIntOrNull() ?: 0,
            )
            assaultPrefs.setAutoAssaultTargetLevelMax(
                assaultLevelMaxEdit.text?.toString()?.trim()?.toIntOrNull() ?: 0,
            )
            assaultPrefs.setAutoAssaultMinRemainingSec(
                assaultMinRemainingEdit.text?.toString()?.trim()?.toIntOrNull()
                    ?: UserSettingsPreferences.AUTO_ASSAULT_MIN_REMAINING_DEFAULT_SEC,
            )
            assaultPrefs.setAutoAssaultCooldownSec(
                assaultCooldownEdit.text?.toString()?.trim()?.toIntOrNull()
                    ?: UserSettingsPreferences.AUTO_ASSAULT_COOLDOWN_DEFAULT_SEC,
            )
            assaultPrefs.setAutoAssaultMaxConcurrent(
                assaultMaxConcurrentEdit.text?.toString()?.trim()?.toIntOrNull() ?: 0,
            )
            assaultPrefs.setAutoAssaultDurationMin(
                assaultDurationEdit.text?.toString()?.trim()?.toIntOrNull() ?: 0,
            )
        }

        fun assaultLabeledRow(labelRes: Int, field: EditText): LinearLayout =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    labelText(
                        context.getString(labelRes),
                        11.5f,
                        Color.parseColor("#9AB0C4D8"),
                    ),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f),
                )
                addView(
                    field,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }

        val assaultSwitch = SwitchCompat(context).apply {
            isChecked = assaultEnabled
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList.valueOf(Color.parseColor("#5538BDF8"))
        }
        val assaultSwitchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                labelText(
                    context.getString(R.string.overlay_assault_enabled),
                    13f,
                    Color.parseColor("#FFF4F7FF"),
                    bold = true,
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(assaultSwitch)
        }
        assaultSwitch.setOnCheckedChangeListener { _, on ->
            assaultEnabled = on
            persistAssaultSettings()
        }

        val assaultSquadChips = (0 until 3).map { idx ->
            choiceChip(
                context.getString(R.string.overlay_assault_squad_label, idx + 1),
                assaultSquads.contains(idx),
            )
        }
        val assaultSquadsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            addView(
                labelText(
                    context.getString(R.string.overlay_assault_squads),
                    11.5f,
                    Color.parseColor("#9AB0C4D8"),
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(8) },
            )
            assaultSquadChips.forEachIndexed { idx, chip ->
                addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { if (idx > 0) marginStart = dp(6) },
                )
                chip.setOnClickListener {
                    if (assaultSquads.contains(idx)) assaultSquads.remove(idx) else assaultSquads.add(idx)
                    if (assaultSquads.isEmpty()) assaultSquads.add(idx)
                    val selected = assaultSquads.contains(idx)
                    chip.background = optionChipBackground(selected)
                    chip.setTextColor(Color.parseColor(if (selected) "#FFE8F4FF" else "#9AB0C4D8"))
                    chip.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    persistAssaultSettings()
                }
            }
        }

        val assaultPowerTitle = labelText(
            context.getString(R.string.overlay_assault_power_title),
            11.5f,
            Color.parseColor("#9AB0C4D8"),
        )
        val assaultPowerRows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            for (idx in 0 until 3) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        labelText(
                            context.getString(R.string.overlay_assault_squad_label, idx + 1),
                            11f,
                            Color.parseColor("#C8DCE8F4"),
                        ),
                        LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT),
                    )
                    addView(
                        assaultPowerMinEdits[idx],
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(
                        assaultPowerMaxEdits[idx],
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            .apply { marginStart = dp(6) },
                    )
                }
                addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { if (idx > 0) topMargin = dp(6) },
                )
            }
        }

        val assaultDistanceRow = assaultLabeledRow(R.string.overlay_assault_max_distance, assaultDistanceEdit)

        // ---- Тип цели: монстры / игроки / города (multi-select) ----
        val assaultTypeOrder = listOf(
            UserSettingsPreferences.AUTO_ASSAULT_TYPE_MONSTER to R.string.overlay_assault_type_monster,
            UserSettingsPreferences.AUTO_ASSAULT_TYPE_PLAYER to R.string.overlay_assault_type_player,
            UserSettingsPreferences.AUTO_ASSAULT_TYPE_CITY to R.string.overlay_assault_type_city,
        )
        val assaultTypesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            addView(
                labelText(
                    context.getString(R.string.overlay_assault_types_title),
                    11.5f,
                    Color.parseColor("#9AB0C4D8"),
                ),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(8) },
            )
            assaultTypeOrder.forEachIndexed { i, (typeKey, labelRes) ->
                val chip = choiceChip(context.getString(labelRes), assaultTypes.contains(typeKey))
                addView(
                    chip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { if (i > 0) marginStart = dp(6) },
                )
                chip.setOnClickListener {
                    if (assaultTypes.contains(typeKey)) assaultTypes.remove(typeKey) else assaultTypes.add(typeKey)
                    if (assaultTypes.isEmpty()) assaultTypes.add(typeKey)
                    val sel = assaultTypes.contains(typeKey)
                    chip.background = optionChipBackground(sel)
                    chip.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#9AB0C4D8"))
                    chip.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    persistAssaultSettings()
                }
            }
        }

        val assaultLevelTitle = labelText(
            context.getString(R.string.overlay_assault_level_title),
            11.5f,
            Color.parseColor("#9AB0C4D8"),
        )
        val assaultLevelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                assaultLevelMinEdit,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                assaultLevelMaxEdit,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(6) },
            )
        }

        val assaultMinRemainingRow = assaultLabeledRow(R.string.overlay_assault_min_remaining, assaultMinRemainingEdit)
        val assaultCooldownRow = assaultLabeledRow(R.string.overlay_assault_cooldown, assaultCooldownEdit)
        val assaultMaxConcurrentRow = assaultLabeledRow(R.string.overlay_assault_max_concurrent, assaultMaxConcurrentEdit)
        val assaultDurationRow = assaultLabeledRow(R.string.overlay_assault_duration, assaultDurationEdit)

        val assaultAlliesLabel = labelText(
            assaultAlliesLabel(),
            12.5f,
            Color.parseColor("#FFF8FAFF"),
            bold = true,
        )
        val assaultAlliesButton = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            minimumHeight = dp(36)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rippleOn(fieldBackground())
            isClickable = true
            addView(assaultAlliesLabel)
        }

        // ---- Лог последних авто-вступлений ----
        val assaultLogContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun refreshAssaultLog() {
            assaultLogContainer.removeAllViews()
            val entries = assaultPrefs.getAutoAssaultJoinLog()
            if (entries.isEmpty()) {
                assaultLogContainer.addView(
                    labelText(
                        context.getString(R.string.overlay_assault_log_empty),
                        11f,
                        Color.parseColor("#7A90A4B8"),
                    ).apply { setPadding(0, dp(6), 0, dp(6)) },
                )
                return
            }
            entries.forEach { entry ->
                assaultLogContainer.addView(
                    labelText(entry, 11f, Color.parseColor("#C8DCE8F4")).apply {
                        setPadding(0, dp(3), 0, dp(3))
                    },
                )
            }
        }
        refreshAssaultLog()
        val assaultLogHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                labelText(
                    context.getString(R.string.overlay_assault_log_title),
                    11.5f,
                    Color.parseColor("#9AB0C4D8"),
                ),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                labelText(
                    context.getString(R.string.overlay_assault_log_clear),
                    11f,
                    Color.parseColor("#FF7DD3FC"),
                ).apply {
                    setPadding(dp(8), dp(4), dp(4), dp(4))
                    isClickable = true
                    setOnClickListener {
                        assaultPrefs.clearAutoAssaultJoinLog()
                        refreshAssaultLog()
                    }
                },
            )
        }

        val assaultInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(assaultSwitchRow)
            fun spaced(v: View, top: Int = 10) = addView(
                v,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(top) },
            )
            spaced(assaultTypesRow)
            spaced(assaultSquadsRow)
            spaced(assaultPowerTitle)
            spaced(assaultPowerRows, 6)
            spaced(assaultLevelTitle)
            spaced(assaultLevelRow, 6)
            spaced(assaultDistanceRow)
            spaced(assaultMinRemainingRow, 8)
            spaced(assaultCooldownRow, 8)
            spaced(assaultMaxConcurrentRow, 8)
            spaced(assaultDurationRow, 8)
            spaced(assaultAlliesButton)
            spaced(assaultLogHeader)
            spaced(assaultLogContainer, 2)
        }
        val assaultContent = MaxHeightScrollView(context).apply {
            visibility = View.GONE
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            maxHeightPx = dp(320)
            addView(
                assaultInner,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        assaultAlliesButton.setOnClickListener {
            readAssaultPowerPrefs()
            persistAssaultSettings()
            showAssaultAlliesPicker(windowManager, assaultAllowedIds) { picked ->
                assaultAllowedIds = picked.toMutableSet()
                assaultAlliesLabel.text = assaultAlliesLabel()
                persistAssaultSettings()
            }
        }

        fun watchAssaultField(edit: EditText) {
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    readAssaultPowerPrefs()
                    persistAssaultSettings()
                }
            })
        }
        watchAssaultField(assaultDistanceEdit)
        watchAssaultField(assaultLevelMinEdit)
        watchAssaultField(assaultLevelMaxEdit)
        watchAssaultField(assaultMinRemainingEdit)
        watchAssaultField(assaultCooldownEdit)
        watchAssaultField(assaultMaxConcurrentEdit)
        watchAssaultField(assaultDurationEdit)
        for (idx in 0 until 3) {
            watchAssaultField(assaultPowerMinEdits[idx])
            watchAssaultField(assaultPowerMaxEdits[idx])
        }

        val targetHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                targetSubTabsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                targetSearchContent,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) },
            )
            addView(
                targetBookmarksContent,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) },
            )
            addView(
                assaultContent,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) },
            )
        }

        fun refreshBookmarkList() {
            bookmarkSelectorLabel.text = context.getString(selectedBookmark.labelRes)
            bookmarkListContainer.removeAllViews()
            val items = OverlayBookmarkStore.list(context, selectedBookmark)
            if (items.isEmpty()) {
                bookmarkListScroll.maxHeightPx = 0
                bookmarkEmpty.visibility = View.VISIBLE
                bookmarkListContainer.addView(bookmarkEmpty)
                return
            }
            // Высота примерно 5 карточек; свыше — включается прокрутка. Иначе высота по содержимому.
            bookmarkListScroll.maxHeightPx = if (items.size > BOOKMARK_VISIBLE_LIMIT) {
                dp(BOOKMARK_CARD_HEIGHT_DP * BOOKMARK_VISIBLE_LIMIT)
            } else {
                0
            }
            bookmarkListScroll.scrollTo(0, 0)
            items.forEach { t ->
                bookmarkListContainer.addView(
                    buildBookmarkCardView(t, selectedBookmark) { refreshBookmarkList() },
                )
            }
        }

        fun openBookmarkPicker() {
            val popupContext = OverlayTickerUi.themedFabContext(context)
            val entries = OverlayBookmarkTag.entries
            val titles = entries.map { context.getString(it.labelRes) }
            val adapter = object : android.widget.ArrayAdapter<String>(
                popupContext,
                android.R.layout.simple_list_item_1,
                titles,
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val row = (convertView as? TextView) ?: TextView(popupContext).apply {
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    }
                    val sel = entries[position] == selectedBookmark
                    row.text = getItem(position)
                    row.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#C8DCE8F4"))
                    row.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    row.setBackgroundColor(if (sel) Color.parseColor("#332A4558") else Color.TRANSPARENT)
                    return row
                }
            }
            val listPopup = android.widget.ListPopupWindow(popupContext).apply {
                anchorView = bookmarkSelector
                setAdapter(adapter)
                width = bookmarkSelector.width.coerceAtLeast(dp(200))
                isModal = true
                inputMethodMode = android.widget.ListPopupWindow.INPUT_METHOD_NOT_NEEDED
                setBackgroundDrawable(
                    roundedRect(
                        fillColor = Color.parseColor("#F0141C28"),
                        strokeColor = Color.parseColor("#3D5A7CAA"),
                        cornerDp = 10,
                    ),
                )
                setOnItemClickListener { _, _, position, _ ->
                    val picked = entries.getOrNull(position)
                    dismiss()
                    if (picked != null && picked != selectedBookmark) {
                        selectedBookmark = picked
                        refreshBookmarkList()
                    }
                }
            }
            bookmarkSelector.post {
                if (!bookmarkSelector.isAttachedToWindow) return@post
                runCatching { listPopup.show() }
            }
        }
        bookmarkSelector.setOnClickListener { openBookmarkPicker() }

        fun refreshTargetSubTabs() {
            listOf(
                targetSearchTabChip to 0,
                targetBookmarksTabChip to 1,
                targetAssaultTabChip to 2,
            ).forEach { (chip, idx) ->
                val sel = selectedTargetTab == idx
                chip.background = optionChipBackground(sel)
                chip.setTextColor(Color.parseColor(if (sel) "#FFE8F4FF" else "#9AB0C4D8"))
                chip.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            targetSearchContent.visibility = if (selectedTargetTab == 0) View.VISIBLE else View.GONE
            targetBookmarksContent.visibility = if (selectedTargetTab == 1) View.VISIBLE else View.GONE
            assaultContent.visibility = if (selectedTargetTab == 2) View.VISIBLE else View.GONE
        }

        fun selectTargetTab(index: Int) {
            selectedTargetTab = index
            if (index == 1) refreshBookmarkList()
            if (index == 2) {
                readAssaultPowerPrefs()
                persistAssaultSettings()
                refreshAssaultLog()
            }
            refreshTargetSubTabs()
        }
        targetSearchTabChip.setOnClickListener { selectTargetTab(0) }
        targetBookmarksTabChip.setOnClickListener { selectTargetTab(1) }
        targetAssaultTabChip.setOnClickListener { selectTargetTab(2) }

        targetSearchButton.setOnClickListener {
            val server = editTargetServer.text?.toString()?.trim()?.toIntOrNull()
            val xv = editTargetX.text?.toString()?.trim()?.toIntOrNull()
            val yv = editTargetY.text?.toString()?.trim()?.toIntOrNull()
            if (server == null || server !in MIN_COORD_SERVER..MAX_COORD_SERVER || xv == null || yv == null) {
                Toast.makeText(context, R.string.overlay_coord_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            hideKeyboard(editTargetServer)
            hideKeyboard(editTargetX)
            hideKeyboard(editTargetY)
            GameMapNavigator.open(context, xv, yv, server)
            hide()
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
                selectedStickerPackKey = tabs.firstOrNull()?.packKey ?: OVERLAY_REACTION_STICKER_PACK
            }
            reactionStickerPackPicker.bind(tabs, selectedStickerPackKey)
        }

        fun updateStickerPackPickerVisibility() {
            val show = overlayShouldShowStickerPackPicker(
                isReactionsCategory = categories.getOrNull(selectedCategoryIndex)?.isReactions == true,
                subcategory = selectedReactionSubcategory,
            )
            if (show) {
                refreshStickerPackPicker()
                reactionStickerPackPicker.root.visibility = View.VISIBLE
            } else {
                reactionStickerPackPicker.dismissPicker()
                reactionStickerPackPicker.root.visibility = View.GONE
            }
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

        reactionGridScroll = ScrollView(context).apply {
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

        rebuildReactionTiles = fun() {
            val isTextTab = selectedReactionSubcategory == OverlayReactionCategory.TEXT
            updateStickerPackPickerVisibility()
            reactionGridScroll?.visibility = if (isTextTab) View.GONE else View.VISIBLE
            reactionGridScroll?.post { invalidateReactionBurstAnchor() }
            reactionTextPanel.visibility = if (isTextTab) View.VISIBLE else View.GONE
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
                loadEnabledStickerPackKeys()
                refreshStickerPackPicker()
                OverlayReactionBitmapCache.preloadStickerPack(context, selectedStickerPackKey)
            }
            if (cat == OverlayReactionCategory.TEXT) {
                stopReactionPreviewKeepAlive()
                reactionStickerPackPicker.dismissPicker()
            }
            updateStickerPackPickerVisibility()
            refreshReactionSubTabs()
            scheduleReactionTilesRebuild()
        }

        reactionSubAnimChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.ANIMATIONS) }
        reactionSubMemeChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.MEMES) }
        reactionSubStickerChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.STICKERS) }
        reactionSubTextChip.setOnClickListener { selectReactionSubcategory(OverlayReactionCategory.TEXT) }

        refreshStickerPackPicker()
        rebuildReactionTiles()

        reactionRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            visibility = View.GONE
            addView(
                reactionSubTabsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
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

        val overlayService = context as CombatOverlayService
        val popoverComposeOwner = overlayService.obtainOverlayPopoverComposeOwner()
        val gameEventPushCompose = ComposeView(context)
        val gameEventPushHost = FrameLayout(context).apply {
            visibility = View.GONE
            addView(
                gameEventPushCompose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
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
                val icon = iconHost?.getChildAt(0) as? OverlayMaterialIconHost
                icon?.setIcon(
                    cat.icon,
                    if (sel) Color.WHITE else Color.parseColor("#C8D4E4"),
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
            rebuildOptionsForCategory(cat)
            if (cat.isTarget) {
                stopHeartPreviewPulse()
                targetHost.visibility = View.VISIBLE
                gameEventPushHost.visibility = View.GONE
                reactionRow?.visibility = View.GONE
                reactionStickerPackPicker.dismissPicker()
                reactionStickerPackPicker.root.visibility = View.GONE
                refreshTargetSubTabs()
                invalidateReactionBurstAnchor()
            } else if (cat.isReactions) {
                ensurePopoverSuppressHeld()
                targetHost.visibility = View.GONE
                gameEventPushHost.visibility = View.GONE
                reactionRow?.visibility = View.VISIBLE
                updateStickerPackPickerVisibility()
                startReactionStripPreviews()
                reactionRow?.post { invalidateReactionBurstAnchor() }
            } else if (cat.isPush) {
                stopHeartPreviewPulse()
                targetHost.visibility = View.GONE
                reactionRow?.visibility = View.GONE
                gameEventPushHost.visibility = View.VISIBLE
                reactionStickerPackPicker.dismissPicker()
                reactionStickerPackPicker.root.visibility = View.GONE
                invalidateReactionBurstAnchor()
            } else {
                stopHeartPreviewPulse()
                targetHost.visibility = View.GONE
                gameEventPushHost.visibility = View.GONE
                reactionRow?.visibility = View.GONE
                reactionStickerPackPicker.dismissPicker()
                reactionStickerPackPicker.root.visibility = View.GONE
                invalidateReactionBurstAnchor()
            }
            if (!cat.isReactions && !cat.isTarget) {
                warmupOverlayRaid()
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
            View(context),
            LinearLayout.LayoutParams(0, 0, 1f),
        )
        titleRow.addView(
            reactionStickerPackPicker.root,
            LinearLayout.LayoutParams(
                dp(152),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(6) },
        )
        bodyColumn.addView(titleRow)
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
            gameEventPushHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(8) },
        )
        bodyColumn.addView(
            targetHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        close.setOnClickListener { hide() }

        val reactionsIndex = categories.indexOfFirst { it.isReactions }
        menuRevealCategory = { reopenOnReactions ->
            if (reopenOnReactions && reactionsIndex >= 0) {
                applyCategory(reactionsIndex)
                selectReactionSubcategory(reopenReactionSubcategory)
            } else {
                applyCategory(0)
            }
        }
        val reopenOnReactions = reopenMenuOnReactionsTab
        reopenMenuOnReactionsTab = false
        menuRevealCategory?.invoke(reopenOnReactions)

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#288899AA"))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            elevation = dp(10).toFloat()
            background = panelShellBackground()
            addView(
                headerRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
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
            addView(
                tabsRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                bodyColumn,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
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

        overlayService.attachOverlayComposeTree(scrim, card, gameEventPushHost, gameEventPushCompose)
        gameEventPushCompose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides popoverComposeOwner,
                LocalViewModelStoreOwner provides popoverComposeOwner,
                LocalSavedStateRegistryOwner provides popoverComposeOwner,
                LocalOnBackPressedDispatcherOwner provides popoverComposeOwner,
            ) {
                SquadRelayTheme {
                    OverlayGameEventPushPanel(
                        onNotify = { event ->
                            val pendingId = prepareOptimisticGameEvent(event.id)
                            CombatOverlayService.extendInGameOverlayUiHold()
                            hideMenuOnly()
                            scope.launch {
                                CombatOverlayService.warmupRaidForQuickCommandSend()
                                val result = notifyGameEvent(event.id)
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
                        },
                    )
                }
            }
        }

        ensurePopoverSuppressHeld()
        menuScrim = scrim
        attachPopoverLayoutListener(card)
        attachedWindowManager = windowManager
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
        val overlayService = context as CombatOverlayService
        prefetchOverlayReactionRecipients(
            scope = scope,
            usersRepository = container.usersRepository,
            teamsRepository = container.teamsRepository,
            launchDiskCache = container.launchDiskCache,
        )
        overlayService.ensureOverlaySessionPresenceStartedForRecipients()
        val cardW = minOf(dp(360), context.resources.displayMetrics.widthPixels - dp(16))
        val composeOwner = overlayService.obtainOverlayPopoverComposeOwner()
        val initialSelected = preselectedReactionUserIds
        val replyMode = preselectedReplyMode
        val replyToLogId = overlayResolveReplyToLogIdForSend(
            replyMode = replyMode,
            replyToLogId = preselectedReplyToLogId,
        )
        val recipientTitleRes = if (replyMode) {
            R.string.overlay_notifications_reply
        } else {
            R.string.overlay_reactions_recipient_title
        }
        // Keep reply context while user picks another reaction (onBack → reactions grid).

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
                        titleRes = recipientTitleRes,
                        allowBroadcast = !replyMode,
                        onBack = { returnToReactionsList(windowManager) },
                        onDismiss = { hideReactionPickOnly() },
                        onSendToUserIds = { userIds ->
                            if (userIds.isEmpty()) return@OverlayReactionRecipientSheet
                            if (!emitReactionIfConnected {
                                    if (replyMode) {
                                        val parentId = replyToLogId
                                            ?: return@emitReactionIfConnected
                                        userIds.forEach { uid ->
                                            emitOverlayReactionReply(uid, reactionId, parentId)
                                        }
                                    } else {
                                        userIds.forEach { uid ->
                                            emitOverlayReaction(uid, reactionId)
                                        }
                                    }
                                }
                            ) {
                                return@OverlayReactionRecipientSheet
                            }
                            hideReactionPickOnly()
        hideAssaultPickOnly()
                            clearPreselectedReactionContext()
                            Toast.makeText(
                                context,
                                context.getString(R.string.overlay_reaction_sent_selected, userIds.size),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        onSendToAll = {
                            if (!emitReactionIfConnected {
                                    emitOverlayReactionBroadcast(reactionId)
                                }
                            ) {
                                return@OverlayReactionRecipientSheet
                            }
                            hideReactionPickOnly()
        hideAssaultPickOnly()
                            clearPreselectedReactionContext()
                            Toast.makeText(
                                context,
                                context.getString(R.string.overlay_reaction_sent_broadcast),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        loadMembers = {
                            loadOverlayIngameReactionRecipients(
                                usersRepository = container.usersRepository,
                                teamsRepository = container.teamsRepository,
                                launchDiskCache = container.launchDiskCache,
                                forceRefresh = true,
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

    private fun showAssaultAlliesPicker(
        windowManager: WindowManager,
        initialSelected: Set<String>,
        onConfirmed: (Set<String>) -> Unit,
    ) {
        ensurePopoverSuppressHeld()
        attachedWindowManager = windowManager
        val overlayService = context as CombatOverlayService
        val cardW = minOf(dp(360), context.resources.displayMetrics.widthPixels - dp(16))
        val composeOwner = overlayService.obtainOverlayPopoverComposeOwner()
        val composeHost = FrameLayout(context).apply { consumeTouchesInSubtree() }
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
            setDismissOnOutsideCardTouch(composeHost) { hideAssaultPickOnly() }
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
        assaultPickScrim = scrim
        overlayService.attachOverlayComposeTree(scrim, composeHost, compose)
        compose.setContent {
            CompositionLocalProvider(
                LocalLifecycleOwner provides composeOwner,
                LocalViewModelStoreOwner provides composeOwner,
                LocalSavedStateRegistryOwner provides composeOwner,
                LocalOnBackPressedDispatcherOwner provides composeOwner,
            ) {
                SquadRelayTheme {
                    OverlayAssaultAlliesSheet(
                        initialSelectedUserIds = initialSelected,
                        onDismiss = { hideAssaultPickOnly() },
                        onConfirm = { picked ->
                            onConfirmed(picked)
                            hideAssaultPickOnly()
                        },
                    )
                }
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
        /** Default kingdom/server for coord quick commands (team plays on #109). */
        private const val DEFAULT_COORD_SERVER = 109
        private const val MIN_COORD_SERVER = 1
        private const val MAX_COORD_SERVER = 9999
        /** Delay suppress release after coord dialog closes so game gate does not tear down HUD. */
        private const val POPOVER_SUPPRESS_RELEASE_DELAY_MS = 500L
        /** Сколько карточек закладок видно без прокрутки; свыше — включается скролл. */
        private const val BOOKMARK_VISIBLE_LIMIT = 5
        /** Примерная высота одной карточки закладки в dp (для расчёта высоты области скролла). */
        private const val BOOKMARK_CARD_HEIGHT_DP = 60
        /** Р РµР¶Рµ Р±СѓРґРёС‚СЊ Lottie-РїСЂРµРІСЊСЋ вЂ” РЅР° РіР»Р°РІРЅРѕРј РїРѕС‚РѕРєРµ С‚РѕР»СЊРєРѕ РґРѕ [MAX_REACTION_LOTTIE_PREVIEWS_PLAYING] С€С‚СѓРє. */
        const val REACTION_PREVIEW_KEEP_ALIVE_MS = 8_000L
    }
}

internal fun overlayResolveReplyToLogIdForSend(replyMode: Boolean, replyToLogId: String?): String? =
    if (replyMode) {
        replyToLogId?.trim()?.takeIf { it.isNotEmpty() }
    } else {
        null
    }

/** True when send must use overlay:reaction:reply (not overlay:reaction). */
internal fun overlayReactionUsesReplyChannel(replyMode: Boolean, replyToLogId: String?): Boolean =
    replyMode && !overlayResolveReplyToLogIdForSend(replyMode, replyToLogId).isNullOrBlank()
