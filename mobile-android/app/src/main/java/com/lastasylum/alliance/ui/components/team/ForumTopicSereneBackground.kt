package com.lastasylum.alliance.ui.components.team

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlin.math.PI
import kotlin.math.sin

private data class SereneOrbSpec(
    val baseX: Float,
    val baseY: Float,
    val radiusFrac: Float,
    val phaseOffset: Float,
)

private val sereneOrbs = listOf(
    SereneOrbSpec(0.18f, 0.42f, 0.34f, 0.5f),
    SereneOrbSpec(0.62f, 0.36f, 0.30f, 2.1f),
    SereneOrbSpec(0.84f, 0.58f, 0.26f, 3.6f),
)

/** Breathing alpha for read-topic serene background (0..1 phase). */
internal fun sereneBreathAlpha(phase: Float): Float {
    val t = (sin(phase * 2f * PI.toFloat()) * 0.5f + 0.5f).coerceIn(0f, 1f)
    return 0.04f + t * 0.06f
}

/** Orb veil alpha from slow drift phase. */
internal fun sereneOrbAlpha(phase: Float, pulse: Float): Float {
    val sway = (sin(phase * 1.4f) * 0.5f + 0.5f).coerceIn(0f, 1f)
    return (0.04f + sway * 0.04f) * (0.75f + pulse * 0.25f)
}

/**
 * Full serene canvas for read forum topic cards — slow aurora + soft orbs.
 */
fun DrawScope.drawForumSereneBackground(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    pulse: Float,
    breathPhase: Float,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val breath = sereneBreathAlpha(breathPhase)
    val driftAngle = drift * 2f * PI.toFloat()

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                PremiumColors.accentCyan.copy(alpha = breath * 0.55f),
                accent.primary.copy(alpha = breath),
                accent.secondary.copy(alpha = breath * 0.75f),
                Color.Transparent,
            ),
            startY = h * 0.08f,
            endY = h * 0.92f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                PremiumColors.accentPurpleDeep.copy(alpha = breath * 0.45f),
                Color.Transparent,
            ),
            startY = h * 0.55f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawSereneOrbs(w, h, driftAngle, pulse)
    drawForumSereneAuroraStrip(w, h, corner, drift, pulse, accent, lite = false)
}

/** Lite serene: aurora strip + bottom glow only. */
fun DrawScope.drawForumSereneBackgroundLite(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    pulse: Float,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                accent.secondary.copy(alpha = 0.05f + pulse * 0.04f),
                accent.primary.copy(alpha = 0.08f + pulse * 0.05f),
            ),
            startY = h * 0.62f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawForumSereneAuroraStrip(w, h, corner, drift, pulse, accent, lite = true)
}

private fun DrawScope.drawSereneOrbs(
    w: Float,
    h: Float,
    driftAngle: Float,
    pulse: Float,
) {
    sereneOrbs.forEach { orb ->
        val phase = driftAngle + orb.phaseOffset
        val cx = w * orb.baseX + sin(phase * 0.9f) * w * 0.035f
        val cy = h * orb.baseY + sin(phase * 0.65f) * h * 0.04f
        val radius = w * orb.radiusFrac * (0.94f + sin(phase * 1.1f) * 0.06f)
        val alpha = sereneOrbAlpha(phase, pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    PremiumColors.accentCyan.copy(alpha = alpha),
                    accentOrbSecondary(alpha),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = radius,
            ),
            radius = radius,
            center = Offset(cx, cy),
        )
    }
}

private fun accentOrbSecondary(alpha: Float): Color =
    PremiumColors.accentPurpleDeep.copy(alpha = alpha * 0.65f)

private fun DrawScope.drawForumSereneAuroraStrip(
    w: Float,
    h: Float,
    corner: Float,
    drift: Float,
    pulse: Float,
    accent: ForumTopicCardTokens.Accent,
    lite: Boolean,
) {
    val stripH = if (lite) 2.dp.toPx() else 2.5.dp.toPx()
    val shift = drift * w * 0.45f
    val alphaScale = if (lite) 0.6f else 1f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                accent.secondary.copy(alpha = (0.08f + pulse * 0.06f) * alphaScale),
                PremiumColors.accentCyan.copy(alpha = (0.14f + pulse * 0.08f) * alphaScale),
                accent.primary.copy(alpha = (0.10f + pulse * 0.06f) * alphaScale),
                Color.Transparent,
            ),
            startX = -shift,
            endX = w * 1.25f - shift,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, stripH),
        cornerRadius = CornerRadius(corner, corner),
    )
}
