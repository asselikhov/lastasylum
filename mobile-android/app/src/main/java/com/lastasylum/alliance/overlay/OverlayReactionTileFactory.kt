package com.lastasylum.alliance.overlay

import android.content.Context
import android.graphics.Color
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
    fun buildTile(
        request: OverlayReactionBurstRequest,
        mode: OverlayReactionTileMode,
        mergeCount: Int = 1,
        playLottie: Boolean = true,
    ): OverlayReactionBuiltTile {
        val tilePx = when (mode) {
            OverlayReactionTileMode.HERO -> OverlayReactionStageLayout.heroTileSizePx(dp)
            OverlayReactionTileMode.MINI -> OverlayReactionStageLayout.miniTileSizePx(dp)
        }
        val card = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
            background = OverlayReactionBurstLayout.slotCardBackground()
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
        val textPayload = decodeTextReactionId(request.reactionId)
        if (textPayload != null) {
            messageView = OverlayReactionTextBurstUi.createTileTextView(
                context,
                textPayload,
                tilePx,
                mini = mode == OverlayReactionTileMode.MINI,
            ).also { view ->
                view.setTag(R.id.tag_overlay_reaction_message, true)
                visualHost.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER,
                    ),
                )
            }
        } else {
            val reaction = overlayQuickReactionById(context, request.reactionId)
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
        }
        column.addView(
            visualHost,
            LinearLayout.LayoutParams(tilePx, tilePx),
        )
        val captionView = if (mode == OverlayReactionTileMode.HERO) {
            OverlayReactionCaption.createCaptionLine(
                context = context,
                displayName = request.fromDisplayName,
                broadcast = request.broadcast,
                maxWidthPx = dp(OverlayReactionStageLayout.HERO_CAPTION_MAX_WIDTH_DP),
                mergeCount = mergeCount,
            ).also { caption ->
                column.addView(
                    caption,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = dp(OverlayReactionStageLayout.HERO_CAPTION_TOP_MARGIN_DP)
                    },
                )
            }
        } else {
            card.contentDescription = OverlayReactionCaption.miniContentDescription(
                context,
                request.fromDisplayName,
                request.broadcast,
            )
            null
        }
        card.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        return OverlayReactionBuiltTile(card, captionView, visualHost, messageView, lottie)
    }

    fun rebuildVisual(
        tile: OverlayReactionBuiltTile,
        request: OverlayReactionBurstRequest,
        mode: OverlayReactionTileMode,
        playLottie: Boolean,
    ) {
        tile.visualHost.removeAllViews()
        val tilePx = when (mode) {
            OverlayReactionTileMode.HERO -> OverlayReactionStageLayout.heroTileSizePx(dp)
            OverlayReactionTileMode.MINI -> OverlayReactionStageLayout.miniTileSizePx(dp)
        }
        val textPayload = decodeTextReactionId(request.reactionId)
        if (textPayload != null) {
            val messageView = OverlayReactionTextBurstUi.createTileTextView(
                context,
                textPayload,
                tilePx,
                mini = mode == OverlayReactionTileMode.MINI,
            )
            tile.visualHost.addView(
                messageView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        } else {
            val reaction = overlayQuickReactionById(context, request.reactionId)
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
}
