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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
fun ForumTopicAnimatedShell(
    onClick: () -> Unit,
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    animationTier: FeedAnimationTier,
    modifier: Modifier = Modifier,
    emberBoost: Float = 1f,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable BoxScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ForumTopicCardTokens.pressScale else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumCardScale",
    )
    val glowBoost by animateFloatAsState(
        targetValue = if (pressed) 1.35f else 1f,
        animationSpec = ForumTopicCardTokens.pressAnimSpec,
        label = "forumCardGlowBoost",
    )

    val animActive = animationTier != FeedAnimationTier.Off
    val fullAnim = animationTier == FeedAnimationTier.Full
    val liteAnim = animationTier == FeedAnimationTier.Lite

    val drift: Float
    val pulse: Float
    val flicker: Float
    val heatPhase: Float
    if (animActive) {
        val infinite = rememberInfiniteTransition(label = "forumCardAmbient")
        drift = infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (fullAnim) 14_000 else 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "forumDrift",
        ).value
        pulse = if (fullAnim) {
            infinite.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2_800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "forumPulse",
            ).value
        } else {
            0.75f
        }
        flicker = infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (fullAnim) 520 else 1_800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "forumFlicker",
        ).value
        heatPhase = if (fullAnim) {
            infinite.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(9_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "forumHeatPhase",
            ).value
        } else {
            drift
        }
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
    val showActivityStrip = animActive && activityLevel != ForumTopicCardTokens.ActivityLevel.Calm
    val firePalette = ForumTopicCardTokens.FirePalette

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (animActive) {
                    drawTacticalCardShadow(activityLevel, accent, glowBoost, flicker)
                }
            }
            .clip(outerShape)
            .background(Brush.linearGradient(borderColors), outerShape)
            .padding(ForumTopicCardTokens.borderWidth)
            .clip(innerShape)
            .background(ForumTopicCardTokens.glassFill(glassAlpha), innerShape)
            .drawWithContent {
                when {
                    fullAnim && isHot -> drawForumFireBackground(
                        accent, drift, flicker, heatPhase, emberBoost,
                    )
                    liteAnim && isHot -> drawForumFireBackgroundLite(accent, drift, flicker)
                    liteAnim && isWarm -> drawTacticalFogParticles(drift, accent)
                }
                if (animActive) {
                    drawTacticalInnerEdgeGlow(accent, activityLevel, pulse)
                }
                drawContent()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = PremiumColors.accentCyan.copy(alpha = 0.18f)),
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(ForumTopicCardTokens.accentRailWidth)
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            railColor(activityLevel, accent).copy(alpha = 0.92f),
                            railColor(activityLevel, accent).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        if (showActivityStrip) {
            val stripHeight = if (isHot && fullAnim) {
                ForumTopicCardTokens.activityStripHeightHot
            } else {
                ForumTopicCardTokens.activityStripHeight
            }
            if (isHot && (fullAnim || liteAnim)) {
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
                                        firePalette.amber.copy(alpha = 0.16f + pulse * 0.2f),
                                        firePalette.orange.copy(alpha = 0.24f + flicker * 0.2f),
                                        accent.primary.copy(alpha = 0.12f + pulse * 0.15f),
                                        Color.Transparent,
                                    ),
                                    startX = -shift,
                                    endX = w * 1.35f - shift,
                                ),
                            )
                        },
                )
            } else if (isWarm && liteAnim) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    accent.primary.copy(alpha = 0.12f),
                                    accent.secondary.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = ForumTopicCardTokens.accentRailWidth + 8.dp,
                    end = ForumTopicCardTokens.cardPaddingH,
                    top = ForumTopicCardTokens.cardPaddingV,
                    bottom = ForumTopicCardTokens.cardPaddingV,
                ),
            content = content,
        )
    }
}

