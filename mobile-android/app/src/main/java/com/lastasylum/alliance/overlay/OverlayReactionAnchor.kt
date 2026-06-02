package com.lastasylum.alliance.overlay

import android.graphics.Rect
import android.view.Gravity
import android.view.View

/** Screen-space anchor for positioning the incoming-reaction stack. */
internal data class OverlayReactionAnchorRect(
    val bounds: Rect,
    val horizontalAlign: HorizontalAlign,
    val maxStackWidthPx: Int? = null,
) {
    val centerX: Int get() = (bounds.left + bounds.right) / 2
    val bottom: Int get() = bounds.bottom
}

internal enum class HorizontalAlign {
    /** Stack under anchor, aligned to screen end (top-right HUD). */
    END,
    /** Stack centered under anchor (reaction grid in popover). */
    CENTER,
    /** Stage centered on screen width; Y from anchor bottom. */
    SCREEN_CENTER,
}

internal fun interface OverlayReactionAnchorProvider {
    fun reactionBurstAnchor(): OverlayReactionAnchorRect?
}

internal data class OverlayReactionStageWindowPlacement(
    val windowGravity: Int,
    val x: Int,
    val y: Int,
    val stackContentGravity: Int,
)

internal object OverlayReactionAnchorLayout {
    const val GAP_BELOW_ANCHOR_DP = 12
    const val FALLBACK_HUD_CHIP_HEIGHT_DP = 48

    fun anchorFromView(
        view: View,
        align: HorizontalAlign,
        maxStackWidthPx: Int? = null,
    ): OverlayReactionAnchorRect? {
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect) || rect.isEmpty()) return null
        return OverlayReactionAnchorRect(rect, align, maxStackWidthPx)
    }

    /** Anchor below the union of overlay HUD rows; burst is centered on screen width. */
    fun hudBurstAnchor(
        statusBounds: Rect?,
        topRightBounds: Rect?,
        screenWidthPx: Int,
    ): OverlayReactionAnchorRect? {
        val parts = listOfNotNull(statusBounds, topRightBounds)
            .filter { rect -> rect.right > rect.left && rect.bottom > rect.top }
        if (parts.isEmpty() || screenWidthPx <= 0) return null
        val top = parts.minOf { it.top }
        val bottom = parts.maxOf { it.bottom }
        return OverlayReactionAnchorRect(
            bounds = Rect(0, top, screenWidthPx, bottom),
            horizontalAlign = HorizontalAlign.SCREEN_CENTER,
        )
    }

    fun fallbackTopEndHud(screenWidthPx: Int, dp: (Int) -> Int): OverlayReactionAnchorRect {
        val x = dp(OverlayHudLayout.WINDOW_X_DP)
        val y = dp(OverlayHudLayout.WINDOW_Y_DP)
        val chipH = dp(FALLBACK_HUD_CHIP_HEIGHT_DP)
        val chipW = dp(120)
        return OverlayReactionAnchorRect(
            bounds = Rect(
                screenWidthPx - x - chipW,
                y,
                screenWidthPx - x,
                y + chipH,
            ),
            horizontalAlign = HorizontalAlign.SCREEN_CENTER,
        )
    }

    fun computeStageWindowPlacement(
        anchor: OverlayReactionAnchorRect,
        screenWidthPx: Int,
        dp: (Int) -> Int,
        safeTopMinY: Int? = null,
    ): OverlayReactionStageWindowPlacement {
        val gap = dp(GAP_BELOW_ANCHOR_DP)
        var y = anchor.bottom + gap
        safeTopMinY?.let { minY -> y = maxOf(y, minY) }
        return when (anchor.horizontalAlign) {
            HorizontalAlign.END -> OverlayReactionStageWindowPlacement(
                windowGravity = Gravity.TOP or Gravity.END,
                x = (screenWidthPx - anchor.bounds.right).coerceAtLeast(0),
                y = y,
                stackContentGravity = Gravity.END,
            )
            HorizontalAlign.CENTER -> OverlayReactionStageWindowPlacement(
                windowGravity = Gravity.TOP or Gravity.START,
                x = 0,
                y = y,
                stackContentGravity = Gravity.CENTER_HORIZONTAL,
            )
            HorizontalAlign.SCREEN_CENTER -> OverlayReactionStageWindowPlacement(
                windowGravity = Gravity.TOP or Gravity.START,
                x = 0,
                y = y,
                stackContentGravity = Gravity.CENTER_HORIZONTAL,
            )
        }
    }

    /** After stage is measured, center stack under anchor for CENTER alignment. */
    fun adjustCenteredWindowX(
        anchor: OverlayReactionAnchorRect,
        stageWidthPx: Int,
        screenWidthPx: Int,
    ): Int {
        val maxX = (screenWidthPx - stageWidthPx).coerceAtLeast(0)
        return when (anchor.horizontalAlign) {
            HorizontalAlign.SCREEN_CENTER -> maxX / 2
            else -> (anchor.centerX - stageWidthPx / 2).coerceIn(0, maxX)
        }
    }

    fun clampStackWidthPx(contentWidthPx: Int, anchor: OverlayReactionAnchorRect?): Int {
        val max = anchor?.maxStackWidthPx ?: return contentWidthPx
        return minOf(contentWidthPx, max)
    }
}
