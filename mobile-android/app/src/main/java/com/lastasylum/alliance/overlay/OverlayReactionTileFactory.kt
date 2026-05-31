package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.lastasylum.alliance.R

internal data class OverlayReactionBuiltTile(
    val card: FrameLayout,
    val captionView: TextView?,
    val visualHost: FrameLayout,
    val messageView: TextView?,
    val lottie: LottieAnimationView?,
)

internal class OverlayReactionTileFactory(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val visualFactory: OverlayReactionVisualFactory,
) {
    private fun metrics() = OverlayReactionBurstLayout.metrics(context, dp)

    fun buildTile(
        request: OverlayReactionBurstRequest,
        mode: OverlayReactionTileMode,
        mergeCount: Int = 1,
        playLottie: Boolean = true,
    ): OverlayReactionBuiltTile {
        val m = metrics()
        val tilePx = when (mode) {
            OverlayReactionTileMode.HERO -> OverlayReactionStageLayout.heroTileSizePx(m.screenWidthPx, dp)
            OverlayReactionTileMode.MINI -> OverlayReactionStageLayout.miniTileSizePx(m.screenWidthPx, dp)
        }
        val textPayload = decodeTextReactionId(request.reactionId)
        val isHeroText = textPayload != null && mode == OverlayReactionTileMode.HERO
        val columnWidthPx = if (isHeroText) {
            OverlayReactionBurstLayout.textMessageMaxWidthPx(m, dp(120))
        } else {
            tilePx
        }
        val card = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            alpha = OverlayReactionBurstLayout.CONTENT_ALPHA
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }
        val visualHost = FrameLayout(context).apply {
            setTag(R.id.tag_overlay_reaction_anim_host, true)
            clipChildren = false
            clipToPadding = false
        }
        var messageView: TextView? = null
        var lottie: LottieAnimationView? = null
        if (textPayload != null) {
            val textSp = if (mode == OverlayReactionTileMode.HERO) {
                OverlayReactionStageLayout.TEXT_HERO_SP
            } else {
                OverlayReactionStageLayout.TEXT_MINI_SP
            }
            val maxLines = if (mode == OverlayReactionTileMode.HERO) {
                OverlayReactionStageLayout.TEXT_HERO_MAX_LINES
            } else {
                2
            }
            val textWidth = if (mode == OverlayReactionTileMode.HERO) columnWidthPx else tilePx
            messageView = OverlayReactionTextBurstUi.createTileTextView(
                context,
                textPayload,
                textWidth,
                textSp = textSp,
                maxLines = maxLines,
            ).also { view ->
                view.setTag(R.id.tag_overlay_reaction_message, true)
                visualHost.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            }
            column.addView(
                visualHost,
                LinearLayout.LayoutParams(columnWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        } else {
            val reaction = overlayQuickReactionById(context, request.reactionId)
            if (mode == OverlayReactionTileMode.HERO) {
                addHeroGlow(visualHost, reaction.burstAccentHex, tilePx)
            }
            val animView = visualFactory.createAnimView(reaction, tilePx, playLottie)
            lottie = animView as? LottieAnimationView
            visualHost.addView(
                animView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            column.addView(
                visualHost,
                LinearLayout.LayoutParams(tilePx, tilePx),
            )
        }
        val captionView = if (mode == OverlayReactionTileMode.HERO) {
            OverlayReactionCaption.createCaptionLine(
                context = context,
                displayName = request.fromDisplayName,
                broadcast = request.broadcast,
                mergeCount = mergeCount,
            ).also { caption ->
                column.addView(
                    caption,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(OverlayReactionStageLayout.HERO_CAPTION_TOP_MARGIN_DP)
                    },
                )
            }
        } else {
            OverlayReactionCaption.createMiniNick(
                context = context,
                displayName = request.fromDisplayName,
                broadcast = request.broadcast,
            ).also { nick ->
                column.addView(
                    nick,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(2)
                    },
                )
            }
        }
        card.contentDescription = OverlayReactionCaption.miniContentDescription(
            context,
            request.fromDisplayName,
            request.broadcast,
        )
        card.addView(
            column,
            FrameLayout.LayoutParams(
                columnWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return OverlayReactionBuiltTile(
            card = card,
            captionView = if (mode == OverlayReactionTileMode.HERO) captionView else null,
            visualHost = visualHost,
            messageView = messageView,
            lottie = lottie,
        )
    }

    fun rebuildVisual(
        tile: OverlayReactionBuiltTile,
        request: OverlayReactionBurstRequest,
        mode: OverlayReactionTileMode,
        playLottie: Boolean,
    ) {
        tile.visualHost.removeAllViews()
        val m = metrics()
        val tilePx = when (mode) {
            OverlayReactionTileMode.HERO -> OverlayReactionStageLayout.heroTileSizePx(m.screenWidthPx, dp)
            OverlayReactionTileMode.MINI -> OverlayReactionStageLayout.miniTileSizePx(m.screenWidthPx, dp)
        }
        val textPayload = decodeTextReactionId(request.reactionId)
        if (textPayload != null) {
            val textSp = if (mode == OverlayReactionTileMode.HERO) {
                OverlayReactionStageLayout.TEXT_HERO_SP
            } else {
                OverlayReactionStageLayout.TEXT_MINI_SP
            }
            val maxLines = if (mode == OverlayReactionTileMode.HERO) {
                OverlayReactionStageLayout.TEXT_HERO_MAX_LINES
            } else {
                2
            }
            val textWidth = if (mode == OverlayReactionTileMode.HERO) {
                OverlayReactionBurstLayout.textMessageMaxWidthPx(m, dp(120))
            } else {
                tilePx
            }
            val messageView = OverlayReactionTextBurstUi.createTileTextView(
                context,
                textPayload,
                textWidth,
                textSp = textSp,
                maxLines = maxLines,
            )
            tile.visualHost.addView(
                messageView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ),
            )
        } else {
            val reaction = overlayQuickReactionById(context, request.reactionId)
            if (mode == OverlayReactionTileMode.HERO) {
                addHeroGlow(tile.visualHost, reaction.burstAccentHex, tilePx)
            }
            val animView = visualFactory.createAnimView(reaction, tilePx, playLottie)
            tile.visualHost.addView(
                animView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }
    }

    private fun addHeroGlow(host: FrameLayout, accentHex: String, sizePx: Int) {
        val accent = runCatching { Color.parseColor(accentHex) }.getOrDefault(Color.parseColor("#66FFE082"))
        val glow = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = sizePx * 0.55f
                setColors(
                    intArrayOf(
                        Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.TRANSPARENT,
                    ),
                )
            }
        }
        host.addView(
            glow,
            FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER),
        )
    }
}
