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
    val speedMul: Float,
)

private val flameLobes = listOf(
    FlameLobeSpec(0.10f, 0.20f, 0.44f, 0f, 1.0f),
    FlameLobeSpec(0.24f, 0.16f, 0.50f, 1.1f, 1.15f),
    FlameLobeSpec(0.40f, 0.22f, 0.58f, 2.3f, 0.92f),
    FlameLobeSpec(0.56f, 0.18f, 0.52f, 0.7f, 1.08f),
    FlameLobeSpec(0.72f, 0.20f, 0.48f, 1.8f, 1.22f),
    FlameLobeSpec(0.86f, 0.17f, 0.42f, 3.1f, 0.88f),
    FlameLobeSpec(0.50f, 0.14f, 0.36f, 4.0f, 1.05f),
)

private data class SmokeVeilSpec(
    val baseX: Float,
    val baseY: Float,
    val radiusFrac: Float,
    val phaseOffset: Float,
    val speedMul: Float,
)

private val smokeVeils = listOf(
    SmokeVeilSpec(0.22f, 0.62f, 0.28f, 0.4f, 0.9f),
    SmokeVeilSpec(0.55f, 0.58f, 0.32f, 1.8f, 1.1f),
    SmokeVeilSpec(0.78f, 0.65f, 0.24f, 3.2f, 1.25f),
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

private data class SparkSpec(
    val baseX: Float,
    val baseY: Float,
    val phaseOffset: Float,
    val arcAmp: Float,
    val radius: Float,
)

private val sparkSpecs = listOf(
    SparkSpec(0.12f, 0.90f, 0.2f, 0.06f, 1.8f),
    SparkSpec(0.28f, 0.88f, 0.9f, 0.08f, 2.1f),
    SparkSpec(0.44f, 0.91f, 1.6f, 0.05f, 1.6f),
    SparkSpec(0.58f, 0.87f, 2.4f, 0.07f, 2.0f),
    SparkSpec(0.70f, 0.89f, 3.1f, 0.06f, 1.7f),
    SparkSpec(0.82f, 0.86f, 0.5f, 0.09f, 2.2f),
    SparkSpec(0.35f, 0.82f, 4.0f, 0.04f, 1.4f),
    SparkSpec(0.65f, 0.84f, 4.8f, 0.05f, 1.5f),
    SparkSpec(0.50f, 0.92f, 5.5f, 0.07f, 1.9f),
    SparkSpec(0.18f, 0.85f, 6.2f, 0.06f, 1.6f),
    SparkSpec(0.92f, 0.88f, 2.8f, 0.05f, 1.5f),
    SparkSpec(0.08f, 0.91f, 3.7f, 0.08f, 2.0f),
)

private data class CrackleSpec(val baseX: Float, val baseY: Float)

private val crackleSpecs = listOf(
    CrackleSpec(0.18f, 0.94f),
    CrackleSpec(0.38f, 0.96f),
    CrackleSpec(0.55f, 0.93f),
    CrackleSpec(0.72f, 0.95f),
    CrackleSpec(0.88f, 0.94f),
)

private val liteSparkSpecs = sparkSpecs.take(4)

/** Animated flame lobe height from slow phase and fast flicker pulse. */
internal fun flameLobeHeight(baseHeight: Float, phase: Float, pulse: Float): Float {
    val sway = sin(phase * 1.35f) * 0.12f
    val intensity = 0.72f + pulse.coerceIn(0f, 1f) * 0.28f
    return baseHeight * (0.88f + sway) * intensity
}

/** Composite turbulence offset for organic flame sway. */
internal fun flameTurbulenceOffset(phase: Float, lobeIndex: Int, amplitude: Float): Float {
    val i = lobeIndex.toFloat()
    val angle = phase * 2f * PI.toFloat()
    return sin(angle + i * 0.9f) * amplitude * 0.55f +
        sin(angle * 2f + i * 1.7f) * amplitude * 0.28f
}

/** Spark position along an arc (rise + horizontal sway). */
internal fun sparkArcPosition(
    baseX: Float,
    baseY: Float,
    rise: Float,
    sway: Float,
): Pair<Float, Float> = (baseX + sway) to (baseY - rise)

/** Pulsing alpha for coal crackle spots. */
internal fun crackleSpotAlpha(crackle: Float, spotIndex: Int): Float {
    val phase = crackle * 2f * PI.toFloat() + spotIndex * 1.3f
    return (sin(phase) * 0.5f + 0.5f).coerceIn(0f, 1f)
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
    turbulence: Float = 0f,
    sparkPhase: Float = 0f,
    crackle: Float = 0.5f,
    emberBoost: Float = 1f,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val pulse = 0.72f + flicker * 0.28f
    val heatAngle = heatPhase * 2f * PI.toFloat()
    val turbAngle = turbulence * 2f * PI.toFloat()

    drawHeatHaze(w, h, corner, pulse, heatAngle)
    drawCoalBed(w, h, corner, pulse, heatAngle, flicker, crackle)
    drawSmokeVeils(w, h, heatPhase, heatAngle, pulse, turbulence)
    drawFlameBodies(w, h, accent, drift, flicker, pulse, turbulence)
    drawFlameCores(w, h, drift, flicker, pulse, turbulence)
    drawSparkArcs(w, h, sparkPhase, flicker, sparkSpecs)
    drawEmberField(w, h, drift, flicker, emberBoost, withTrails = true)
    drawTextScrim(w, h, corner)
    drawRimGlow(w, h, corner, accent, pulse, flicker)
    drawForumEdgeShimmer(w, h, corner, drift, flicker, accent, flickerFast = flicker)
}

/** Lite tier: heat haze, coal glow, mini sparks, edge shimmer — no full flame bodies. */
fun DrawScope.drawForumFireBackgroundLite(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    flicker: Float,
    sparkPhase: Float = drift,
    crackle: Float = 0.5f,
) {
    val w = size.width
    val h = size.height
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val pulse = 0.60f + flicker * 0.22f
    val heatAngle = drift * 2f * PI.toFloat()
    drawHeatHaze(w, h, corner, pulse * 0.75f, heatAngle, lite = true)
    drawCoalBedLite(w, h, corner, pulse, heatAngle, crackle)
    drawSparkArcs(w, h, sparkPhase, flicker, liteSparkSpecs, lite = true)
    drawForumEdgeShimmer(w, h, corner, drift, flicker, accent, lite = true)
}

/** Horizontal shimmer sweep along top edge (dual-layer: slow drift + fast flicker). */
fun DrawScope.drawForumEdgeShimmer(
    w: Float,
    h: Float,
    corner: Float,
    drift: Float,
    flicker: Float,
    accent: ForumTopicCardTokens.Accent,
    lite: Boolean = false,
    flickerFast: Float = flicker,
) {
    val stripH = if (lite) 2.dp.toPx() else 3.dp.toPx()
    val shift = drift * w * 0.6f
    val fastShift = flickerFast * w * 0.18f
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
    if (!lite) {
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.whiteHot.copy(alpha = 0.06f + flickerFast * 0.14f),
                    palette.haze.copy(alpha = 0.10f + flickerFast * 0.12f),
                    Color.Transparent,
                ),
                startX = -fastShift,
                endX = w * 0.85f - fastShift,
            ),
            topLeft = Offset(0f, 0f),
            size = Size(w, stripH * 0.65f),
            cornerRadius = CornerRadius(corner, corner),
        )
    }
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

