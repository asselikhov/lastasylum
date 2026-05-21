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
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import com.airbnb.lottie.model.KeyPath
import com.airbnb.lottie.value.LottieValueCallback
import com.lastasylum.alliance.R

internal data class OverlayReactionBurstRequest(
    val fromDisplayName: String,
    val reactionId: String,
    val broadcast: Boolean,
    val outgoingPreview: Boolean = false,
)

/**
 * Очередь полноэкранных вспышек реакций: без наложения, плавный enter/exit.
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

    fun isActive(): Boolean = showing || queue.isNotEmpty() || burstRoot != null

    fun clear() {
        hideBurstRunnable?.let { mainHandler.removeCallbacks(it) }
        hideBurstRunnable = null
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
        val reactionTitle = context.getString(reaction.labelRes)

        val root = OverlayPassthroughMultitouchFrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
        }
        val scrim = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(200, 4, 10, 22),
                    Color.argb(140, 8, 16, 32),
                    Color.argb(200, 2, 6, 14),
                ),
            )
        }
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        val burstAnimSize = when {
            reaction.memeDrawableRes != null -> dp(210)
            reaction.lottieRawRes != null -> dp(172)
            else -> dp(128)
        }
        val animView = createBurstAnimView(reaction)
        val accent = Color.parseColor(reaction.burstAccentHex)
        val accentSoft = (accent and 0x40FFFFFF.toInt()) or 0x20000000

        val glowRing = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentSoft)
            }
        }

        val reactionLabel = TextView(context).apply {
            text = reactionTitle
            setTextColor(Color.parseColor("#FFE8F4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = 0.03f
        }

        val scopeLabel = when {
            request.outgoingPreview -> context.getString(R.string.overlay_reaction_burst_scope_sent)
            request.broadcast -> context.getString(R.string.overlay_reaction_burst_scope_broadcast)
            else -> context.getString(R.string.overlay_reaction_burst_scope_private)
        }
        val scopeView = TextView(context).apply {
            text = scopeLabel
            setTextColor(
                if (request.broadcast) Color.parseColor("#FFFFB74D")
                else Color.parseColor("#FF90CAF9"),
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val caption = TextView(context).apply {
            text = if (request.outgoingPreview) {
                context.getString(R.string.overlay_reaction_burst_caption_sent)
            } else {
                context.getString(R.string.overlay_reaction_burst_caption)
            }
            setTextColor(Color.parseColor("#B8C8D8E8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val nameLabel = TextView(context).apply {
            text = displayName
            setTextColor(Color.parseColor("#FFF8FAFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setShadowLayer(dp(1).toFloat(), 0f, 1.2f, Color.parseColor("#66000000"))
        }

        val accentBar = View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.TRANSPARENT,
                    accent,
                    Color.TRANSPARENT,
                ),
            ).apply {
                cornerRadius = dp(2).toFloat()
            }
        }

        val textInner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            addView(reactionLabel)
            addView(
                accentBar,
                LinearLayout.LayoutParams(dp(100), dp(3)).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(8)
                    gravity = Gravity.CENTER_HORIZONTAL
                },
            )
            addView(caption)
            addView(
                scopeView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(5) },
            )
            addView(
                nameLabel,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(3) },
            )
        }

        val cardStroke = if (request.broadcast) {
            Color.parseColor("#88FFB74D")
        } else {
            Color.parseColor("#8890CAF9")
        }
        val textCard = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#F0182438"))
                setStroke(dp(1).coerceAtLeast(1), cardStroke)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }
            addView(textInner)
        }

        val animHost = FrameLayout(context).apply {
            addView(
                glowRing,
                FrameLayout.LayoutParams(
                    (burstAnimSize * 1.15f).toInt(),
                    (burstAnimSize * 1.15f).toInt(),
                    Gravity.CENTER,
                ),
            )
            addView(
                animView,
                FrameLayout.LayoutParams(burstAnimSize, burstAnimSize, Gravity.CENTER),
            )
        }

        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(animHost)
            addView(
                textCard,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(20) },
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
            overlayWindowType(context),
            OverlayWindowLayout.reactionBurstWindowFlags(),
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            OverlayWindowLayout.applyFullscreenOverlayWindow(context, this)
        }

        if (runCatching { windowManager.addView(root, params) }.isFailure) {
            onFinished()
            return
        }
        burstRoot = root

        val visibleMs = if (request.outgoingPreview) {
            OUTGOING_BURST_VISIBLE_MS
        } else {
            OVERLAY_REACTION_BURST_VISIBLE_MS
        }

        scrim.alpha = 0f
        animView.scaleX = 0.2f
        animView.scaleY = 0.2f
        animView.alpha = 0f
        glowRing.scaleX = 0.5f
        glowRing.scaleY = 0.5f
        glowRing.alpha = 0f
        textCard.alpha = 0f
        textCard.translationY = dp(12).toFloat()

        animView.post {
            animView.pivotX = animView.width * 0.5f
            animView.pivotY = animView.height * 0.5f
        }

        val overshoot = OvershootInterpolator(1.35f)
        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(scrim, "alpha", 0f, 1f).apply { duration = 280 },
                ObjectAnimator.ofFloat(animView, "scaleX", 0.2f, 1.12f, 1f).apply {
                    duration = 780
                    interpolator = overshoot
                },
                ObjectAnimator.ofFloat(animView, "scaleY", 0.2f, 1.12f, 1f).apply {
                    duration = 780
                    interpolator = overshoot
                },
                ObjectAnimator.ofFloat(animView, "alpha", 0f, 1f).apply { duration = 320 },
                ObjectAnimator.ofFloat(glowRing, "scaleX", 0.5f, 1f).apply { duration = 820 },
                ObjectAnimator.ofFloat(glowRing, "scaleY", 0.5f, 1f).apply { duration = 820 },
                ObjectAnimator.ofFloat(glowRing, "alpha", 0f, 0.85f).apply { duration = 400 },
            )
        }
        val cardIn = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(textCard, "alpha", 0f, 1f).apply { duration = 450 },
                ObjectAnimator.ofFloat(textCard, "translationY", dp(12).toFloat(), 0f).apply {
                    duration = 520
                    interpolator = overshoot
                },
            )
            startDelay = 160
        }
        enter.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animView is LottieAnimationView) return
                    val beat = AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(animView, "scaleX", 1f, 1.08f, 1f).apply {
                                duration = 640
                                repeatCount = 1
                            },
                            ObjectAnimator.ofFloat(animView, "scaleY", 1f, 1.08f, 1f).apply {
                                duration = 640
                                repeatCount = 1
                            },
                        )
                    }
                    beat.start()
                }
            },
        )
        enter.start()
        cardIn.start()

        val hideRunnable = Runnable {
            val exit = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(scrim, "alpha", 1f, 0f).apply { duration = 420 },
                    ObjectAnimator.ofFloat(animView, "alpha", 1f, 0f).apply { duration = 380 },
                    ObjectAnimator.ofFloat(animView, "scaleX", 1f, 1.28f).apply { duration = 380 },
                    ObjectAnimator.ofFloat(animView, "scaleY", 1f, 1.28f).apply { duration = 380 },
                    ObjectAnimator.ofFloat(textCard, "alpha", 1f, 0f).apply { duration = 360 },
                    ObjectAnimator.ofFloat(textCard, "translationY", 0f, (-dp(8)).toFloat()).apply { duration = 360 },
                    ObjectAnimator.ofFloat(glowRing, "alpha", 0.85f, 0f).apply { duration = 360 },
                )
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            hideBurstImmediate()
                            onFinished()
                        }
                    },
                )
            }
            exit.start()
        }
        hideBurstRunnable = hideRunnable
        mainHandler.postDelayed(hideRunnable, visibleMs)
    }

    private fun createBurstAnimView(reaction: OverlayQuickReaction): View {
        val stickerStem = reaction.stickerAssetStem
        if (stickerStem != null) {
            val bmp = loadStickerReactionBitmap(context, stickerStem)
            if (bmp != null) {
                return ImageView(context).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            }
        }
        val memeRes = reaction.memeDrawableRes
        if (memeRes != null) {
            return ImageView(context).apply {
                setImageDrawable(AppCompatResources.getDrawable(context, memeRes))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.parseColor("#22182533"))
                }
                clipToOutline = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                }
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
                repeatCount = 1
                playAnimation()
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

    companion object {
        private const val OUTGOING_BURST_VISIBLE_MS = 1_400L
    }
}
