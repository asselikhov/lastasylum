package com.lastasylum.alliance.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionBurstReplyTo

internal data class OverlayReactionBurstRequest(
    val fromUserId: String,
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
    val replyToLog: OverlayReactionBurstReplyTo? = null,
)

/** Incoming overlay reactions: hero tile + history strip under top-right HUD. */
internal class OverlayReactionBurstPresenter(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
) {
    private var anchorResolver: () -> OverlayReactionAnchorRect? = { null }
    private var safeTopMinYProvider: () -> Int? = { null }

    private val visualFactory = OverlayReactionVisualFactory(context) { configureBurstLottie(it) }
    private val tileFactory = OverlayReactionTileFactory(context, dp, visualFactory)

    private var heroSlot: IncomingReactionSlot? = null
    private val historySlots = mutableListOf<IncomingReactionSlot>()
    private var slotIdSeq = 0L
    private var stageRoot: OverlayReactionBurstTouchRoot? = null
    private var stageColumn: LinearLayout? = null
    private var heroHost: FrameLayout? = null
    private var historyScroll: HorizontalScrollView? = null
    private var historyFanHost: FrameLayout? = null
    private var overflowBadge: TextView? = null
    private var evictedHistoryCount = 0
    private var stageParams: WindowManager.LayoutParams? = null
    private var attachedWindowManager: WindowManager? = null
    private var burstLottieKeepAliveRunnable: Runnable? = null
    private var burstIdleRunnable: Runnable? = null
    private var burstMode = false
    private val recentEnqueueTimestamps = ArrayDeque<Long>(5)

    fun setAnchorResolver(resolver: () -> OverlayReactionAnchorRect?) {
        anchorResolver = resolver
    }

    fun setSafeTopMinYProvider(provider: () -> Int?) {
        safeTopMinYProvider = provider
    }

    fun invalidateReactionBurstAnchor() {
        if (stageRoot == null) return
        relayoutStagePosition()
    }

    fun isActive(): Boolean =
        heroSlot != null || historySlots.isNotEmpty() || stageRoot != null

    fun clear() {
        stopBurstLottieKeepAlive()
        burstIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        burstIdleRunnable = null
        burstMode = false
        evictedHistoryCount = 0
        recentEnqueueTimestamps.clear()
        val all = buildList {
            heroSlot?.let { add(it) }
            addAll(historySlots)
        }
        heroSlot = null
        historySlots.clear()
        all.forEach { removeSlot(it, animate = false, fromEviction = false) }
        hideStageImmediate()
    }

    fun enqueue(
        windowManager: WindowManager,
        request: OverlayReactionBurstRequest,
        onBurstFinished: () -> Unit = {},
    ) {
        attachedWindowManager = windowManager
        preloadReactionAssets(request)
        if (!ensureStage(windowManager)) {
            onBurstFinished()
            return
        }
        val now = System.currentTimeMillis()
        noteEnqueue(now)
        val hero = heroSlot
        if (hero != null && OverlayReactionSlotMergePolicy.canMergeIntoHead(hero.toMergeableHead(), request, now)) {
            mergeIntoHero(hero, request)
            extendHeroExpiry()
            relayoutStagePosition()
            return
        }
        val hadHero = heroSlot != null
        demoteHeroToHistory()
        installHero(request, onBurstFinished)
        evictOldestHistory()
        refreshLottiePlaybackBudget()
        startBurstLottieKeepAlive()
        if (hadHero) {
            extendHeroExpiry()
        } else {
            OverlayReactionBurstHaptic.lightTap(context, stageRoot)
        }
        relayoutStagePosition()
    }

    private data class IncomingReactionSlot(
        val id: Long,
        val fromUserId: String,
        var fromDisplayName: String,
        var reactionId: String,
        val broadcast: Boolean,
        val replyToLog: OverlayReactionBurstReplyTo?,
        var mergeCount: Int,
        val createdAtMs: Long,
        val onFinished: () -> Unit,
        val tile: OverlayReactionBuiltTile,
        var hideRunnable: Runnable?,
    ) {
        fun toMergeableHead() = OverlayReactionMergeableHead(
            fromUserId = fromUserId,
            broadcast = broadcast,
            createdAtMs = createdAtMs,
        )

        fun toRequest() = OverlayReactionBurstRequest(
            fromUserId = fromUserId,
            fromDisplayName = fromDisplayName,
            reactionId = reactionId,
            broadcast = broadcast,
            replyToLog = replyToLog,
        )

        fun currentLottie(): LottieAnimationView? = tile.card.findLottieInSubtree()
    }

    private fun ensureStage(windowManager: WindowManager): Boolean {
        if (stageRoot != null) return true
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val maxH = OverlayReactionStageLayout.maxStageHeightPx(layout.screenHeightPx)
        val heroContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        heroHost = heroContainer
        val fanHost = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        historyFanHost = fanHost
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(
                fanHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
        historyScroll = scroll
        val overflow = TextView(context).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#B0C8D8E8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            disableOverlayTouchTarget(this)
        }
        overflowBadge = overflow
        val historySection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(
                scroll,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                overflow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4) },
            )
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                heroContainer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                historySection,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(OverlayReactionStageLayout.HISTORY_GAP_DP) },
            )
        }
        stageColumn = column
        val clipHost = FrameLayout(context).apply {
            clipChildren = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                ),
            )
        }
        val root = OverlayReactionBurstTouchRoot(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
            addView(
                clipHost,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    maxH,
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            layout.screenWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            OverlayWindowLayout.reactionBurstWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            OverlayWindowLayout.applyReactionBurstWindowTouchPolicy(context, this)
        }
        if (runCatching { windowManager.addView(root, params) }.isFailure) {
            stageColumn = null
            heroHost = null
            historyFanHost = null
            historyScroll = null
            overflowBadge = null
            return false
        }
        stageRoot = root
        stageParams = params
        disableOverlayTouchTarget(root)
        root.post {
            OverlayWindowLayout.applyReactionBurstWindowTouchPolicy(context, params)
            runCatching { windowManager.updateViewLayout(root, params) }
        }
        return true
    }

    private fun hideStageImmediate() {
        stopBurstLottieKeepAlive()
        evictedHistoryCount = 0
        stageRoot?.let { root ->
            val wm = attachedWindowManager
                ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(root) }
        }
        stageRoot = null
        stageColumn = null
        heroHost = null
        historyFanHost = null
        historyScroll = null
        overflowBadge = null
        stageParams = null
    }

    private fun installHero(request: OverlayReactionBurstRequest, onFinished: () -> Unit) {
        val host = heroHost ?: return
        val built = tileFactory.buildTile(request, OverlayReactionTileMode.HERO, mergeCount = 1, playLottie = true)
        val slot = IncomingReactionSlot(
            id = ++slotIdSeq,
            fromUserId = request.fromUserId,
            fromDisplayName = request.fromDisplayName,
            reactionId = request.reactionId,
            broadcast = request.broadcast,
            replyToLog = request.replyToLog,
            mergeCount = 1,
            createdAtMs = System.currentTimeMillis(),
            onFinished = onFinished,
            tile = built,
            hideRunnable = null,
        )
        host.addView(
            built.card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        heroSlot = slot
        scheduleHeroExpiry(slot)
        playHeroEnter(built.card)
    }

    private fun demoteHeroToHistory() {
        val hero = heroSlot ?: return
        hero.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        heroSlot = null
        val card = hero.tile.card
        hero.tile.captionView?.animate()?.alpha(0f)?.setDuration(120L)?.start()
        card.animate()
            .scaleX(OverlayReactionStageLayout.MINI_SCALE_RATIO)
            .scaleY(OverlayReactionStageLayout.MINI_SCALE_RATIO)
            .translationY(card.translationY + dp(20))
            .setDuration(OverlayReactionStageLayout.DEMOTE_ANIM_MS)
            .withEndAction { completeDemote(hero) }
            .start()
    }

    private fun completeDemote(hero: IncomingReactionSlot) {
        heroHost?.removeView(hero.tile.card)
        attachMiniSlot(
            hero.copy(
                tile = tileFactory.buildTile(
                    hero.toRequest(),
                    OverlayReactionTileMode.MINI,
                    mergeCount = hero.mergeCount,
                    playLottie = false,
                ),
                hideRunnable = null,
            ),
        )
    }

    private fun attachMiniSlot(miniSlot: IncomingReactionSlot) {
        val host = historyFanHost ?: return
        val m = OverlayReactionBurstLayout.metrics(context, dp)
        val miniPx = OverlayReactionStageLayout.miniTileSizePx(m.screenWidthPx, dp)
        host.addView(
            miniSlot.tile.card,
            FrameLayout.LayoutParams(
                miniPx,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        playMiniEnter(miniSlot.tile.card)
        historySlots.add(miniSlot)
        scheduleMiniExpiry(miniSlot, historySlots.lastIndex)
        layoutHistory()
    }

    private fun mergeIntoHero(hero: IncomingReactionSlot, request: OverlayReactionBurstRequest) {
        hero.reactionId = request.reactionId
        hero.fromDisplayName = request.fromDisplayName
        hero.mergeCount++
        hero.tile.captionView?.let { caption ->
            OverlayReactionCaption.updateMergeCount(
                caption,
                hero.fromDisplayName,
                hero.broadcast,
                isReply = hero.replyToLog != null,
                mergeCount = hero.mergeCount,
            )
        }
        tileFactory.rebuildVisual(hero.tile, request, OverlayReactionTileMode.HERO, playLottie = true)
        playMergePulse(hero.tile.card)
        OverlayReactionBurstHaptic.mergePulse(context, stageRoot)
        refreshLottiePlaybackBudget()
    }

    private fun promoteNewestHistoryToHero() {
        val mini = historySlots.removeLastOrNull() ?: return
        mini.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        historyFanHost?.removeView(mini.tile.card)
        val built = tileFactory.buildTile(
            mini.toRequest(),
            OverlayReactionTileMode.HERO,
            mergeCount = mini.mergeCount,
            playLottie = true,
        )
        built.captionView?.alpha = 0f
        val promoted = mini.copy(tile = built, hideRunnable = null)
        heroHost?.addView(
            built.card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        heroSlot = promoted
        scheduleHeroExpiry(promoted)
        playPromoteFromMini(built.card, built.captionView)
        layoutHistory()
        refreshLottiePlaybackBudget()
    }

    private fun evictOldestHistory() {
        while (OverlayReactionStageLayout.shouldEvictOldestHistory(historySlots.size)) {
            val oldest = historySlots.firstOrNull() ?: break
            evictedHistoryCount++
            updateOverflowBadge(pulse = true)
            removeSlot(oldest, animate = true, fromEviction = true)
        }
    }

    private fun layoutHistory() {
        val host = historyFanHost ?: return
        val scroll = historyScroll ?: return
        if (historySlots.isEmpty()) {
            host.layoutParams = host.layoutParams?.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            return
        }
        val m = OverlayReactionBurstLayout.metrics(context, dp)
        val miniPx = OverlayReactionStageLayout.miniTileSizePx(m.screenWidthPx, dp)
        val gapPx = dp(OverlayReactionStageLayout.HISTORY_GAP_DP)
        val overlapPx = dp(OverlayReactionHistoryLayout.FAN_OVERLAP_X_DP)
        val arcYPx = dp(OverlayReactionHistoryLayout.FAN_ARC_Y_DP)
        val mode = OverlayReactionHistoryLayout.modeFor(burstMode, historySlots.size)
        val riverStep = (miniPx + dp(OverlayReactionHistoryLayout.RIVER_GAP_DP)).toFloat()
        val contentWidth = if (mode == HistoryLayoutMode.RIVER) {
            (historySlots.size * riverStep).toInt().coerceAtLeast(miniPx)
        } else {
            val span = ((historySlots.size - 1).coerceAtLeast(0)) *
                (miniPx + gapPx - overlapPx) + miniPx
            span.coerceAtLeast(miniPx)
        }
        host.layoutParams = host.layoutParams?.apply {
            width = contentWidth
        } ?: FrameLayout.LayoutParams(contentWidth, FrameLayout.LayoutParams.WRAP_CONTENT)
        historySlots.forEachIndexed { index, slot ->
            val card = slot.tile.card
            card.pivotX = miniPx / 2f
            card.pivotY = 0f
            when (mode) {
                HistoryLayoutMode.FAN -> {
                    val offset = OverlayReactionHistoryLayout.fanOffsets(
                        index = index,
                        count = historySlots.size,
                        miniPx = miniPx,
                        gapPx = gapPx,
                        overlapPx = overlapPx,
                        arcYPx = arcYPx,
                    )
                    card.translationX = offset.translationX
                    card.translationY = offset.translationY
                }
                HistoryLayoutMode.RIVER -> {
                    val startX = -contentWidth / 2f + miniPx / 2f
                    card.translationX = startX + index * riverStep
                    card.translationY = 0f
                }
            }
        }
        host.requestLayout()
        if (mode == HistoryLayoutMode.RIVER) {
            host.post {
                scroll.smoothScrollTo(
                    OverlayReactionHistoryLayout.riverScrollTargetX(host.width, scroll.width),
                    0,
                )
            }
        }
    }

    private fun updateOverflowBadge(pulse: Boolean = false) {
        val badge = overflowBadge ?: return
        if (evictedHistoryCount <= 0) {
            badge.visibility = View.GONE
            return
        }
        badge.visibility = View.VISIBLE
        badge.text = context.getString(R.string.overlay_reaction_history_overflow, evictedHistoryCount)
        if (pulse) {
            badge.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(120L)
                .withEndAction {
                    badge.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                .start()
        }
    }

    private fun playMergePulse(card: FrameLayout) {
        card.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(120L)
            .withEndAction {
                card.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
            }
            .start()
    }

    private fun playMiniEnter(card: FrameLayout) {
        card.alpha = 0.6f
        card.scaleX = 0.85f
        card.scaleY = 0.85f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(OverlayReactionStageLayout.REFLOW_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun playPromoteFromMini(card: FrameLayout, caption: TextView?) {
        card.scaleX = OverlayReactionStageLayout.MINI_SCALE_RATIO
        card.scaleY = OverlayReactionStageLayout.MINI_SCALE_RATIO
        card.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(OverlayReactionStageLayout.REFLOW_MS + 80L)
            .setInterpolator(OvershootInterpolator(1.05f))
            .start()
        caption?.animate()?.alpha(1f)?.setDuration(220L)?.setStartDelay(80L)?.start()
    }

    private fun playHeroEnter(card: FrameLayout) {
        val enterFrom = -dp(OverlayReactionStageLayout.ENTER_FROM_ANCHOR_Y_DP).toFloat()
        card.alpha = 0f
        card.scaleX = 0.7f
        card.scaleY = 0.7f
        card.translationY = enterFrom
        card.translationX = 0f
        val pop = OvershootInterpolator(1.1f)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(card, View.SCALE_X, 0.7f, 1.02f, 1f).apply {
                    duration = 420
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.7f, 1.02f, 1f).apply {
                    duration = 420
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, enterFrom, 0f).apply {
                    duration = 320
                    interpolator = DecelerateInterpolator()
                },
            )
            start()
        }
    }

    private fun scheduleHeroExpiry(slot: IncomingReactionSlot, extended: Boolean = false) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val duration = OverlayReactionExpiryPolicy.heroExpiryMs(
            slot.reactionId,
            extended,
            burstMode,
        )
        val runnable = Runnable {
            if (heroSlot?.id == slot.id) onHeroExpired()
        }
        slot.hideRunnable = runnable
        mainHandler.postDelayed(runnable, duration)
    }

    private fun scheduleMiniExpiry(slot: IncomingReactionSlot, slotIndex: Int) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val duration = OverlayReactionExpiryPolicy.miniExpiryMs(
            slot.reactionId,
            burstMode,
            slotIndex,
        )
        val runnable = Runnable {
            if (historySlots.any { it.id == slot.id }) removeSlot(slot, animate = true, fromEviction = false)
        }
        slot.hideRunnable = runnable
        mainHandler.postDelayed(runnable, duration)
    }

    private fun extendHeroExpiry() {
        heroSlot?.let { scheduleHeroExpiry(it, extended = true) }
    }

    private fun onHeroExpired() {
        val hero = heroSlot ?: return
        hero.hideRunnable = null
        heroHost?.removeView(hero.tile.card)
        heroSlot = null
        hero.onFinished()
        if (historySlots.isNotEmpty()) {
            promoteNewestHistoryToHero()
        } else {
            hideStageImmediate()
        }
        refreshLottiePlaybackBudget()
    }

    private fun removeSlot(slot: IncomingReactionSlot, animate: Boolean, fromEviction: Boolean) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        slot.hideRunnable = null
        slot.currentLottie()?.cancelAnimation()
        val isHero = heroSlot?.id == slot.id
        val removedFromHistory = historySlots.remove(slot)
        if (!isHero && !removedFromHistory) {
            slot.onFinished()
            return
        }
        if (isHero) heroSlot = null
        val finish = {
            if (isHero) {
                heroHost?.removeView(slot.tile.card)
            } else {
                historyFanHost?.removeView(slot.tile.card)
                layoutHistory()
            }
            slot.onFinished()
            refreshLottiePlaybackBudget()
            if (heroSlot == null && historySlots.isEmpty()) {
                hideStageImmediate()
            }
        }
        if (!animate) {
            finish()
            return
        }
        if (fromEviction) {
            updateOverflowBadge(pulse = true)
        }
        slot.tile.card.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .translationX(slot.tile.card.translationX - dp(16))
            .translationY(slot.tile.card.translationY + dp(OverlayReactionStageLayout.EXIT_SLIDE_Y_DP))
            .setDuration(200L)
            .withEndAction { finish() }
            .start()
    }

    private fun noteEnqueue(nowMs: Long) {
        recentEnqueueTimestamps.addLast(nowMs)
        while (recentEnqueueTimestamps.size > 5) recentEnqueueTimestamps.removeFirst()
        val cutoff = nowMs - OverlayReactionStageLayout.BURST_WINDOW_MS
        val wasBurst = burstMode
        burstMode = recentEnqueueTimestamps.count { it >= cutoff } >= OverlayReactionStageLayout.BURST_MIN_EVENTS
        if (burstMode != wasBurst) {
            layoutHistory()
        }
        burstIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        val idleRunnable = Runnable {
            val idleCutoff = System.currentTimeMillis() - OverlayReactionStageLayout.BURST_WINDOW_MS * 2
            if (recentEnqueueTimestamps.none { it >= idleCutoff }) {
                burstMode = false
                layoutHistory()
            }
        }
        burstIdleRunnable = idleRunnable
        mainHandler.postDelayed(idleRunnable, OverlayReactionStageLayout.BURST_WINDOW_MS * 2)
    }

    private fun relayoutStagePosition() {
        val root = stageRoot ?: return
        val params = stageParams ?: return
        val wm = attachedWindowManager ?: return
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val anchor = anchorResolver()
            ?: OverlayReactionAnchorLayout.fallbackTopEndHud(layout.screenWidthPx, dp)
        val placement = OverlayReactionAnchorLayout.computeStageWindowPlacement(
            anchor,
            layout.screenWidthPx,
            dp,
            safeTopMinYProvider(),
        )
        params.gravity = placement.windowGravity
        params.x = placement.x
        params.y = placement.y
        if (placement.fullScreenWidth) {
            params.width = layout.screenWidthPx
        }
        stageColumn?.gravity = placement.stackContentGravity
        clipHostGravity(placement.stackContentGravity)
        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun clipHostGravity(contentGravity: Int) {
        val root = stageRoot ?: return
        val clipHost = (root as? ViewGroup)?.getChildAt(0) as? FrameLayout ?: return
        (clipHost.getChildAt(0)?.layoutParams as? FrameLayout.LayoutParams)?.gravity =
            contentGravity or Gravity.TOP
    }

    private fun preloadReactionAssets(request: OverlayReactionBurstRequest) {
        decodeTextReactionId(request.reactionId)?.let { return }
        val reaction = overlayQuickReactionById(context, request.reactionId)
        reaction.stickerAssetStem?.let { stem ->
            val packKey = reaction.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
            OverlayReactionBitmapCache.preloadSticker(context, packKey, stem)
            OverlayReactionBitmapCache.loadSync(context, packKey, stem)
        }
    }

    private fun refreshLottiePlaybackBudget() {
        historySlots.forEach { slot ->
            slot.currentLottie()?.let { lottie ->
                lottie.pauseAnimation()
                lottie.progress = 0f
            }
        }
        heroSlot?.currentLottie()?.let { configureBurstLottie(it) }
        historySlots.lastOrNull()?.currentLottie()?.let { configureBurstLottie(it) }
    }

    private fun startBurstLottieKeepAlive() {
        stopBurstLottieKeepAlive()
        val tick = object : Runnable {
            override fun run() {
                if (stageRoot == null) return
                refreshLottiePlaybackBudget()
                mainHandler.postDelayed(this, 2_000L)
            }
        }
        burstLottieKeepAliveRunnable = tick
        mainHandler.postDelayed(tick, 2_000L)
    }

    private fun stopBurstLottieKeepAlive() {
        burstLottieKeepAliveRunnable?.let { mainHandler.removeCallbacks(it) }
        burstLottieKeepAliveRunnable = null
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