private fun DrawScope.drawHeatHaze(
    w: Float,
    h: Float,
    corner: Float,
    pulse: Float,
    heatAngle: Float,
    lite: Boolean = false,
) {
    val scale = if (lite) 0.55f else 1f
    val cx = w * (0.52f + sin(heatAngle * 0.5f) * 0.06f)
    val cy = h * 0.78f
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.haze.copy(alpha = 0.10f * pulse * scale),
                palette.orange.copy(alpha = 0.06f * pulse * scale),
                palette.magenta.copy(alpha = 0.04f * pulse * scale),
                Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = w * 0.72f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}

private fun DrawScope.drawCoalBed(
    w: Float,
    h: Float,
    corner: Float,
    pulse: Float,
    heatAngle: Float,
    flicker: Float,
    crackle: Float,
) {
    val shimmer = sin(heatAngle) * 0.04f
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                palette.smoke.copy(alpha = 0.085f + flicker * 0.04f),
                palette.magenta.copy(alpha = 0.06f * pulse),
                palette.coal.copy(alpha = 0.17f * pulse + shimmer),
                palette.deep.copy(alpha = 0.31f * pulse),
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
    drawCrackleSpots(w, h, pulse, crackle)
}

private fun DrawScope.drawCoalBedLite(
    w: Float,
    h: Float,
    corner: Float,
    pulse: Float,
    heatAngle: Float,
    crackle: Float,
) {
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                palette.smoke.copy(alpha = 0.07f),
                palette.deep.copy(alpha = 0.19f * pulse),
            ),
            startY = h * 0.55f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.orange.copy(alpha = 0.18f * pulse),
                palette.deep.copy(alpha = 0.10f * pulse),
                Color.Transparent,
            ),
            center = Offset(w * (0.5f + sin(heatAngle * 0.7f) * 0.06f), h * 0.93f),
            radius = w * 0.45f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    drawCrackleSpots(w, h, pulse * 0.7f, crackle, lite = true)
}

