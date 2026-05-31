package com.lastasylum.alliance.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.lastasylum.alliance.R

internal data class OverlayReactionBurstRequest(
    val fromUserId: String,
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
)

/** Incoming overlay reactions: anchored stack under reaction buttons / HUD. */
internal class OverlayReactionBurstPresenter(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
) {
    private var anchorResolver: () -> OverlayReactionAnchorRect? = { null }
    private var safeTopMinYProvider: () -> Int? = { null }

    private val slots = mutableListOf<IncomingReactionSlot>()
    private var slotIdSeq = 0L
    private var stageRoot: OverlayReactionBurstTouchRoot? = null
    private var stackColumn: LinearLayout? = null
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

    fun isActive(): Boolean = slots.isNotEmpty() || stageRoot != null

    fun clear() {
        stopBurstLottieKeepAlive()
        burstIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        burstIdleRunnable = null
        burstMode = false
        recentEnqueueTimestamps.clear()
        val copy = slots.toList()
        slots.clear()
        copy.forEach { removeSlot(it, animate = false) }
        hideStageImmediate()
    }

    fun enqueue(
        windowManager: WindowManager,
        request: OverlayReactionBurstRequest,
        onBurstFinished: () -> Unit = {},
    ) {
        attachedWindowManager = windowManager
        if (!ensureStage(windowManager)) {
            onBurstFinished()
            return
        }
        val now = System.currentTimeMillis()
        noteEnqueue(now)
        val head = slots.firstOrNull()
        if (head != null && OverlayReactionSlotMergePolicy.canMergeIntoHead(head.toMergeableHead(), request, now)) {
            mergeIntoHead(head, request)
            extendHeadExpiry()
            relayoutStagePosition()
            return
        }
        addSlot(request, onBurstFinished)
        if (slots.size > 1) {
            extendHeadExpiry()
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
        val card: FrameLayout,
        val captionRow: LinearLayout,
        val animHost: FrameLayout?,
        var messageView: TextView?,
        var hideRunnable: Runnable?,
        var lottie: LottieAnimationView?,
    ) {
        fun toMergeableHead() = OverlayReactionMergeableHead(
            fromUserId = fromUserId,
            broadcast = broadcast,
            createdAtMs = createdAtMs,
        )
    }

    private data class BuiltSlotCard(
        val card: FrameLayout,
        val captionRow: LinearLayout,
        val animHost: FrameLayout?,
        val messageView: TextView?,
    )

    private fun ensureStage(windowManager: WindowManager): Boolean {
        if (stageRoot != null) return true
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val maxH = OverlayReactionStackLayout.maxStageHeightPx(layout.screenHeightPx)
        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        stackColumn = stack
        val clipHost = FrameLayout(context).apply {
            clipChildren = true
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                stack,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
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
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    maxH,
                    Gravity.CENTER_HORIZONTAL or Gravity.TOP,
                ),
            )
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            OverlayWindowLayout.reactionBurstWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            OverlayWindowLayout.applyReactionBurstWindowTouchPolicy(context, this)
        }
        if (runCatching { windowManager.addView(root, params) }.isFailure) {
            stackColumn = null
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
        stackColumn = null
        stageParams = null
    }

    private fun addSlot(request: OverlayReactionBurstRequest, onFinished: () -> Unit) {
        val stack = stackColumn ?: return
        val built = buildSlotCard(request, mergeCount = 1)
        val slot = IncomingReactionSlot(
            id = ++slotIdSeq,
            fromUserId = request.fromUserId,
            fromDisplayName = request.fromDisplayName,
            reactionId = request.reactionId,
            broadcast = request.broadcast,
            mergeCount = 1,
            createdAtMs = System.currentTimeMillis(),
            onFinished = onFinished,
            card = built.card,
            captionRow = built.captionRow,
            animHost = built.animHost,
            messageView = built.messageView,
            hideRunnable = null,
            lottie = built.card.findLottieInSubtree(),
        )
        stack.addView(
            built.card,
            0,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(OverlayReactionStackLayout.SLOT_GAP_DP) },
        )
        slots.add(0, slot)
        refreshLottiePlaybackBudget()
        applyStackVisuals(animate = true)
        evictOverflowSlots()
        scheduleSlotExpiry(slot)
        startBurstLottieKeepAlive()
        playSlotEnter(built.card)
    }

    private fun buildSlotCard(request: OverlayReactionBurstRequest, mergeCount: Int): BuiltSlotCard {
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val contentMax = contentMaxWidthPx(layout)
        val card = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            background = OverlayReactionBurstLayout.slotCardBackground()
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        val caption = OverlayReactionCaption.createSenderRow(
            context = context,
            displayName = request.fromDisplayName,
            fromUserId = request.fromUserId,
            broadcast = request.broadcast,
            maxWidthPx = contentMax,
            dp = dp,
            mergeCount = mergeCount,
        )
        var animHost: FrameLayout? = null
        var messageView: TextView? = null
        val textPayload = decodeTextReactionId(request.reactionId)
        if (textPayload != null) {
            column.addView(caption, wrapContentLp())
            messageView = OverlayReactionTextBurstUi.createPreviewMessageTextView(context, textPayload, contentMax)
                .also { view ->
                    view.setTag(R.id.tag_overlay_reaction_message, true)
                    column.addView(view, wrapContentLp().apply { topMargin = dp(6) })
                }
        } else {
            column.addView(caption, wrapContentLp())
            val reaction = overlayQuickReactionById(context, request.reactionId)
            val animSide = OverlayReactionBurstLayout.animSideForReaction(reaction, layout, dp)
            animHost = FrameLayout(context).apply {
                setTag(R.id.tag_overlay_reaction_anim_host, true)
                clipChildren = false
                setPadding(layout.animPadPx, layout.animPadPx, layout.animPadPx, layout.animPadPx)
                addView(
                    createBurstAnimView(reaction, animSide, playLottie = true),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            }
            column.addView(
                animHost,
                LinearLayout.LayoutParams(contentMax, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(OverlayReactionBurstLayout.SENDER_BELOW_ANIM_DP)
                },
            )
        }
        card.addView(column, FrameLayout.LayoutParams(wrapContent(), wrapContent(), Gravity.CENTER))
        return BuiltSlotCard(card, caption, animHost, messageView)
    }

    private fun contentMaxWidthPx(layout: OverlayReactionBurstLayout.Metrics): Int {
        val anchor = anchorResolver()
        val clamped = OverlayReactionAnchorLayout.clampStackWidthPx(layout.maxTextWidthPx, anchor)
        return OverlayReactionBurstLayout.textMessageMaxWidthPx(layout, dp(100), clamped)
    }

    private fun wrapContent() = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun wrapContentLp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun mergeIntoHead(head: IncomingReactionSlot, request: OverlayReactionBurstRequest) {
        head.reactionId = request.reactionId
        head.fromDisplayName = request.fromDisplayName
        head.mergeCount++
        OverlayReactionCaption.updateMergeCount(head.captionRow, head.mergeCount)
        updateSlotContent(head, request)
        playMergePulse(head.card)
        refreshLottiePlaybackBudget()
        applyStackVisuals(animate = true)
    }

    private fun updateSlotContent(head: IncomingReactionSlot, request: OverlayReactionBurstRequest) {
        decodeTextReactionId(request.reactionId)?.let { text ->
            head.messageView?.text = text
            head.lottie?.cancelAnimation()
            head.lottie = null
            return
        }
        val host = head.animHost ?: return
        host.removeAllViews()
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val reaction = overlayQuickReactionById(context, request.reactionId)
        val animSide = OverlayReactionBurstLayout.animSideForReaction(reaction, layout, dp)
        val slotIndex = slots.indexOfFirst { it.id == head.id }.coerceAtLeast(0)
        val playLottie = slotIndex < OverlayReactionStackLayout.MAX_PLAYING_LOTTIES
        val animView = createBurstAnimView(reaction, animSide, playLottie)
        host.addView(
            animView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        head.lottie = animView as? LottieAnimationView
        head.messageView = null
    }

    private fun playMergePulse(card: FrameLayout) {
        val sx = card.scaleX
        val sy = card.scaleY
        card.animate()
            .scaleX(sx * 1.05f)
            .scaleY(sy * 1.05f)
            .setDuration(120L)
            .withEndAction { applyStackVisuals(animate = true) }
            .start()
    }

    private fun playSlotEnter(card: FrameLayout) {
        val enterFrom = -dp(OverlayReactionStackLayout.ENTER_FROM_ANCHOR_Y_DP).toFloat()
        val align = lastAnchor?.horizontalAlign ?: HorizontalAlign.END
        val depthPx = dp(OverlayReactionStackLayout.SLOT_DEPTH_Y_DP)
        val depthXPx = dp(OverlayReactionStackLayout.SLOT_DEPTH_X_DP)
        val targetY = OverlayReactionStackLayout.slotTranslationYForIndex(0, depthPx)
        val targetX = OverlayReactionStackLayout.slotTranslationXForIndex(0, depthXPx, align)
        card.alpha = 0f
        card.scaleX = 0.55f
        card.scaleY = 0.55f
        card.translationY = enterFrom
        card.translationX = 0f
        val pop = OvershootInterpolator(1.1f)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(card, View.ALPHA, 0f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(card, View.SCALE_X, 0.55f, 1.02f, 1f).apply {
                    duration = 420
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(card, View.SCALE_Y, 0.55f, 1.02f, 1f).apply {
                    duration = 420
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, enterFrom, targetY).apply {
                    duration = 320
                    interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0f, targetX).apply {
                    duration = 320
                    interpolator = DecelerateInterpolator()
                },
            )
            start()
        }
    }

    private fun applyStackVisuals(animate: Boolean) {
        val duration = if (animate) OverlayReactionStackLayout.STACK_REFLOW_MS else 0L
        val align = lastAnchor?.horizontalAlign ?: HorizontalAlign.END
        val depthPx = dp(OverlayReactionStackLayout.SLOT_DEPTH_Y_DP)
        val depthXPx = dp(OverlayReactionStackLayout.SLOT_DEPTH_X_DP)
        slots.forEachIndexed { index, slot ->
            val scale = OverlayReactionStackLayout.slotScaleForIndex(index)
            val alpha = OverlayReactionStackLayout.slotAlphaForIndex(index, burstMode)
            val targetY = OverlayReactionStackLayout.slotTranslationYForIndex(index, depthPx)
            val targetX = OverlayReactionStackLayout.slotTranslationXForIndex(index, depthXPx, align)
            val card = slot.card
            if (animate) {
                card.animate()
                    .scaleX(scale).scaleY(scale).alpha(alpha)
                    .translationY(targetY).translationX(targetX)
                    .setDuration(duration)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                card.scaleX = scale
                card.scaleY = scale
                card.alpha = alpha
                card.translationY = targetY
                card.translationX = targetX
            }
        }
    }

    private fun estimateStackHeightPx(): Int {
        var total = 0
        slots.forEachIndexed { index, slot ->
            val h = slot.card.height.takeIf { it > 0 }
                ?: dp(OverlayReactionBurstLayout.MIN_SLOT_ESTIMATE_HEIGHT_DP)
            total += (h * OverlayReactionStackLayout.slotScaleForIndex(index)).toInt() +
                dp(OverlayReactionStackLayout.SLOT_GAP_DP)
        }
        return total
    }

    private fun evictOverflowSlots() {
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val maxH = OverlayReactionStackLayout.maxStageHeightPx(layout.screenHeightPx)
        while (
            OverlayReactionStackLayout.shouldEvictOldestSlot(
                slots.size,
                estimateStackHeightPx(),
                maxH,
            )
        ) {
            val oldest = slots.lastOrNull() ?: break
            removeSlot(oldest, animate = true)
        }
    }

    private fun scheduleSlotExpiry(slot: IncomingReactionSlot, headExtended: Boolean = false) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val index = slots.indexOfFirst { it.id == slot.id }
        val duration = when {
            index == 0 && headExtended -> OverlayReactionStackLayout.headExpiryMs(extended = true)
            else -> OverlayReactionStackLayout.visibleDurationMsForIndex(
                index.coerceAtLeast(0),
                slots.size,
                burstMode,
            )
        }
        val runnable = Runnable {
            if (slots.any { it.id == slot.id }) removeSlot(slot, animate = true)
        }
        slot.hideRunnable = runnable
        mainHandler.postDelayed(runnable, duration)
    }

    private fun extendHeadExpiry() {
        slots.firstOrNull()?.let { scheduleSlotExpiry(it, headExtended = true) }
    }

    private fun removeSlot(slot: IncomingReactionSlot, animate: Boolean) {
        slot.hideRunnable?.let { mainHandler.removeCallbacks(it) }
        slot.hideRunnable = null
        slot.lottie?.cancelAnimation()
        if (!slots.remove(slot)) {
            slot.onFinished()
            return
        }
        val finish = {
            stackColumn?.removeView(slot.card)
            slot.onFinished()
            refreshLottiePlaybackBudget()
            applyStackVisuals(animate = true)
            evictOverflowSlots()
            if (slots.isEmpty()) hideStageImmediate() else relayoutStagePosition()
        }
        if (!animate) {
            finish()
            return
        }
        slot.card.animate()
            .alpha(0f)
            .scaleX(slot.card.scaleX * 0.9f)
            .scaleY(slot.card.scaleY * 0.9f)
            .translationY(slot.card.translationY + dp(OverlayReactionStackLayout.EXIT_SLIDE_Y_DP))
            .setDuration(200L)
            .withEndAction { finish() }
            .start()
    }

    private fun noteEnqueue(nowMs: Long) {
        recentEnqueueTimestamps.addLast(nowMs)
        while (recentEnqueueTimestamps.size > 5) recentEnqueueTimestamps.removeFirst()
        val cutoff = nowMs - OverlayReactionStackLayout.BURST_WINDOW_MS
        burstMode = recentEnqueueTimestamps.count { it >= cutoff } >= OverlayReactionStackLayout.BURST_MIN_EVENTS
        burstIdleRunnable?.let { mainHandler.removeCallbacks(it) }
        val idleRunnable = Runnable {
            val idleCutoff = System.currentTimeMillis() - OverlayReactionStackLayout.BURST_WINDOW_MS * 2
            if (recentEnqueueTimestamps.none { it >= idleCutoff }) {
                burstMode = false
                applyStackVisuals(animate = true)
            }
        }
        burstIdleRunnable = idleRunnable
        mainHandler.postDelayed(idleRunnable, OverlayReactionStackLayout.BURST_WINDOW_MS * 2)
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
            anchor, layout.screenWidthPx, dp, safeTopMinYProvider(),
        )
        params.gravity = placement.windowGravity
        params.x = placement.x
        params.y = placement.y
        stackColumn?.gravity = placement.stackContentGravity
        anchor.maxStackWidthPx?.let { maxW ->
            stackColumn?.layoutParams = (stackColumn?.layoutParams as? FrameLayout.LayoutParams)?.apply {
                width = maxW
            } ?: FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        applyStackVisuals(animate = false)
        root.post {
            if (anchor.horizontalAlign == HorizontalAlign.CENTER && root.width > 0) {
                params.x = OverlayReactionAnchorLayout.adjustCenteredWindowX(
                    anchor, root.width, layout.screenWidthPx,
                )
            }
            runCatching { wm.updateViewLayout(root, params) }
        }
        runCatching { wm.updateViewLayout(root, params) }
    }

    private fun refreshLottiePlaybackBudget() {
        slots.forEachIndexed { index, slot ->
            slot.lottie?.let { lottie ->
                if (index < OverlayReactionStackLayout.MAX_PLAYING_LOTTIES) {
                    configureBurstLottie(lottie)
                } else {
                    lottie.pauseAnimation()
                    lottie.progress = 0f
                }
            }
        }
    }

    private fun configureBurstLottie(lottie: LottieAnimationView) {
        lottie.repeatCount = LottieDrawable.INFINITE
        lottie.repeatMode = LottieDrawable.RESTART
        lottie.enableMergePathsForKitKatAndAbove(true)
        lottie.setRenderMode(RenderMode.AUTOMATIC)
        lottie.alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
        disableOverlayTouchTarget(lottie)
        if (!lottie.isAnimating) lottie.playAnimation()
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

    private fun createBurstAnimView(
        reaction: OverlayQuickReaction,
        maxSidePx: Int,
        playLottie: Boolean,
    ): View {
        reaction.stickerAssetStem?.let { stem ->
            val packKey = reaction.stickerPackKey ?: OVERLAY_REACTION_STICKER_PACK
            return nonClippingImage(maxSidePx).also { image ->
                OverlayReactionBitmapCache.loadAsync(context, packKey, stem) { bmp ->
                    if (bmp != null && image.isAttachedToWindow) image.setImageBitmap(bmp)
                }
            }
        }
        reaction.gifDrawableRes?.let { res ->
            return nonClippingImage(maxSidePx).apply { bindOverlayGif(context, res) }
        }
        reaction.memeDrawableRes?.let { res ->
            return nonClippingImage(maxSidePx).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, res))
            }
        }
        reaction.lottieRawRes?.let { res ->
            return LottieAnimationView(context).apply {
                setAnimation(res)
                reaction.lottieTintHex?.let { tint ->
                    addValueCallback(
                        KeyPath("**"),
                        LottieProperty.COLOR_FILTER,
                        LottieValueCallback(
                            PorterDuffColorFilter(Color.parseColor(tint), PorterDuff.Mode.SRC_ATOP),
                        ),
                    )
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (playLottie) configureBurstLottie(this)
                maxWidth = maxSidePx
                maxHeight = maxSidePx
            }
        }
        return nonClippingImage(maxSidePx).apply {
            setImageDrawable(
                AppCompatResources.getDrawable(context, reaction.iconRes)?.mutate()?.also { d ->
                    DrawableCompat.setTint(d, Color.parseColor(reaction.tintHex))
                },
            )
        }
    }

    private fun nonClippingImage(maxSidePx: Int): ImageView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
            disableOverlayTouchTarget(this)
            maxWidth = maxSidePx
            maxHeight = maxSidePx
        }

    private fun View.findLottieInSubtree(): LottieAnimationView? {
        if (this is LottieAnimationView) return this
        if (this is ViewGroup) {
            for (i in 0 until childCount) {
                getChildAt(i).findLottieInSubtree()?.let { return it }
            }
        }
        return null
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
