package com.lastasylum.alliance.ui.components.team

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val palette get() = ForumTopicCardTokens.FirePalette

private data class FlameLobeSpec(
    val baseX: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val phaseOffset: Float,
)

private val flameLobes = listOf(
    FlameLobeSpec(0.10f, 0.20f, 0.44f, 0f),
    FlameLobeSpec(0.24f, 0.16f, 0.50f, 1.1f),
    FlameLobeSpec(0.40f, 0.22f, 0.58f, 2.3f),
    FlameLobeSpec(0.56f, 0.18f, 0.52f, 0.7f),
    FlameLobeSpec(0.72f, 0.20f, 0.48f, 1.8f),
    FlameLobeSpec(0.86f, 0.17f, 0.42f, 3.1f),
    FlameLobeSpec(0.50f, 0.14f, 0.36f, 4.0f),
)

private data class SmokeVeilSpec(
    val baseX: Float,
    val baseY: Float,
    val radiusFrac: Float,
    val phaseOffset: Float,
)

private val smokeVeils = listOf(
    SmokeVeilSpec(0.22f, 0.62f, 0.28f, 0.4f),
    SmokeVeilSpec(0.55f, 0.58f, 0.32f, 1.8f),
    SmokeVeilSpec(0.78f, 0.65f, 0.24f, 3.2f),
)

private data class EmberSpec(
    val baseX: Float,
    val baseY: Float,
    val phaseOffset: Float,
    val radius: Float,
    val riseSpeed: Float,
)

private val emberSpecs = listOf(
    EmberSpec(0.15f, 0.84f, 0.3f, 2.4f, 1.0f),
    EmberSpec(0.38f, 0.86f, 1.2f, 1.9f, 1.15f),
    EmberSpec(0.58f, 0.80f, 2.1f, 2.6f, 0.95f),
    EmberSpec(0.74f, 0.83f, 0.8f, 1.7f, 1.25f),
    EmberSpec(0.26f, 0.74f, 2.7f, 1.5f, 1.1f),
    EmberSpec(0.52f, 0.76f, 3.5f, 2.1f, 0.88f),
    EmberSpec(0.90f, 0.78f, 1.6f, 1.8f, 1.05f),
    EmberSpec(0.06f, 0.88f, 4.2f, 1.6f, 1.2f),
)

/** Animated flame lobe height from slow phase and fast flicker pulse. */
internal fun flameLobeHeight(baseHeight: Float, phase: Float, pulse: Float): Float {
    val sway = sin(phase * 1.35f) * 0.12f
    val intensity = 0.72f + pulse.coerceIn(0f, 1f) * 0.28f
    return baseHeight * (0.88f + sway) * intensity
}

/** Ember alpha fades as it rises (0 = base, 1 = apex). */
internal fun emberOpacityAtRise(riseFraction: Float): Float {
    val t = riseFraction.coerceIn(0f, 1f)
    return (1f - t) * (0.35f + (1f - t) * 0.45f)
}

private fun buildFlamePath(
    cx: Float,
    baseY: Float,
    halfWidth: Float,
    topY: Float,
    lean: Float,
): Path {
    val path = Path()
    val left = cx - halfWidth
    val right = cx + halfWidth
    val midY = (baseY + topY) * 0.52f
    path.moveTo(left, baseY)
    path.quadraticTo(
        cx + lean - halfWidth * 0.35f,
        midY,
        cx + lean * 0.55f,
        topY,
    )
    path.quadraticTo(
        cx + lean + halfWidth * 0.35f,
        midY,
        right,
        baseY,
    )
    path.close()
    return path
}

private fun buildCorePath(
    cx: Float,
    baseY: Float,
    halfWidth: Float,
    topY: Float,
    lean: Float,
): Path {
    val coreHalf = halfWidth * 0.42f
    val coreTop = topY + (baseY - topY) * 0.18f
    return buildFlamePath(cx, baseY, coreHalf, coreTop, lean * 0.7f)
}

/**
 * Animated fire layer for forum topic cards with unread messages (Hot).
 */
