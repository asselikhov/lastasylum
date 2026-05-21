package com.lastasylum.alliance.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.text.TextUtils
import android.util.TypedValue
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
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
)

/**
 * Входящая реакция: см. [OverlayReactionBurstLayout] (контракт с игрой и производительностью).
 */
internal class OverlayReactionBurstPresenter(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
) {
    private val queue = ArrayDeque<OverlayReactionBurstRequest>()
    private var showing = false
    private var burstRoot: OverlayPassthroughMultitouchFrameLayout? = null
    private var burstLottie: LottieAnimationView? = null
    private var attachedWindowManager: WindowManager? = null
    private var hideBurstRunnable: Runnable? = null
    private var activeEnterAnimator: Animator? = null

    fun isActive(): Boolean = showing || queue.isNotEmpty() || burstRoot != null

    fun clear() {
        hideBurstRunnable?.let { mainHandler.removeCallbacks(it) }
        hideBurstRunnable = null
        activeEnterAnimator?.cancel()
        activeEnterAnimator = null
        hideBurstImmediate()
        queue.clear()
        showing = false
    }

    fun enqueue(windowManager: WindowManager, request: OverlayReactionBurstRequest) {
        attachedWindowManager = windowManager
        queue.addLast(request)
        drainQueue()
    }

    private fun drainQueue() {
        if (showing || queue.isEmpty()) return
        val req = queue.removeFirst()
        showing = true
        showBurstNow(req) {
            showing = false
            mainHandler.post { drainQueue() }
        }
    }

    private fun hideBurstImmediate() {
        activeEnterAnimator?.cancel()
        activeEnterAnimator = null
        burstLottie?.cancelAnimation()
        burstLottie = null
        val root = burstRoot
        burstRoot = null
        val wm = attachedWindowManager
            ?: context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (root != null) {
            runCatching { wm?.removeView(root) }
        }
    }

    private fun showBurstNow(request: OverlayReactionBurstRequest, onFinished: () -> Unit) {
        hideBurstImmediate()
        val windowManager = attachedWindowManager ?: run {
            onFinished()
            return
        }

        val reaction = overlayQuickReactionById(request.reactionId)
        val displayName = request.fromDisplayName.trim().ifBlank { "—" }
        val broadcast = request.broadcast
        val layout = OverlayReactionBurstLayout.metrics(context, dp)
        val animSide = OverlayReactionBurstLayout.animSideForReaction(reaction, layout, dp)

        val root = OverlayPassthroughMultitouchFrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
        }

        val animView = createBurstAnimView(reaction, animSide)

        val caption = TextView(context).apply {
            text = if (broadcast) {
                context.getString(R.string.overlay_reaction_burst_caption_broadcast)
            } else {
                context.getString(R.string.overlay_reaction_burst_caption_private)
            }
            setTextColor(
                if (broadcast) Color.parseColor("#FFFFB74D")
                else Color.parseColor("#FF90CAF9"),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.TRANSPARENT)
            setShadowLayer(dp(2).toFloat(), 0f, 1f, Color.parseColor("#99000000"))
        }

        val senderLine = TextView(context).apply {
            text = context.getString(R.string.overlay_reaction_burst_from, displayName)
            setTextColor(Color.parseColor("#FFFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            maxWidth = layout.maxTextWidthPx
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setBackgroundColor(Color.TRANSPARENT)
            setShadowLayer(dp(3).toFloat(), 0f, 1.5f, Color.parseColor("#AA000000"))
        }

        val textBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, dp(6), 0, 0)
            addView(
                caption,
                LinearLayout.LayoutParams(
                    layout.maxTextWidthPx,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                senderLine,
                LinearLayout.LayoutParams(
                    layout.maxTextWidthPx,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }

        val animHost = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(
                layout.animPadPx,
                layout.animPadPx,
                layout.animPadPx,
                layout.animPadPx,
            )
            addView(
                animView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        }

        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            isClickable = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(animHost)
            addView(
                textBlock,
                LinearLayout.LayoutParams(
                    layout.maxTextWidthPx,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) },
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
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            OverlayWindowLayout.reactionBurstWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyPopupLayoutCompat(this)
            gravity = Gravity.CENTER
        }

        if (runCatching { windowManager.addView(root, params) }.isFailure) {
            onFinished()
            return
        }
        burstRoot = root

        animView.alpha = 0f
        animView.scaleX = 0.4f
        animView.scaleY = 0.4f
        textBlock.alpha = 0f
        textBlock.translationY = dp(6).toFloat()

        animView.post {
            animView.pivotX = animView.width * 0.5f
            animView.pivotY = animView.height * 0.5f
        }

        val pop = OvershootInterpolator(1.15f)
        val ease = DecelerateInterpolator()
        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(animView, "alpha", 0f, 1f).apply {
                    duration = 280
                },
                ObjectAnimator.ofFloat(animView, "scaleX", 0.4f, 1.06f, 1f).apply {
                    duration = 520
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(animView, "scaleY", 0.4f, 1.06f, 1f).apply {
                    duration = 520
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(textBlock, "alpha", 0f, 1f).apply {
                    duration = 360
                    startDelay = 120
                },
                ObjectAnimator.ofFloat(textBlock, "translationY", dp(6).toFloat(), 0f).apply {
                    duration = 400
                    startDelay = 120
                    interpolator = ease
                },
            )
        }
        activeEnterAnimator = enter
        enter.start()

        val hideRunnable = Runnable {
            activeEnterAnimator?.cancel()
            activeEnterAnimator = null
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(animView, "alpha", 1f, 0f).apply { duration = 280 },
                    ObjectAnimator.ofFloat(animView, "scaleX", 1f, 1.08f).apply { duration = 280 },
                    ObjectAnimator.ofFloat(animView, "scaleY", 1f, 1.08f).apply { duration = 280 },
                    ObjectAnimator.ofFloat(textBlock, "alpha", 1f, 0f).apply { duration = 260 },
                )
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            hideBurstImmediate()
                            onFinished()
                        }
                    },
                )
            }.start()
        }
        hideBurstRunnable = hideRunnable
        mainHandler.postDelayed(hideRunnable, OVERLAY_REACTION_BURST_VISIBLE_MS)
    }

    private fun createBurstAnimView(reaction: OverlayQuickReaction, maxSidePx: Int): View {
        val stickerStem = reaction.stickerAssetStem
        if (stickerStem != null) {
            val bmp = loadStickerReactionBitmap(context, stickerStem)
            if (bmp != null) {
                return nonClippingImage(maxSidePx).apply {
                    setImageBitmap(bmp)
                }
            }
        }
        val memeRes = reaction.memeDrawableRes
        if (memeRes != null) {
            return nonClippingImage(maxSidePx).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
            }
        }
        val lottieRes = reaction.lottieRawRes
        if (lottieRes != null) {
            return LottieAnimationView(context).apply {
                setAnimation(lottieRes)
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
                setRenderMode(RenderMode.AUTOMATIC)
                repeatCount = 0
                playAnimation()
                maxWidth = maxSidePx
                maxHeight = maxSidePx
            }.also { burstLottie = it }
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
            maxWidth = maxSidePx
            maxHeight = maxSidePx
        }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