private fun DrawScope.drawCrackleSpots(
    w: Float,
    h: Float,
    pulse: Float,
    crackle: Float,
    lite: Boolean = false,
) {
    val specs = if (lite) crackleSpecs.take(3) else crackleSpecs
    specs.forEachIndexed { index, spot ->
        val alpha = crackleSpotAlpha(crackle, index) * (0.35f + pulse * 0.25f)
        val radius = if (lite) 2.5f else 3.5f + alpha * 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.whiteHot.copy(alpha = alpha * 0.9f),
                    palette.amber.copy(alpha = alpha * 0.5f),
                    Color.Transparent,
                ),
                center = Offset(w * spot.baseX, h * spot.baseY),
                radius = radius * 2.5f,
            ),
            radius = radius,
            center = Offset(w * spot.baseX, h * spot.baseY),
        )
    }
}

private fun DrawScope.drawSmokeVeils(
    w: Float,
    h: Float,
    heatPhase: Float,
    heatAngle: Float,
    pulse: Float,
    turbulence: Float,
) {
    val turbAngle = turbulence * 2f * PI.toFloat()
    smokeVeils.forEach { veil ->
        val phase = heatPhase * 2f * PI.toFloat() * veil.speedMul + veil.phaseOffset
        val rise = (sin(phase) * 0.5f + 0.5f) * h * 0.14f
        val turbX = sin(turbAngle + veil.phaseOffset) * w * 0.02f
        val cx = w * veil.baseX + sin(phase * 1.3f) * w * 0.03f + turbX
        val cy = h * veil.baseY - rise
        val radius = w * veil.radiusFrac * (0.92f + sin(heatAngle + veil.phaseOffset) * 0.08f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.smoke.copy(alpha = 0.12f * pulse),
                    palette.deep.copy(alpha = 0.07f * pulse),
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
    turbulence: Float,
) {
    val baseY = h * 0.92f
    val driftAngle = drift * 2f * PI.toFloat()
    val turbAngle = turbulence * 2f * PI.toFloat()

    flameLobes.forEachIndexed { index, lobe ->
        val phase = driftAngle * lobe.speedMul + lobe.phaseOffset
        val turb = flameTurbulenceOffset(turbAngle, index, w * 0.032f)
        val sway = sin(phase) * w * 0.028f + turb
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
                    accent.primary.copy(alpha = 0.10f * pulse),
                    palette.amber.copy(alpha = 0.48f * pulse),
                    palette.orange.copy(alpha = 0.55f * pulse),
                    palette.deep.copy(alpha = 0.42f * pulse),
                ),
                startY = topY,
                endY = baseY,
            ),
        )
    }
}