fun DrawScope.drawForumFireBackground(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    flicker: Float,
    heatPhase: Float,
    emberBoost: Float = 1f,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val pulse = 0.72f + flicker * 0.28f
    val heatAngle = heatPhase * 2f * PI.toFloat()

    drawCoalBed(w, h, corner, pulse, heatAngle, flicker)
    drawSmokeVeils(w, h, heatPhase, heatAngle, pulse)
    drawFlameBodies(w, h, accent, drift, flicker, pulse)
    drawEmberField(w, h, drift, flicker, emberBoost)
    drawRimGlow(w, h, corner, accent, pulse, flicker)
    drawForumEdgeShimmer(w, h, corner, drift, flicker, accent)
}

/** Lite tier: edge shimmer + bottom glow without full flame bodies. */
fun DrawScope.drawForumFireBackgroundLite(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    flicker: Float,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val pulse = 0.65f + flicker * 0.25f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                palette.smoke.copy(alpha = 0.08f),
                palette.deep.copy(alpha = 0.22f * pulse),
            ),
            startY = h * 0.55f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawForumEdgeShimmer(w, h, corner, drift, flicker, accent, lite = true)
}

/** Horizontal shimmer sweep along top edge. */
fun DrawScope.drawForumEdgeShimmer(
    w: Float,
    h: Float,
    corner: Float,
    drift: Float,
    flicker: Float,
    accent: ForumTopicCardTokens.Accent,
    lite: Boolean = false,
) {
    val stripH = if (lite) 2.dp.toPx() else 3.dp.toPx()
    val shift = drift * w * 0.6f
    val alphaScale = if (lite) 0.55f else 1f
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                palette.amber.copy(alpha = (0.12f + flicker * 0.18f) * alphaScale),
                palette.orange.copy(alpha = (0.28f + flicker * 0.22f) * alphaScale),
                accent.primary.copy(alpha = (0.15f + flicker * 0.12f) * alphaScale),
                palette.orange.copy(alpha = (0.22f + flicker * 0.18f) * alphaScale),
                Color.Transparent,
            ),
            startX = -shift,
            endX = w * 1.4f - shift,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, stripH),
        cornerRadius = CornerRadius(corner, corner),
    )
}

/** Top activity strip drawn in canvas — zero layout height impact. */
fun DrawScope.drawForumActivityStripOverlay(
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    drift: Float,
    pulse: Float,
    flicker: Float,
    hot: Boolean,
) {
    val w = size.width
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val stripH = if (hot) {
        ForumTopicCardTokens.activityStripHeightHot.toPx()
    } else {
        ForumTopicCardTokens.activityStripHeight.toPx()
    }
    val shift = drift * w * 0.35f
    val colors = when {
        hot -> listOf(
            Color.Transparent,
            palette.amber.copy(alpha = 0.16f + pulse * 0.2f),
            palette.orange.copy(alpha = 0.24f + flicker * 0.2f),
            accent.primary.copy(alpha = 0.12f + pulse * 0.15f),
            Color.Transparent,
        )
        activityLevel == ForumTopicCardTokens.ActivityLevel.Warm -> listOf(
            Color.Transparent,
            accent.primary.copy(alpha = 0.12f + pulse * 0.1f),
            accent.secondary.copy(alpha = 0.16f + pulse * 0.08f),
            Color.Transparent,
        )
        else -> return
    }
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = colors,
            startX = -shift,
            endX = w * 1.35f - shift,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(w, stripH),
        cornerRadius = CornerRadius(corner, corner),
    )
}

private fun DrawScope.drawCoalBed(
    w: Float,
    h: Float,
    corner: Float,
    pulse: Float,
    heatAngle: Float,
    flicker: Float,
) {
    val shimmer = sin(heatAngle) * 0.04f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                palette.smoke.copy(alpha = 0.10f + flicker * 0.05f),
                palette.coal.copy(alpha = 0.20f * pulse + shimmer),
                palette.deep.copy(alpha = 0.36f * pulse),
            ),
            startY = h * 0.32f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.orange.copy(alpha = 0.28f * pulse),
                palette.deep.copy(alpha = 0.18f * pulse),
                Color.Transparent,
            ),
            center = Offset(w * (0.5f + sin(heatAngle * 0.7f) * 0.08f), h * 0.94f),
            radius = w * 0.55f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                palette.orange.copy(alpha = 0.16f * pulse),
                palette.amber.copy(alpha = 0.22f * pulse),
                palette.orange.copy(alpha = 0.16f * pulse),
                Color.Transparent,
            ),
            startX = 0f,
            endX = w,
        ),
        topLeft = Offset(0f, h - 7.dp.toPx()),
        size = Size(w, 7.dp.toPx()),
        cornerRadius = CornerRadius(3.5.dp.toPx()),
    )
}

