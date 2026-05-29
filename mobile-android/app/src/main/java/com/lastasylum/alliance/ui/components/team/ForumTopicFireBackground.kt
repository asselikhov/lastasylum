package com.lastasylum.alliance.ui.components.team

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val FireAmber = Color(0xFFE8A030)
private val FireOrange = Color(0xFFFF6B35)
private val FireDeep = Color(0xFFC45C20)
private val FireCoal = Color(0xFF8B2500)
private val FireSmoke = Color(0xFF2A1810)

private data class FlameLobeSpec(
    val baseX: Float,
    val widthFrac: Float,
    val heightFrac: Float,
    val phaseOffset: Float,
)

private val flameLobes = listOf(
    FlameLobeSpec(0.14f, 0.22f, 0.42f, 0f),
    FlameLobeSpec(0.32f, 0.18f, 0.48f, 1.2f),
    FlameLobeSpec(0.50f, 0.24f, 0.55f, 2.4f),
    FlameLobeSpec(0.68f, 0.20f, 0.46f, 0.8f),
    FlameLobeSpec(0.84f, 0.22f, 0.40f, 1.9f),
)

private data class EmberSpec(
    val baseX: Float,
    val baseY: Float,
    val phaseOffset: Float,
    val radius: Float,
)

private val emberSpecs = listOf(
    EmberSpec(0.18f, 0.78f, 0.3f, 2.2f),
    EmberSpec(0.42f, 0.82f, 1.1f, 1.8f),
    EmberSpec(0.61f, 0.76f, 2.0f, 2.5f),
    EmberSpec(0.75f, 0.80f, 0.7f, 1.6f),
    EmberSpec(0.28f, 0.70f, 2.6f, 1.4f),
    EmberSpec(0.55f, 0.72f, 3.4f, 2.0f),
    EmberSpec(0.88f, 0.74f, 1.5f, 1.7f),
    EmberSpec(0.08f, 0.85f, 4.1f, 1.5f),
)

/**
 * Animated fire layer for forum topic cards with unread messages (Hot).
 */
fun DrawScope.drawForumFireBackground(
    accent: ForumTopicCardTokens.Accent,
    drift: Float,
    flicker: Float,
) {
    val w = size.width
    val h = size.height
    val corner = 20.dp.toPx()
    val pulse = 0.72f + flicker * 0.28f

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                FireSmoke.copy(alpha = 0.12f + flicker * 0.06f),
                FireCoal.copy(alpha = 0.22f * pulse),
                FireDeep.copy(alpha = 0.38f * pulse),
            ),
            startY = h * 0.35f,
            endY = h,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )

    val baseY = h * 0.92f
    flameLobes.forEach { lobe ->
        val phase = drift * 2f * PI.toFloat() + lobe.phaseOffset
        val sway = sin(phase) * w * 0.025f
        val cx = w * lobe.baseX + sway
        val flameH = h * lobe.heightFrac * (0.88f + sin(phase * 1.35f) * 0.12f) * pulse
        val flameW = w * lobe.widthFrac * (0.9f + flicker * 0.15f)
        val bottom = baseY
        val top = (bottom - flameH).coerceAtLeast(h * 0.08f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    FireAmber.copy(alpha = 0.55f * pulse),
                    FireOrange.copy(alpha = 0.42f * pulse),
                    accent.primary.copy(alpha = 0.28f * pulse),
                    Color.Transparent,
                ),
                center = Offset(cx, (top + bottom) * 0.45f),
                radius = flameW * 0.95f,
            ),
            radius = flameW,
            center = Offset(cx, (top + bottom) * 0.5f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f * flicker),
                    FireAmber.copy(alpha = 0.35f * pulse),
                    Color.Transparent,
                ),
                center = Offset(cx + sway * 0.3f, top + flameH * 0.25f),
                radius = flameW * 0.45f,
            ),
            radius = flameW * 0.5f,
            center = Offset(cx, top + flameH * 0.3f),
        )
    }

    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                FireOrange.copy(alpha = 0.18f * pulse),
                FireAmber.copy(alpha = 0.24f * pulse),
                FireOrange.copy(alpha = 0.18f * pulse),
                Color.Transparent,
            ),
            startX = 0f,
            endX = w,
        ),
        topLeft = Offset(0f, h - 6.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(w, 6.dp.toPx()),
        cornerRadius = CornerRadius(3.dp.toPx()),
    )

    emberSpecs.forEach { ember ->
        val phase = drift * 2f * PI.toFloat() + ember.phaseOffset
        val rise = (sin(phase) * 0.5f + 0.5f) * h * 0.22f
        val x = w * ember.baseX + sin(phase * 1.7f) * w * 0.04f
        val y = h * ember.baseY - rise
        drawCircle(
            color = FireAmber.copy(alpha = 0.35f + flicker * 0.25f),
            radius = ember.radius * (0.8f + flicker * 0.4f),
            center = Offset(x, y),
        )
    }
}