private fun DrawScope.drawFlameCores(
    w: Float,
    h: Float,
    drift: Float,
    flicker: Float,
    pulse: Float,
    turbulence: Float,
) {
    val baseY = h * 0.92f
    val driftAngle = drift * 2f * PI.toFloat()
    val turbAngle = turbulence * 2f * PI.toFloat()

    flameLobes.forEachIndexed { index, lobe ->
        val phase = driftAngle * lobe.speedMul + lobe.phaseOffset
        val turb = flameTurbulenceOffset(turbAngle, index, w * 0.032f)
        val sway = sin(phase) * w * 0.028f + turb
        val cx = w * lobe.baseX + sway
        val baseHeight = h * lobe.heightFrac
        val flameH = flameLobeHeight(baseHeight, phase, flicker)
        val halfWidth = w * lobe.widthFrac * 0.5f * (0.9f + flicker * 0.12f)
        val topY = (baseY - flameH).coerceAtLeast(h * 0.06f)
        val lean = sway * 0.85f

        val corePath = buildCorePath(cx, baseY, halfWidth, topY, lean)
        drawPath(
            path = corePath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.whiteHot.copy(alpha = 0.28f * flicker),
                    palette.core.copy(alpha = 0.32f * flicker),
                    palette.amber.copy(alpha = 0.55f * pulse),
                    palette.orange.copy(alpha = 0.30f * pulse),
                    Color.Transparent,
                ),
                startY = topY,
                endY = baseY - (baseY - topY) * 0.35f,
            ),
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.drawSparkArcs(
    w: Float,
    h: Float,
    sparkPhase: Float,
    flicker: Float,
    specs: List<SparkSpec>,
    lite: Boolean = false,
) {
    val phaseBase = sparkPhase * 2f * PI.toFloat()
    val maxRise = h * (if (lite) 0.18f else 0.30f)
    val scale = if (lite) 0.65f else 1f

    specs.forEach { spark ->
        val phase = phaseBase + spark.phaseOffset
        val riseNorm = (sin(phase) * 0.5f + 0.5f)
        val rise = riseNorm * maxRise
        val sway = sin(phase * 1.4f) * w * spark.arcAmp
        val (x, y) = sparkArcPosition(w * spark.baseX, h * spark.baseY, rise, sway)
        val alpha = emberOpacityAtRise(riseNorm) *
            (0.45f + flicker * 0.35f) * scale
        val radius = spark.radius * (0.7f + flicker * 0.3f) * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.whiteHot.copy(alpha = alpha),
                    palette.amber.copy(alpha = alpha * 0.75f),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = radius * 2f,
            ),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawEmberField(
    w: Float,
    h: Float,
    drift: Float,
    flicker: Float,
    emberBoost: Float = 1f,
    withTrails: Boolean = false,
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
        val alpha = emberOpacityAtRise(riseNorm) * (0.58f + flicker * 0.28f) * boost
        val radius = ember.radius * (0.75f + flicker * 0.35f)
        if (withTrails && riseNorm > 0.08f) {
            val trailY = y + rise * 0.18f
            drawCircle(
                color = palette.amber.copy(alpha = alpha * 0.35f),
                radius = radius * 0.55f,
                center = Offset(x, trailY),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.whiteHot.copy(alpha = alpha * 0.7f),
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

private fun DrawScope.drawTextScrim(w: Float, h: Float, corner: Float) {
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF0A101C).copy(alpha = 0.72f),
                Color(0xFF0A101C).copy(alpha = 0.42f),
                Color(0xFF0A101C).copy(alpha = 0.12f),
                Color.Transparent,
            ),
            endX = w * 0.55f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}

private fun DrawScope.drawRimGlow(
    w: Float,
    h: Float,
    corner: Float,
    accent: ForumTopicCardTokens.Accent,
    pulse: Float,
    flicker: Float,
) {
    val accentRim = ForumTopicCardTokens.accentRimColor(accent)
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                palette.amber.copy(alpha = 0.05f + pulse * 0.03f),
                accentRim.copy(alpha = 0.04f + pulse * 0.02f),
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
                accentRim.copy(alpha = 0.14f * flicker),
                Color.Transparent,
                Color.Transparent,
                accentRim.copy(alpha = 0.10f * flicker),
            ),
            startX = 0f,
            endX = w,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}
