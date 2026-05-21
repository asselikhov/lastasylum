package com.lastasylum.alliance.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.lastasylum.alliance.R

internal data class OverlayReactionBurstRequest(
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
)

/**
 * Очередь вспышек реакций по центру экрана: премиальный «wow» без блокировки касаний в игру.
 */
internal class OverlayReactionBurstPresenter(
    private val context: Context,
    private val mainHandler: Handler,
    private val dp: (Int) -> Int,
) {
    private val queue = ArrayDeque<OverlayReactionBurstRequest>()
    private var showing = false
    private var burstRoot: FrameLayout? = null
    private var burstLottie: LottieAnimationView? = null
    private var attachedWindowManager: WindowManager? = null
    private var hideBurstRunnable: Runnable? = null
    private var glowPulseAnimator: Animator? = null

    fun isActive(): Boolean = showing || queue.isNotEmpty() || burstRoot != null

    fun clear() {
        hideBurstRunnable?.let { mainHandler.removeCallbacks(it) }
        hideBurstRunnable = null
        glowPulseAnimator?.cancel()
        glowPulseAnimator = null
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
        glowPulseAnimator?.cancel()
        glowPulseAnimator = null
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
        val theme = burstTheme(reaction.burstAccentHex, broadcast)

        val screenW = context.resources.displayMetrics.widthPixels
        val maxBurstSide = minOf((screenW * 0.54f).toInt(), dp(300))
        val burstAnimSize = when {
            reaction.memeDrawableRes != null -> maxBurstSide
            reaction.stickerAssetStem != null -> maxBurstSide
            reaction.lottieRawRes != null -> minOf(dp(210), maxBurstSide)
            else -> dp(132)
        }
        val stagePad = (burstAnimSize * 0.22f).toInt().coerceAtLeast(dp(14))

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
            clipChildren = false
            clipToPadding = false
        }

        val animView = createBurstAnimView(reaction, burstAnimSize)

        val outerGlow = View(context).apply {
            background = ovalGlowDrawable(theme.glowStrong)
            alpha = 0f
        }
        val innerGlow = View(context).apply {
            background = ovalGlowDrawable(theme.glowSoft)
            alpha = 0f
        }
        val ringStroke = View(context).apply {
            background = ringStrokeDrawable(theme.ringStroke)
            alpha = 0f
        }

        val animStage = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            val outerSize = (burstAnimSize * 1.38f).toInt()
            val innerSize = (burstAnimSize * 1.12f).toInt()
            val ringSize = (burstAnimSize * 1.22f).toInt()
            addView(
                outerGlow,
                FrameLayout.LayoutParams(outerSize, outerSize, Gravity.CENTER),
            )
            addView(
                innerGlow,
                FrameLayout.LayoutParams(innerSize, innerSize, Gravity.CENTER),
            )
            addView(
                ringStroke,
                FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER),
            )
            addView(
                animView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
            setPadding(stagePad, stagePad, stagePad, stagePad)
        }

        val captionBadge = TextView(context).apply {
            text = if (broadcast) {
                context.getString(R.string.overlay_reaction_burst_caption_broadcast)
            } else {
                context.getString(R.string.overlay_reaction_burst_caption_private)
            }
            setTextColor(theme.captionText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = badgeDrawable(theme)
        }

        val fromPrefix = TextView(context).apply {
            text = context.getString(R.string.overlay_reaction_burst_from_prefix)
            setTextColor(theme.fromPrefixText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            setShadowLayer(dp(2).toFloat(), 0f, 1f, Color.parseColor("#88000000"))
        }
        val senderName = TextView(context).apply {
            text = displayName
            setTextColor(Color.parseColor("#FFFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(dp(3).toFloat(), 0f, 1.5f, Color.parseColor("#AA000000"))
        }
        val nameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            addView(fromPrefix)
            addView(
                senderName,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = dp(5) },
            )
        }

        val shimmerBar = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.TRANSPARENT,
                    theme.accent,
                    Color.WHITE,
                    theme.accent,
                    Color.TRANSPARENT,
                ),
            ).apply {
                cornerRadius = dp(2).toFloat()
            }
            alpha = 0.85f
        }

        val textCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            background = glassCardDrawable(theme)
            setPadding(dp(16), dp(12), dp(16), dp(14))
            addView(captionBadge)
            addView(
                shimmerBar,
                LinearLayout.LayoutParams(dp(120), dp(3)).apply {
                    topMargin = dp(10)
                    bottomMargin = dp(10)
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            addView(nameRow)
        }

        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            addView(animStage)
            addView(
                textCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(6) },
            )
        }

        root.setPadding(stagePad, stagePad, stagePad, stagePad)
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
            overlayWindowType(context),
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

        stack.alpha = 0f
        stack.scaleX = 0.88f
        stack.scaleY = 0.88f
        animView.scaleX = 0.05f
        animView.scaleY = 0.05f
        animView.alpha = 0f
        animView.rotation = -14f
        textCard.alpha = 0f
        textCard.translationY = dp(18).toFloat()
        textCard.scaleX = 0.92f
        textCard.scaleY = 0.92f
        shimmerBar.scaleX = 0.2f

        animView.post {
            animView.pivotX = animView.width * 0.5f
            animView.pivotY = animView.height * 0.5f
        }

        val pop = OvershootInterpolator(1.65f)
        val easeOut = DecelerateInterpolator(1.4f)

        val glowIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(outerGlow, "alpha", 0f, 0.92f).apply {
                    duration = 520
                    interpolator = easeOut
                },
                ObjectAnimator.ofFloat(outerGlow, "scaleX", 0.35f, 1f).apply {
                    duration = 680
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(outerGlow, "scaleY", 0.35f, 1f).apply {
                    duration = 680
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(innerGlow, "alpha", 0f, 1f).apply {
                    duration = 420
                    startDelay = 80
                },
                ObjectAnimator.ofFloat(ringStroke, "alpha", 0f, 1f).apply {
                    duration = 500
                    startDelay = 120
                },
                ObjectAnimator.ofFloat(ringStroke, "rotation", -18f, 0f).apply {
                    duration = 900
                    interpolator = easeOut
                },
            )
        }

        val heroIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(stack, "alpha", 0f, 1f).apply { duration = 320 },
                ObjectAnimator.ofFloat(stack, "scaleX", 0.88f, 1f).apply {
                    duration = 720
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(stack, "scaleY", 0.88f, 1f).apply {
                    duration = 720
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(animView, "scaleX", 0.05f, 1.14f, 1f).apply {
                    duration = 820
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(animView, "scaleY", 0.05f, 1.14f, 1f).apply {
                    duration = 820
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(animView, "alpha", 0f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(animView, "rotation", -14f, 4f, 0f).apply {
                    duration = 820
                    interpolator = easeOut
                },
            )
        }

        val cardIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(textCard, "alpha", 0f, 1f).apply { duration = 480 },
                ObjectAnimator.ofFloat(textCard, "translationY", dp(18).toFloat(), 0f).apply {
                    duration = 580
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(textCard, "scaleX", 0.92f, 1f).apply {
                    duration = 580
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(textCard, "scaleY", 0.92f, 1f).apply {
                    duration = 580
                    interpolator = pop
                },
                ObjectAnimator.ofFloat(shimmerBar, "scaleX", 0.2f, 1f).apply {
                    duration = 640
                    interpolator = easeOut
                },
            )
            startDelay = 200
        }

        glowIn.start()
        heroIn.start()
        cardIn.start()

        glowPulseAnimator = buildGlowPulse(outerGlow, innerGlow)
        glowPulseAnimator?.start()

        heroIn.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animView is LottieAnimationView) return
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(animView, "scaleX", 1f, 1.06f, 1f).apply {
                                duration = 700
                                repeatCount = 1
                            },
                            ObjectAnimator.ofFloat(animView, "scaleY", 1f, 1.06f, 1f).apply {
                                duration = 700
                                repeatCount = 1
                            },
                        )
                    }.start()
                }
            },
        )

        val hideRunnable = Runnable {
            glowPulseAnimator?.cancel()
            glowPulseAnimator = null
            val exitEase = AccelerateInterpolator(1.2f)
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(stack, "alpha", 1f, 0f).apply {
                        duration = 460
                        interpolator = exitEase
                    },
                    ObjectAnimator.ofFloat(animView, "alpha", 1f, 0f).apply { duration = 400 },
                    ObjectAnimator.ofFloat(animView, "scaleX", 1f, 1.32f).apply { duration = 400 },
                    ObjectAnimator.ofFloat(animView, "scaleY", 1f, 1.32f).apply { duration = 400 },
                    ObjectAnimator.ofFloat(animView, "rotation", 0f, 8f).apply { duration = 400 },
                    ObjectAnimator.ofFloat(outerGlow, "alpha", outerGlow.alpha, 0f).apply { duration = 420 },
                    ObjectAnimator.ofFloat(innerGlow, "alpha", innerGlow.alpha, 0f).apply { duration = 380 },
                    ObjectAnimator.ofFloat(ringStroke, "alpha", ringStroke.alpha, 0f).apply { duration = 360 },
                    ObjectAnimator.ofFloat(textCard, "alpha", 1f, 0f).apply { duration = 380 },
                    ObjectAnimator.ofFloat(textCard, "translationY", 0f, (-dp(10)).toFloat()).apply { duration = 380 },
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

    private fun buildGlowPulse(outer: View, inner: View): AnimatorSet {
        val outerPulse = ObjectAnimator.ofFloat(outer, "scaleX", 1f, 1.07f, 1f).apply {
            duration = 1_800L
            repeatCount = ValueAnimator.INFINITE
        }
        val outerPulseY = ObjectAnimator.ofFloat(outer, "scaleY", 1f, 1.07f, 1f).apply {
            duration = 1_800L
            repeatCount = ValueAnimator.INFINITE
        }
        val outerAlpha = ObjectAnimator.ofFloat(outer, "alpha", 0.88f, 0.55f, 0.88f).apply {
            duration = 1_800L
            repeatCount = ValueAnimator.INFINITE
        }
        val innerAlpha = ObjectAnimator.ofFloat(inner, "alpha", 0.95f, 0.65f, 0.95f).apply {
            duration = 1_400L
            repeatCount = ValueAnimator.INFINITE
        }
        return AnimatorSet().apply {
            playTogether(outerPulse, outerPulseY, outerAlpha, innerAlpha)
        }
    }

    private data class BurstTheme(
        val accent: Int,
        val glowStrong: Int,
        val glowSoft: Int,
        val ringStroke: Int,
        val captionText: Int,
        val fromPrefixText: Int,
        val badgeFill: Int,
        val badgeStroke: Int,
        val cardTop: Int,
        val cardBottom: Int,
        val cardStroke: Int,
    )

    private fun burstTheme(accentHex: String, broadcast: Boolean): BurstTheme {
        val accent = Color.parseColor(accentHex)
        val base = if (broadcast) {
            Color.parseColor("#FFFFB74D")
        } else {
            Color.parseColor("#FF64B5F6")
        }
        return BurstTheme(
            accent = accent,
            glowStrong = ColorUtils.setAlphaComponent(accent, 72),
            glowSoft = ColorUtils.setAlphaComponent(accent, 40),
            ringStroke = ColorUtils.setAlphaComponent(base, 140),
            captionText = if (broadcast) Color.parseColor("#FFFFF3E0") else Color.parseColor("#FFE3F2FD"),
            fromPrefixText = ColorUtils.setAlphaComponent(base, 220),
            badgeFill = ColorUtils.setAlphaComponent(base, 48),
            badgeStroke = ColorUtils.setAlphaComponent(base, 120),
            cardTop = Color.parseColor("#E6141C2E"),
            cardBottom = Color.parseColor("#CC0A0F18"),
            cardStroke = ColorUtils.setAlphaComponent(base, 100),
        )
    }

    private fun ovalGlowDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun ringStrokeDrawable(stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(2).coerceAtLeast(1), stroke)
        }

    private fun badgeDrawable(theme: BurstTheme): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(theme.badgeFill)
            setStroke(dp(1), theme.badgeStroke)
        }

    private fun glassCardDrawable(theme: BurstTheme): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(theme.cardTop, theme.cardBottom),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1).coerceAtLeast(1), theme.cardStroke)
        }

    private fun createBurstAnimView(reaction: OverlayQuickReaction, maxSidePx: Int): View {
        val stickerStem = reaction.stickerAssetStem
        if (stickerStem != null) {
            val bmp = loadStickerReactionBitmap(context, stickerStem)
            if (bmp != null) {
                return ImageView(context).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                    maxWidth = maxSidePx
                    maxHeight = maxSidePx
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
            }
        }
        val memeRes = reaction.memeDrawableRes
        if (memeRes != null) {
            return ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                maxWidth = maxSidePx
                maxHeight = maxSidePx
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
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
                        LottieValueCallback(PorterDuffColorFilter(Color.parseColor(tint), PorterDuff.Mode.SRC_ATOP)),
                    )
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                repeatCount = LottieDrawable.INFINITE
                repeatMode = LottieDrawable.RESTART
                playAnimation()
                layoutParams = ViewGroup.LayoutParams(maxSidePx, maxSidePx)
            }.also { burstLottie = it }
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

    private fun overlayWindowType(@Suppress("UNUSED_PARAMETER") context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
}
