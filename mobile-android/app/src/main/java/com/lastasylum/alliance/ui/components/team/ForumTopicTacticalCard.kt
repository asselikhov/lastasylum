package com.lastasylum.alliance.ui.components.team

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.ui.theme.premium.PremiumColors
import kotlin.math.sin

@Composable
fun ForumTopicTacticalCardShell(
    onClick: () -> Unit,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ForumTopicCardTokens.pressScale else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumTacticalCardScale",
    )
    val glowBoost by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumTacticalGlowBoost",
    )
    val drift: Float
    val pulse: Float
    val flicker: Float
    val heatPhase: Float
    if (animationsEnabled) {
        val infinite = rememberInfiniteTransition(label = "forumTacticalAmbient")
        drift = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(14_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "forumTacticalDrift",
        ).value
        pulse = infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2_800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "forumTacticalPulse",
        ).value
        flicker = infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "forumFireFlicker",
        ).value
        heatPhase = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(9_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "forumFireHeatPhase",
        ).value
    } else {
        drift = 0f
        pulse = 0.75f
        flicker = 0.5f
        heatPhase = 0f
    }

    val isHot = activityLevel == ForumTopicCardTokens.ActivityLevel.Hot
    val isWarm = activityLevel == ForumTopicCardTokens.ActivityLevel.Warm
    val glassAlpha = ForumTopicCardTokens.glassAlpha(activityLevel)
    val borderColors = ForumTopicCardTokens.gradientBorderColors(accent, activityLevel, glowBoost)
    val outerShape = ForumTopicCardTokens.cardShape
    val innerShape = ForumTopicCardTokens.cardInnerShape
    val showActivityStrip = activityLevel != ForumTopicCardTokens.ActivityLevel.Calm
    val firePalette = ForumTopicCardTokens.FirePalette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                drawTacticalCardShadow(activityLevel, accent, glowBoost, flicker)
            }
            .clip(outerShape)
            .background(Brush.linearGradient(borderColors), outerShape)
            .padding(ForumTopicCardTokens.borderWidth)
            .clip(innerShape)
            .background(ForumTopicCardTokens.glassFill(glassAlpha), innerShape)
            .drawWithContent {
                when {
                    isHot -> drawForumFireBackground(accent, drift, flicker, heatPhase)
                    isWarm -> drawTacticalFogParticles(drift, accent)
                }
                drawTacticalInnerEdgeGlow(accent, activityLevel, pulse)
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = PremiumColors.accentCyan.copy(alpha = 0.18f)),
                onClick = onClick,
            ),
    ) {
        if (showActivityStrip) {
            val stripHeight = if (isHot) {
                ForumTopicCardTokens.activityStripHeightHot
            } else {
                ForumTopicCardTokens.activityStripHeight
            }
            if (isHot) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .drawBehind {
                            val w = size.width
                            val shift = drift * w * 0.35f
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        firePalette.amber.copy(alpha = 0.20f + pulse * 0.35f),
                                        firePalette.orange.copy(alpha = 0.32f + flicker * 0.28f),
                                        accent.primary.copy(alpha = 0.18f + pulse * 0.22f),
                                        firePalette.orange.copy(alpha = 0.28f + pulse * 0.3f),
                                        firePalette.amber.copy(alpha = 0.20f + flicker * 0.25f),
                                        Color.Transparent,
                                    ),
                                    startX = -shift,
                                    endX = w * 1.45f - shift,
                                ),
                            )
                        },
                )
            } else {
                val stripColors = listOf(
                    Color.Transparent,
                    accent.primary.copy(alpha = 0.15f + pulse * 0.35f),
                    accent.secondary.copy(alpha = 0.22f + pulse * 0.28f),
                    PremiumColors.accentCyan.copy(alpha = 0.18f + pulse * 0.22f),
                    Color.Transparent,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .background(Brush.horizontalGradient(colors = stripColors)),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = ForumTopicCardTokens.cardPaddingH,
                    vertical = ForumTopicCardTokens.cardPaddingV,
                ),
            content = content,
        )
    }
}

private fun DrawScope.drawTacticalCardShadow(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
    glowBoost: Float,
    flicker: Float,
) {
    val firePalette = ForumTopicCardTokens.FirePalette
    val baseAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.38f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.24f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.16f
    } * glowBoost.coerceAtMost(1.4f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(0f, 10.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
    val glowCenter = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        Offset(
            size.width * (0.5f + sin(flicker * 6.28f) * 0.04f),
            size.height * (0.88f + flicker * 0.02f),
        )
    } else {
        Offset(size.width * 0.18f, size.height * 0.12f)
    }
    val glowColors = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        listOf(
            firePalette.orange.copy(alpha = baseAlpha * 1.15f),
            firePalette.deep.copy(alpha = baseAlpha * 0.62f),
            Color.Transparent,
        )
    } else {
        listOf(
            accent.primary.copy(alpha = baseAlpha),
            accent.secondary.copy(alpha = baseAlpha * 0.45f),
            Color.Transparent,
        )
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = glowColors,
            center = glowCenter,
            radius = size.maxDimension * 0.98f,
        ),
        size = size,
        cornerRadius = CornerRadius(22.dp.toPx()),
    )
    if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        val secondaryCenter = Offset(
            size.width * 0.5f,
            size.height * 0.96f,
        )
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    firePalette.amber.copy(alpha = 0.22f * flicker),
                    firePalette.orange.copy(alpha = 0.12f * flicker),
                    Color.Transparent,
                ),
                center = secondaryCenter,
                radius = size.width * 0.65f,
            ),
            size = size,
            cornerRadius = CornerRadius(22.dp.toPx()),
        )
    }
}

private fun DrawScope.drawTacticalFogParticles(drift: Float, accent: ForumTopicCardTokens.Accent) {
    val specs = rememberFogParticleSpecs()
    specs.forEachIndexed { index, spec ->
        val phase = drift * 6.283f + index * 1.7f
        val x = size.width * (spec.baseX + sin(phase) * spec.wobbleX)
        val y = size.height * (spec.baseY + sin(phase * 0.85f) * spec.wobbleY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.secondary.copy(alpha = spec.alpha),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = spec.radius,
            ),
            radius = spec.radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawTacticalInnerEdgeGlow(
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    pulse: Float,
) {
    val edgeAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.14f + pulse * 0.08f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.10f + pulse * 0.05f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.06f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = edgeAlpha),
                accent.primary.copy(alpha = edgeAlpha * 0.35f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.45f,
        ),
        size = size,
        cornerRadius = CornerRadius(20.dp.toPx()),
    )
}

private data class FogParticleSpec(
    val baseX: Float,
    val baseY: Float,
    val wobbleX: Float,
    val wobbleY: Float,
    val radius: Float,
    val alpha: Float,
)

private fun rememberFogParticleSpecs(): List<FogParticleSpec> = listOf(
    FogParticleSpec(0.12f, 0.22f, 0.04f, 0.03f, 38f, 0.07f),
    FogParticleSpec(0.78f, 0.18f, 0.05f, 0.04f, 44f, 0.05f),
    FogParticleSpec(0.62f, 0.72f, 0.03f, 0.05f, 52f, 0.04f),
    FogParticleSpec(0.28f, 0.68f, 0.04f, 0.03f, 34f, 0.06f),
)