private fun DrawScope.drawSmokeVeils(
    w: Float,
    h: Float,
    heatPhase: Float,
    heatAngle: Float,
    pulse: Float,
) {
    smokeVeils.forEach { veil ->
        val phase = heatPhase * 2f * PI.toFloat() + veil.phaseOffset
        val rise = (sin(phase) * 0.5f + 0.5f) * h * 0.14f
        val cx = w * veil.baseX + sin(phase * 1.3f) * w * 0.03f
        val cy = h * veil.baseY - rise
        val radius = w * veil.radiusFrac * (0.92f + sin(heatAngle + veil.phaseOffset) * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.smoke.copy(alpha = 0.14f * pulse),
                    palette.deep.copy(alpha = 0.08f * pulse),
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

private fun DrawScope.drawFlameBodies(
    w: Float,
    h: Float,
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    flicker: Float,
    pulse: Float,
) {
    val baseY = h * 0.92f
    val driftAngle = drift * 2f * PI.toFloat()

    flameLobes.forEach { lobe ->
        val phase = driftAngle + lobe.phaseOffset
        val sway = sin(phase) * w * 0.028f
        val cx = w * lobe.baseX + sway
        val baseHeight = h * lobe.heightFrac
        val flameH = flameLobeHeight(baseHeight, phase, flicker)
        val halfWidth = w * lobe.widthFrac * 0.5f * (0.9f + flicker * 0.12f)
        val topY = (baseY - flameH).coerceAtLeast(h * 0.06f)
        val lean = sway * 0.85f

        val bodyPath = buildFlamePath(cx, baseY, halfWidth, topY, lean)
        drawPath(
            path = bodyPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.primary.copy(alpha = 0.12f * pulse),
                    palette.amber.copy(alpha = 0.48f * pulse),
                    palette.orange.copy(alpha = 0.55f * pulse),
                    palette.deep.copy(alpha = 0.42f * pulse),
                ),
                startY = topY,
                endY = baseY,
            ),
        )

        val corePath = buildCorePath(cx, baseY, halfWidth, topY, lean)
        drawPath(
            path = corePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.core.copy(alpha = 0.22f * flicker),
                    palette.amber.copy(alpha = 0.55f * pulse),
                    palette.orange.copy(alpha = 0.35f * pulse),
                    Color.Transparent,
                ),
                startY = topY,
                endY = baseY - (baseY - topY) * 0.35f,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawEmberField(
    w: Float,
    h: Float,
    drift: Float,
    flicker: Float,
    emberBoost: Float = 1f,
) {
    val driftAngle = drift * 2f * PI.toFloat()
    val maxRise = h * 0.26f
    val boost = emberBoost.coerceIn(0.5f, 1.6f)

    emberSpecs.forEach { ember ->
        val phase = driftAngle + ember.phaseOffset
        val riseNorm = (sin(phase * ember.riseSpeed) * 0.5f + 0.5f)
        val rise = riseNorm * maxRise
        val x = w * ember.baseX + sin(phase * 1.65f) * w * 0.045f
        val y = h * ember.baseY - rise
        val alpha = emberOpacityAtRise(riseNorm) * (0.65f + flicker * 0.35f) * boost
        val radius = ember.radius * (0.75f + flicker * 0.35f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.core.copy(alpha = alpha * 0.85f),
                    palette.amber.copy(alpha = alpha),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = radius * 1.8f,
            ),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawRimGlow(
    w: Float,
    h: Float,
    corner: Float,
    accent: ForumTopicCardTokens.Accent,
    pulse: Float,
    flicker: Float,
) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                palette.amber.copy(alpha = 0.06f + pulse * 0.04f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = h * 0.22f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                accent.primary.copy(alpha = 0.15f * flicker),
                Color.Transparent,
                Color.Transparent,
                accent.primary.copy(alpha = 0.15f * flicker),
            ),
            startX = 0f,
            endX = w,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}
