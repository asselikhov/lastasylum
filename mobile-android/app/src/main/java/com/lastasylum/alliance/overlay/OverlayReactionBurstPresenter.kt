package com.lastasylum.alliance.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.airbnb.lottie.LottieAnimationView

internal data class OverlayReactionBurstRequest(
    val fromUserId: String,
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
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
    private var historyRow: LinearLayout? = null
    private var stageParams: WindowManager.LayoutParams? = null
    private var attachedWindowManager: WindowManager? = null
    private var burstLottieKeepAliveRunnable: Runnable? = null
    private var burstIdleRunnable: Runnable? = null
    private var lastAnchor: OverlayReactionAnchorRect? = null
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
        recentEnqueueTimestamps.clear()
        val all = buildList {
            heroSlot?.let { add(it) }
            addAll(historySlots)
        }
        heroSlot = null
        historySlots.clear()
        all.forEach { removeSlot(it, animate = false) }
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
        val history = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        historyRow = history
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
                history,
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
            historyRow = null
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
        stageRoot?.let { root ->
            val wm = attachedWindowManager
                ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            runCatching { wm?.removeView(root) }
        }
        stageRoot = null
        stageColumn = null
        heroHost = null
        historyRow = null
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
        heroHost?.removeView(hero.tile.card)
        val built = tileFactory.buildTile(
            hero.toRequest(),
            OverlayReactionTileMode.MINI,
            mergeCount = hero.mergeCount,
            playLottie = false,
        )
        val miniSlot = hero.copy(
            id = hero.id,
            tile = built,
            hideRunnable = null,
        )
        historyRow?.addView(
            built.card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = dp(OverlayReactionStageLayout.HISTORY_GAP_DP) },
        )
        playMiniEnter(built.card)
        historySlots.add(miniSlot)
        scheduleMiniExpiry(miniSlot)
        heroSlot = null
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
                hero.mergeCount,
            )
        }
        tileFactory.rebuildVisual(hero.tile, request, OverlayReactionTileMode.HERO, playLottie = true)
        playMergePulse(hero.tile.card)
        refreshLottiePlaybackBudget()
    }

    private fun promoteNewestHistoryToHero() {
        val mini = historySlots.removeLastOrNull() ?: return
        mini.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        historyRow?.removeView(mini.tile.card)
        val built = tileFactory.buildTile(
            mini.toRequest(),
            OverlayReactionTileMode.HERO,
            mergeCount = mini.mergeCount,
            playLottie = true,
        )
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
        refreshLottiePlaybackBudget()
    }

    private fun evictOldestHistory() {
        while (OverlayReactionStageLayout.shouldEvictOldestHistory(historySlots.size)) {
            val oldest = historySlots.firstOrNull() ?: break
            removeSlot(oldest, animate = true)
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
        val duration = OverlayReactionStageLayout.heroExpiryMs(extended)
        val runnable = Runnable {
            if (heroSlot?.id == slot.id) onHeroExpired()
        }
        slot.hideRunnable = runnable
        mainHandler.postDelayed(runnable, duration)
    }

    private fun scheduleMiniExpiry(slot: IncomingReactionSlot) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val duration = OverlayReactionStageLayout.miniExpiryMs(burstMode)
        val runnable = Runnable {
            if (historySlots.any { it.id == slot.id }) removeSlot(slot, animate = true)
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

    private fun removeSlot(slot: IncomingReactionSlot, animate: Boolean) {
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
                historyRow?.removeView(slot.tile.card)
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
        slot.tile.card.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .translationY(slot.tile.card.translationY + dp(OverlayReactionStageLayout.EXIT_SLIDE_Y_DP))
            .setDuration(200L)
            .withEndAction { finish() }
            .start()
    }

    private fun noteEnqueue(nowMs: Long) {
        recentEnqueueTimestamps.addLast(nowMs)
        while (recentEnqueueTimestamps.size > 5) recentEnqueueTimestamps.removeFirst()
        val cutoff = nowMs - OverlayReactionStageLayout.BURST_WINDOW_MS
        burstMode = recentEnqueueTimestamps.count { it >= cutoff } >= OverlayReactionStageLayout.BURST_MIN_EVENTS
        burstIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        val idleRunnable = Runnable {
            val idleCutoff = System.currentTimeMillis() - OverlayReactionStageLayout.BURST_WINDOW_MS * 2
            if (recentEnqueueTimestamps.none { it >= idleCutoff }) {
                burstMode = false
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
        lastAnchor = anchor
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