private fun railColor(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
): Color = when (activityLevel) {
    ForumTopicCardTokens.ActivityLevel.Hot -> ForumTopicCardTokens.FirePalette.orange
    ForumTopicCardTokens.ActivityLevel.Warm -> accent.primary
    ForumTopicCardTokens.ActivityLevel.Calm -> accent.primary.copy(alpha = 0.65f)
}

/** @deprecated Use [ForumTopicAnimatedShell] with explicit [FeedAnimationTier]. */
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
    val tier = if (animationsEnabled) {
        when (activityLevel) {
            ForumTopicCardTokens.ActivityLevel.Hot -> FeedAnimationTier.Full
            ForumTopicCardTokens.ActivityLevel.Warm -> FeedAnimationTier.Lite
            ForumTopicCardTokens.ActivityLevel.Calm -> FeedAnimationTier.Off
        }
    } else {
        FeedAnimationTier.Off
    }
    ForumTopicAnimatedShell(
        onClick = onClick,
        accent = accent,
        activityLevel = activityLevel,
        animationTier = tier,
        modifier = modifier,
        interactionSource = interactionSource,
        content = content,
    )
}

private fun DrawScope.drawTacticalCardShadow(
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    accent: ForumTopicCardTokens.Accent,
    glowBoost: Float,
    flicker: Float,
) {
    val firePalette = ForumTopicCardTokens.FirePalette
    val corner = FeedCardDesignTokens.compactCornerRadius.toPx()
    val baseAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.32f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.18f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.12f
    } * glowBoost.coerceAtMost(1.4f)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.36f),
        topLeft = Offset(0f, 6.dp.toPx()),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
    val glowCenter = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        Offset(
            size.width * (0.5f + sin(flicker * 6.28f) * 0.04f),
            size.height * 0.88f,
        )
    } else {
        Offset(size.width * 0.18f, size.height * 0.12f)
    }
    val glowColors = if (activityLevel == ForumTopicCardTokens.ActivityLevel.Hot) {
        listOf(
            firePalette.orange.copy(alpha = baseAlpha),
            firePalette.deep.copy(alpha = baseAlpha * 0.5f),
            Color.Transparent,
        )
    } else {
        listOf(
            accent.primary.copy(alpha = baseAlpha),
            accent.secondary.copy(alpha = baseAlpha * 0.4f),
            Color.Transparent,
        )
    }
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = glowColors,
            center = glowCenter,
            radius = size.maxDimension * 0.85f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
    )
}

private fun DrawScope.drawTacticalFogParticles(drift: Float, accent: ForumTopicCardTokens.Accent) {
    val specs = rememberFogParticleSpecs()
    specs.take(2).forEachIndexed { index, spec ->
        val phase = drift * 6.283f + index * 1.7f
        val x = size.width * (spec.baseX + sin(phase) * spec.wobbleX)
        val y = size.height * (spec.baseY + sin(phase * 0.85f) * spec.wobbleY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.secondary.copy(alpha = spec.alpha * 0.8f),
                    Color.Transparent,
                ),
                center = Offset(x, y),
                radius = spec.radius * 0.85f,
            ),
            radius = spec.radius * 0.85f,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.drawTacticalInnerEdgeGlow(
    accent: ForumTopicCardTokens.Accent,
    activityLevel: ForumTopicCardTokens.ActivityLevel,
    pulse: Float,
) {
    val corner = FeedCardDesignTokens.compactInnerCornerRadius.toPx()
    val edgeAlpha = when (activityLevel) {
        ForumTopicCardTokens.ActivityLevel.Hot -> 0.12f + pulse * 0.06f
        ForumTopicCardTokens.ActivityLevel.Warm -> 0.08f + pulse * 0.04f
        ForumTopicCardTokens.ActivityLevel.Calm -> 0.05f
    }
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = edgeAlpha),
                accent.primary.copy(alpha = edgeAlpha * 0.3f),
                Color.Transparent,
            ),
            endY = size.height * 0.4f,
        ),
        size = size,
        cornerRadius = CornerRadius(corner),
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
